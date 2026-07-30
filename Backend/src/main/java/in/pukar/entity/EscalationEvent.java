package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "escalation_events")
@Getter
@Setter
public class EscalationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "complaint_id", length = 36)
    private String complaintId;

    @Column(name = "from_role", length = 50)
    private String fromRole;

    @Column(name = "to_role", length = 50)
    private String toRole;

    @Column(name = "to_user_id", length = 36)
    private String toUserId;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "triggered_by", length = 20)
    private String triggeredBy; // AUTO_SLA | CITIZEN_REJECT | MANUAL

    @Column(name = "escalated_at")
    private Instant escalatedAt = Instant.now();
}
