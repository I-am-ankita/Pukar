package in.pukar.repository;

import in.pukar.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WardRepository extends JpaRepository<Ward, String> {
    Optional<Ward> findByCode(String code);
    boolean existsByCode(String code);
}
