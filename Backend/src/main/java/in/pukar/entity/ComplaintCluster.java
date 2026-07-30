package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "complaint_clusters")
@Getter
@Setter
public class ComplaintCluster {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "primary_complaint_id", length = 36)
    private String primaryComplaintId;

    @Column(length = 100)
    private String category;

    @Column(name = "latitude_center", precision = 9, scale = 6)
    private BigDecimal latitudeCenter;

    @Column(name = "longitude_center", precision = 9, scale = 6)
    private BigDecimal longitudeCenter;

    @Column(name = "member_count")
    private int memberCount = 1;

    @Column(name = "cluster_label", length = 200)
    private String clusterLabel;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
