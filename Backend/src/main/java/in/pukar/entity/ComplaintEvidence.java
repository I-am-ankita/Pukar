package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "complaint_evidence")
@Getter
@Setter
public class ComplaintEvidence {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "complaint_id", length = 36, nullable = false)
    private String complaintId;

    @Column(name = "uploader_id", length = 36)
    private String uploaderId;

    @Column(name = "uploader_hash", length = 64)
    private String uploaderHash;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "upload_type", length = 20)
    private String uploadType; // CITIZEN_EVIDENCE | RESOLUTION_PROOF

    @Column(name = "uploaded_at")
    private Instant uploadedAt = Instant.now();
}
