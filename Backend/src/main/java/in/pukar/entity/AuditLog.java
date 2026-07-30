package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_id"),
        @Index(name = "idx_audit_time", columnList = "occurred_at")
})
@Getter
@Setter
public class AuditLog {
    // sequential id for hash chaining
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 36)
    private String entityId;

    @Column(name = "actor_id", length = 36)
    private String actorId;

    @Column(name = "actor_hash", length = 64)
    private String actorHash;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON payload

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue; // JSON snapshot of affected fields before the change

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue; // JSON snapshot of affected fields after the change

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "occurred_at")
    private Instant occurredAt = Instant.now();
}
