package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    @Column(name = "recipient_id", length = 36)
    private String recipientId;

    @Column(name = "recipient_hash", length = 64)
    private String recipientHash;

    @Column(length = 20)
    private String channel; // EMAIL | SMS | IN_APP

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "is_sent")
    private boolean sent = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
}
