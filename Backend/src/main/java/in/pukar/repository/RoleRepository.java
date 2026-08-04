package in.pukar.repository;

import in.pukar.entity.Role;
import in.pukar.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByName(RoleName name);
}
