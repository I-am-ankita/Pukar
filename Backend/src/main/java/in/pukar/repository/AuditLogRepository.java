package in.pukar.repository;

import in.pukar.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    AuditLog findTopByOrderByIdDesc();
    List<AuditLog> findByEntityIdOrderByIdAsc(String entityId);
}
