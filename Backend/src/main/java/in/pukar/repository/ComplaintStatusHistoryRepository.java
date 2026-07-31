package in.pukar.repository;

import in.pukar.entity.ComplaintStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintStatusHistoryRepository extends JpaRepository<ComplaintStatusHistory, String> {
    List<ComplaintStatusHistory> findByComplaintIdOrderByChangedAtAsc(String complaintId);
}
