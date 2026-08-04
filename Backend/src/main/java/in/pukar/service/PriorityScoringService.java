package in.pukar.service;

import in.pukar.entity.Complaint;
import in.pukar.entity.ComplaintPriority;
import in.pukar.repository.ComplaintRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Smart-triage priority scoring (Intelligence tier).
 * Score = w1*severity + w2*locationSensitivity + w3*repeatFrequency
 *       + w4*deptDelay + w5*evidenceStrength  -> normalised to 0..10
 */
@Service
public class PriorityScoringService {

    private static final Map<String, Double> CATEGORY_SEVERITY = Map.of(
            "BRIBERY", 9.0,
            "CORRUPTION", 9.0,
            "LAND_ENCROACHMENT", 7.0,
            "WATER", 6.0,
            "SANITATION", 6.0,
            "GARBAGE", 5.0,
            "ROAD", 5.0,
            "ELECTRICITY", 6.0,
            "OTHER", 4.0
    );

    private final ComplaintRepository complaintRepo;

    public PriorityScoringService(ComplaintRepository complaintRepo) {
        this.complaintRepo = complaintRepo;
    }

    public BigDecimal computeScore(Complaint c, boolean hasEvidence) {
        double severity = CATEGORY_SEVERITY.getOrDefault(
                c.getCategory() == null ? "OTHER" : c.getCategory().toUpperCase(), 4.0);

        // location sensitivity: complaints with coordinates are actionable
        double locationSensitivity = (c.getLatitude() != null && c.getLongitude() != null) ? 6.0 : 3.0;

        // repeat frequency: how many existing complaints share ward + category
        double repeat = 0.0;
        if (c.getWard() != null) {
            long sameWard = complaintRepo.findByDeletedFalse().stream()
                    .filter(o -> o.getWard() != null
                            && o.getWard().equalsIgnoreCase(c.getWard())
                            && o.getCategory() != null
                            && o.getCategory().equalsIgnoreCase(c.getCategory()))
                    .count();
            repeat = Math.min(10.0, sameWard);
        }

        double deptDelay = c.getEscalationCount() * 2.5; // historical/ongoing delay signal
        double evidence = hasEvidence ? 8.0 : 3.0;

        double raw = 0.35 * severity
                + 0.15 * locationSensitivity
                + 0.20 * repeat
                + 0.15 * Math.min(10.0, deptDelay)
                + 0.15 * evidence;

        double score = Math.max(0, Math.min(10, raw));
        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    public ComplaintPriority labelFor(BigDecimal score) {
        double s = score.doubleValue();
        if (s >= 8.0) return ComplaintPriority.CRITICAL;
        if (s >= 6.0) return ComplaintPriority.HIGH;
        if (s >= 4.0) return ComplaintPriority.MEDIUM;
        return ComplaintPriority.LOW;
    }
}
