package in.pukar.repository;

import in.pukar.entity.PerformanceScorecard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceScorecardRepository extends JpaRepository<PerformanceScorecard, String> {
}
