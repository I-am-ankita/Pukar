package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.common.HashUtil;
import in.pukar.entity.OtpChallenge;
import in.pukar.repository.OtpChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository otpRepo;
    private final String reporterSalt;
    private final int length;
    private final long ttlSeconds;
    private final int maxAttempts;
    private final boolean devMode;

    public OtpService(OtpChallengeRepository otpRepo,
                      @Value("${pukar.security.reporter-salt}") String reporterSalt,
                      @Value("${pukar.otp.length:6}") int length,
                      @Value("${pukar.otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${pukar.otp.max-attempts:5}") int maxAttempts,
                      @Value("${pukar.otp.dev-mode:true}") boolean devMode) {
        this.otpRepo = otpRepo;
        this.reporterSalt = reporterSalt;
        this.length = length;
        this.ttlSeconds = ttlSeconds;
        this.maxAttempts = maxAttempts;
        this.devMode = devMode;
    }

    public record OtpRequestResult(boolean sent, String maskedPhone, String devCode, long expiresInSeconds) {}

 
    public String phoneHash(String phone) {
        return HashUtil.sha256(phone.trim() + reporterSalt);
    }

    @Transactional
    public OtpRequestResult requestOtp(String phone, String purpose) {
        if (phone == null || phone.isBlank()) {
            throw ApiException.badRequest("PHONE_REQUIRED", "A phone number is required to send an OTP");
        }
        String pHash = phoneHash(phone);

        // invalidate any earlier live challenge for this phone
        otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(pHash).ifPresent(prev -> {
            prev.setConsumed(true);
            otpRepo.save(prev);
        });

        String code = generateCode();
        OtpChallenge ch = new OtpChallenge();
        ch.setPhoneHash(pHash);
        ch.setCodeHash(HashUtil.sha256(code + reporterSalt));
        ch.setPurpose(purpose);
        ch.setExpiresAt(Instant.now().plus(ttlSeconds, ChronoUnit.SECONDS));
        otpRepo.save(ch);

      
        log.info("OTP for {} (purpose={}) = {}", maskPhone(phone), purpose, code);

        return new OtpRequestResult(true, maskPhone(phone), devMode ? code : null, ttlSeconds);
    }

  
    @Transactional
    public String verifyOtp(String phone, String code) {
        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            throw ApiException.badRequest("OTP_INVALID", "Phone and code are required");
        }
        String pHash = phoneHash(phone);
        Optional<OtpChallenge> opt = otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(pHash);
        OtpChallenge ch = opt.orElseThrow(() ->
                ApiException.badRequest("OTP_NOT_FOUND", "No active OTP. Please request a new code."));

        if (ch.getExpiresAt().isBefore(Instant.now())) {
            ch.setConsumed(true);
            otpRepo.save(ch);
            throw ApiException.badRequest("OTP_EXPIRED", "This OTP has expired. Please request a new code.");
        }
        if (ch.getAttempts() >= maxAttempts) {
            ch.setConsumed(true);
            otpRepo.save(ch);
            throw ApiException.badRequest("OTP_LOCKED", "Too many attempts. Please request a new code.");
        }

        ch.setAttempts(ch.getAttempts() + 1);
        boolean ok = HashUtil.sha256(code.trim() + reporterSalt).equals(ch.getCodeHash());
        if (!ok) {
            otpRepo.save(ch);
            throw ApiException.badRequest("OTP_MISMATCH", "Incorrect code. Please try again.");
        }
        ch.setConsumed(true);
        otpRepo.save(ch);
        return pHash;
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, length);
        int n = RANDOM.nextInt(bound);
        return String.format("%0" + length + "d", n);
    }

    private String maskPhone(String phone) {
        String p = phone.trim();
        if (p.length() <= 3) return "***";
        return "*".repeat(Math.max(2, p.length() - 3)) + p.substring(p.length() - 3);
    }
}
