package in.pukar.repository;

import in.pukar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findByDepartmentId(String departmentId);
    List<User> findByWardIdAndDepartmentId(String wardId, String departmentId);
    List<User> findByWardId(String wardId);
}
