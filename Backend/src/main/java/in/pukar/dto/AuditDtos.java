package in.pukar.dto;

import java.time.Instant;
import java.util.List;

public class AuditDtos {

    public record AuditEntry(
            Long id,
            String eventType,
            String entityType,
            String entityId,
            String actorRole,
            String actorId,
            String details,
            String oldValue,
            String newValue,
            String eventHash,
            String previousHash,
            Instant occurredAt) {}

    public record AuditVerifyResult(
            boolean intact,
            int totalEntries,
            Long firstBrokenId,
            Instant verifiedAt) {}

    public record AuditTrail(
            String complaintId,
            List<AuditEntry> entries) {}
}
