package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "complaint_status_history")
@Getter
@Setter
public class ComplaintStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "complaint_id", length = 36, nullable = false)
    private String complaintId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private ComplaintStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30, nullable = false)
    private ComplaintStatus toStatus;

    @Column(name = "changed_by_id", length = 36)
    private String changedById;

    @Column(name = "changed_by_hash", length = 64)
    private String changedByHash;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "changed_at")
    private Instant changedAt = Instant.now();
}
