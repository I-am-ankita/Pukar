package in.pukar.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Entity
@Table(name = "otp_challenges", indexes = {
        @Index(name = "idx_otp_phone_hash", columnList = "phone_hash")
})
@Getter
@Setter
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_hash", nullable = false, length = 64)
    private String phoneHash;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(length = 40)
    private String purpose;

    @Column(nullable = false)
    private Instant expiresAt;

    private boolean consumed = false;

    private int attempts = 0;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
