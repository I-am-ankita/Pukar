package in.pukar.repository;

import in.pukar.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    Optional<OtpChallenge> findTopByPhoneHashAndConsumedFalseOrderByIdDesc(String phoneHash);
}
