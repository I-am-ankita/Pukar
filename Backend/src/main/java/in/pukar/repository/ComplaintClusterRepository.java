package in.pukar.repository;

import in.pukar.entity.ComplaintCluster;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintClusterRepository extends JpaRepository<ComplaintCluster, String> {
    List<ComplaintCluster> findByCategory(String category);
}
