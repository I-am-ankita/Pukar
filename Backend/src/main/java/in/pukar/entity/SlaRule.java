package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "sla_rules")
@Getter
@Setter
public class SlaRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "level1_hours", nullable = false)
    private int level1Hours;   // officer -> supervisor

    @Column(name = "level2_hours", nullable = false)
    private int level2Hours;   // supervisor -> watchdog

    @Column(name = "level3_hours", nullable = false)
    private int level3Hours;   // watchdog -> critical

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
