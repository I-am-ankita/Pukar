package in.pukar.repository;

import in.pukar.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByRecipientHashOrderByCreatedAtDesc(String recipientHash);
}
