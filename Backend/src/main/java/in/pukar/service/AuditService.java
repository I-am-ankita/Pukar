package in.pukar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pukar.common.HashUtil;
import in.pukar.dto.AuditDtos;
import in.pukar.entity.AuditLog;
import in.pukar.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private static final String GENESIS = "GENESIS";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogRepository auditRepo;

    public AuditService(AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    /**
     * Append a tamper-evident, hash-chained audit record.
     * Each record's hash = SHA-256(previousHash + canonical event payload).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized void record(String eventType, String entityType, String entityId,
                                    String actorId, String actorRole, String actorHash,
                                    String details, String ipAddress, String oldValue, String newValue) {
        AuditLog last = auditRepo.findTopByOrderByIdDesc();
        String previousHash = (last == null) ? GENESIS : last.getEventHash();

        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setActorId(actorId);
        log.setActorRole(actorRole);
        log.setActorHash(actorHash);
        log.setDetails(details);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setIpAddress(ipAddress);
        log.setPreviousHash(previousHash);

        String canonical = canonical(log);
        log.setEventHash(HashUtil.sha256(previousHash + canonical));
        auditRepo.save(log);
    }

    public void record(String eventType, String entityType, String entityId,
                       String actorId, String actorRole, String actorHash,
                       String details, String ipAddress) {
        record(eventType, entityType, entityId, actorId, actorRole, actorHash, details, ipAddress, null, null);
    }

    public void record(String eventType, String entityType, String entityId,
                       String actorId, String actorRole, String details) {
        record(eventType, entityType, entityId, actorId, actorRole, null, details, null, null, null);
    }

    /** Like record(), but takes before/after field snapshots (narrow maps, not whole entities) and JSON-serializes them. */
    public void recordChange(String eventType, String entityType, String entityId,
                             String actorId, String actorRole, String actorHash,
                             String details, String ipAddress, Object oldSnapshot, Object newSnapshot) {
        record(eventType, entityType, entityId, actorId, actorRole, actorHash, details, ipAddress,
                toJson(oldSnapshot), toJson(newSnapshot));
    }

    public void recordChange(String eventType, String entityType, String entityId,
                             String actorId, String actorRole, String details,
                             Object oldSnapshot, Object newSnapshot) {
        recordChange(eventType, entityType, entityId, actorId, actorRole, null, details, null, oldSnapshot, newSnapshot);
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try { return MAPPER.writeValueAsString(o); }
        catch (Exception e) { return null; }
    }

    private String canonical(AuditLog log) {
        return log.getEventType() + "|" + log.getEntityType() + "|" + log.getEntityId() + "|"
                + nz(log.getActorId()) + "|" + nz(log.getActorRole()) + "|" + nz(log.getActorHash())
                + "|" + nz(log.getDetails()) + "|" + nz(log.getOldValue()) + "|" + nz(log.getNewValue())
                + "|" + log.getOccurredAt();
    }

    private String nz(String s) { return s == null ? "" : s; }

    @Transactional(readOnly = true)
    public List<AuditDtos.AuditEntry> trail(String entityId) {
        return auditRepo.findByEntityIdOrderByIdAsc(entityId).stream()
                .map(a -> new AuditDtos.AuditEntry(
                        a.getId(), a.getEventType(), a.getEntityType(), a.getEntityId(),
                        a.getActorRole(), a.getActorId(), a.getDetails(),
                        a.getOldValue(), a.getNewValue(),
                        a.getEventHash(), a.getPreviousHash(), a.getOccurredAt()))
                .toList();
    }

    /**
     * Re-walk the entire chain and verify each record's hash still derives
     * from its stored previous hash + payload. Detects edits or backdating.
     */
    @Transactional(readOnly = true)
    public AuditDtos.AuditVerifyResult verifyChain() {
        List<AuditLog> all = auditRepo.findAll(
                org.springframework.data.domain.Sort.by("id").ascending());
        String expectedPrev = GENESIS;
        Long firstBroken = null;
        for (AuditLog log : all) {
            String recomputed = HashUtil.sha256(expectedPrev + canonical(log));
            boolean prevOk = expectedPrev.equals(log.getPreviousHash());
            boolean hashOk = recomputed.equals(log.getEventHash());
            if (!prevOk || !hashOk) {
                firstBroken = log.getId();
                break;
            }
            expectedPrev = log.getEventHash();
        }
        return new AuditDtos.AuditVerifyResult(
                firstBroken == null, all.size(), firstBroken, java.time.Instant.now());
    }
}
