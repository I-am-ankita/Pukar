package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "performance_scorecards")
@Getter
@Setter
public class PerformanceScorecard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "department_id", length = 36)
    private String departmentId;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "total_complaints")
    private int totalComplaints;

    @Column(name = "resolved_count")
    private int resolvedCount;

    @Column(name = "avg_resolution_hours", precision = 10, scale = 2)
    private BigDecimal avgResolutionHours;

    @Column(name = "sla_compliance_pct", precision = 5, scale = 2)
    private BigDecimal slaCompliancePct;

    @Column(name = "citizen_satisfaction_pct", precision = 5, scale = 2)
    private BigDecimal citizenSatisfactionPct;

    @Column(name = "recurrence_rate", precision = 5, scale = 2)
    private BigDecimal recurrenceRate;

    @Column(name = "efficiency_index", precision = 5, scale = 2)
    private BigDecimal efficiencyIndex;

    @Column(name = "computed_at")
    private Instant computedAt = Instant.now();
}
