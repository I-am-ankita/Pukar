package in.pukar.repository;

import in.pukar.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, String> {
    Optional<Department> findByCode(String code);
    boolean existsByCode(String code);
}
