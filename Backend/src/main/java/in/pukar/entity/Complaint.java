package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaints_status", columnList = "status"),
        @Index(name = "idx_complaints_department", columnList = "department_id"),
        @Index(name = "idx_complaints_location", columnList = "latitude,longitude"),
        @Index(name = "idx_complaints_reporter_hash", columnList = "reporter_hash"),
        @Index(name = "idx_complaints_cluster", columnList = "cluster_id")
})
@Getter
@Setter
public class Complaint {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "tracking_code", unique = true, nullable = false, length = 20)
    private String trackingCode;

    @Column(name = "reporter_hash", nullable = false, length = 64)
    private String reporterHash;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "sub_category", length = 100)
    private String subCategory;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "location_text", length = 500)
    private String locationText;

    @Column(name = "ward", length = 120)
    private String ward;

    @Column(name = "ward_id", length = 36)
    private String wardId;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "incident_date")
    private LocalDate incidentDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ComplaintPriority priority = ComplaintPriority.LOW;

    @Column(name = "priority_score", precision = 6, scale = 2)
    private BigDecimal priorityScore;

    @Column(name = "assigned_officer_id", length = 36)
    private String assignedOfficerId;

    @Column(name = "department_id", length = 36)
    private String departmentId;

    @Column(name = "cluster_id", length = 36)
    private String clusterId;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolution_evidence_url", columnDefinition = "TEXT")
    private String resolutionEvidenceUrl;

    @Column(name = "supervisor_approval_id", length = 36)
    private String supervisorApprovalId;

    @Column(name = "supervisor_approved_at")
    private Instant supervisorApprovedAt;

    @Column(name = "citizen_feedback")
    private Boolean citizenFeedback;

    @Column(name = "citizen_feedback_at")
    private Instant citizenFeedbackAt;

    @Column(name = "sla_deadline")
    private Instant slaDeadline;

    @Column(name = "escalation_count")
    private int escalationCount = 0;

    @Column(name = "is_public_visible")
    private boolean publicVisible = true;

    @Column(name = "is_deleted")
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
