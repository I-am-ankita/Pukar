package in.pukar.repository;

import in.pukar.entity.ComplaintEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintEvidenceRepository extends JpaRepository<ComplaintEvidence, String> {
    List<ComplaintEvidence> findByComplaintIdOrderByUploadedAtAsc(String complaintId);
}
