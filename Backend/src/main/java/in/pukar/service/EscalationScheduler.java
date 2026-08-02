package in.pukar.service;

import in.pukar.entity.Complaint;
import in.pukar.entity.ComplaintStatus;
import in.pukar.repository.ComplaintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * SLA-based auto-escalation engine (Core tier).
 * Runs on a fixed interval, escalates breached complaints and auto-closes
 * resolutions the citizen never responded to within the feedback window.
 */
@Service
public class EscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(EscalationScheduler.class);
    private static final long FEEDBACK_WINDOW_DAYS = 5;

    private final ComplaintRepository complaintRepo;
    private final ComplaintService complaintService;
    private final AuditService auditService;

    public EscalationScheduler(ComplaintRepository complaintRepo,
                               ComplaintService complaintService,
                               AuditService auditService) {
        this.complaintRepo = complaintRepo;
        this.complaintService = complaintService;
        this.auditService = auditService;
    }

    // every hour (configurable). For demo speed you can lower this.
    @Scheduled(fixedDelayString = "${pukar.escalation.interval-ms:3600000}", initialDelay = 60000)
    @Transactional
    public void runEscalationSweep() {
        Instant now = Instant.now();
        List<Complaint> breached = complaintRepo.findSlaBreached(now);
        for (Complaint c : breached) {
            try {
                ComplaintStatus from = c.getStatus();
                complaintService.escalate(c,
                        "SLA breached: no resolution within deadline", "AUTO_SLA", null, "SYSTEM");
                complaintRepo.save(c);
                auditService.recordChange("AUTO_ESCALATED", "COMPLAINT", c.getId(), null, "SYSTEM", null,
                        "{\"reason\":\"SLA_BREACH\"}", null,
                        Map.of("status", from.name()), Map.of("status", c.getStatus().name()));
            } catch (Exception ex) {
                log.warn("Escalation failed for complaint {}: {}", c.getId(), ex.getMessage());
            }
        }
        if (!breached.isEmpty()) {
            log.info("SLA sweep escalated {} complaint(s)", breached.size());
        }

        // auto-close resolutions awaiting citizen feedback past the window
        Instant cutoff = now.minus(FEEDBACK_WINDOW_DAYS, ChronoUnit.DAYS);
        List<Complaint> expired = complaintRepo.findFeedbackExpired(cutoff);
        for (Complaint c : expired) {
            ComplaintStatus from = c.getStatus();
            c.setStatus(ComplaintStatus.CLOSED);
            complaintRepo.save(c);
            auditService.recordChange("AUTO_CLOSED", "COMPLAINT", c.getId(), null, "SYSTEM", null,
                    "{\"reason\":\"UNVERIFIED_RESOLUTION\"}", null,
                    Map.of("status", from.name()), Map.of("status", "CLOSED"));
        }
    }
}
