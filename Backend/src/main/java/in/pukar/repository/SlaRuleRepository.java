package in.pukar.repository;

import in.pukar.entity.SlaRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SlaRuleRepository extends JpaRepository<SlaRule, String> {
    Optional<SlaRule> findFirstByCategoryAndActiveTrue(String category);
    boolean existsByCategory(String category);
}
