package in.pukar.service;

import in.pukar.entity.Notification;
import in.pukar.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Lightweight notification service. In this MVP it persists an in-app
 * notification record (and logs it). Email/SMS adapters can be plugged in later.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void notifyCitizen(String recipientHash, String subject, String body) {
        Notification n = new Notification();
        n.setRecipientHash(recipientHash);
        n.setChannel("IN_APP");
        n.setSubject(subject);
        n.setBody(body);
        n.setSent(true);
        n.setSentAt(Instant.now());
        repo.save(n);
    }

    public void notifyUser(String recipientId, String subject, String body) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setChannel("IN_APP");
        n.setSubject(subject);
        n.setBody(body);
        n.setSent(true);
        n.setSentAt(Instant.now());
        repo.save(n);
    }
}
