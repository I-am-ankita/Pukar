package in.pukar.repository;

import in.pukar.entity.EscalationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EscalationEventRepository extends JpaRepository<EscalationEvent, String> {
    List<EscalationEvent> findByComplaintIdOrderByEscalatedAtAsc(String complaintId);
    Optional<EscalationEvent> findTopByComplaintIdOrderByEscalatedAtDesc(String complaintId);
}
