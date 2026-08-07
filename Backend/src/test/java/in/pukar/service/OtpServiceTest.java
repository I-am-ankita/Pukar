package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.common.HashUtil;
import in.pukar.entity.OtpChallenge;
import in.pukar.repository.OtpChallengeRepository;
import in.pukar.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OtpService}.
 * Covers OTP request, verification, expiry, and attempt limits.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService - Citizen OTP Verification")
class OtpServiceTest {

    private static final String PHONE = "9876543210";
    private static final String OTP_CODE = "123456";

    @Mock
    private OtpChallengeRepository otpRepo;

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(
                otpRepo,
                TestDataFactory.REPORTER_SALT,
                6,      // length
                300,    // ttl seconds
                5,      // max attempts
                true    // dev mode
        );
    }

    @Nested
    @DisplayName("requestOtp()")
    class RequestOtpTests {

        @Test
        @DisplayName("Should create OTP challenge and return masked phone in dev mode")
        void requestOtp_success() {
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(any()))
                    .thenReturn(Optional.empty());
            when(otpRepo.save(any(OtpChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

            OtpService.OtpRequestResult result = otpService.requestOtp(PHONE, "CITIZEN_FEEDBACK");

            assertTrue(result.sent());
            assertNotNull(result.maskedPhone());
            assertTrue(result.maskedPhone().endsWith("210"));
            assertNotNull(result.devCode(), "Dev mode should expose OTP for testing");
            assertEquals(6, result.devCode().length());
            assertEquals(300, result.expiresInSeconds());

            verify(otpRepo).save(any(OtpChallenge.class));
        }

        @Test
        @DisplayName("Should invalidate previous active OTP before issuing new one")
        void requestOtp_invalidatesPreviousChallenge() {
            OtpChallenge previous = TestDataFactory.activeOtpChallenge(
                    HashUtil.sha256(PHONE + TestDataFactory.REPORTER_SALT), "000000",
                    TestDataFactory.REPORTER_SALT);
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(any()))
                    .thenReturn(Optional.of(previous));
            when(otpRepo.save(any(OtpChallenge.class))).thenAnswer(inv -> inv.getArgument(0));

            otpService.requestOtp(PHONE, "CITIZEN_FEEDBACK");

            assertTrue(previous.isConsumed());
            verify(otpRepo, times(2)).save(any(OtpChallenge.class));
        }

        @Test
        @DisplayName("Should throw bad request when phone is blank")
        void requestOtp_blankPhone_throwsBadRequest() {
            ApiException ex = assertThrows(ApiException.class,
                    () -> otpService.requestOtp("  ", "CITIZEN_FEEDBACK"));

            assertEquals("PHONE_REQUIRED", ex.getCode());
            verify(otpRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("Should verify correct OTP and return phone hash")
        void verifyOtp_success() {
            String phoneHash = HashUtil.sha256(PHONE + TestDataFactory.REPORTER_SALT);
            OtpChallenge challenge = TestDataFactory.activeOtpChallenge(
                    phoneHash, OTP_CODE, TestDataFactory.REPORTER_SALT);
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(phoneHash))
                    .thenReturn(Optional.of(challenge));
            when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            String result = otpService.verifyOtp(PHONE, OTP_CODE);

            assertEquals(phoneHash, result);
            assertTrue(challenge.isConsumed());
        }

        @Test
        @DisplayName("Should throw when no active OTP exists")
        void verifyOtp_noActiveChallenge_throwsNotFound() {
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(any()))
                    .thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class,
                    () -> otpService.verifyOtp(PHONE, OTP_CODE));

            assertEquals("OTP_NOT_FOUND", ex.getCode());
        }

        @Test
        @DisplayName("Should throw when OTP has expired")
        void verifyOtp_expired_throwsExpired() {
            String phoneHash = HashUtil.sha256(PHONE + TestDataFactory.REPORTER_SALT);
            OtpChallenge challenge = TestDataFactory.activeOtpChallenge(
                    phoneHash, OTP_CODE, TestDataFactory.REPORTER_SALT);
            challenge.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(phoneHash))
                    .thenReturn(Optional.of(challenge));
            when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApiException ex = assertThrows(ApiException.class,
                    () -> otpService.verifyOtp(PHONE, OTP_CODE));

            assertEquals("OTP_EXPIRED", ex.getCode());
            assertTrue(challenge.isConsumed());
        }

        @Test
        @DisplayName("Should throw when OTP code is incorrect")
        void verifyOtp_wrongCode_throwsMismatch() {
            String phoneHash = HashUtil.sha256(PHONE + TestDataFactory.REPORTER_SALT);
            OtpChallenge challenge = TestDataFactory.activeOtpChallenge(
                    phoneHash, OTP_CODE, TestDataFactory.REPORTER_SALT);
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(phoneHash))
                    .thenReturn(Optional.of(challenge));
            when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApiException ex = assertThrows(ApiException.class,
                    () -> otpService.verifyOtp(PHONE, "999999"));

            assertEquals("OTP_MISMATCH", ex.getCode());
            assertEquals(1, challenge.getAttempts());
        }

        @Test
        @DisplayName("Should lock OTP after max failed attempts")
        void verifyOtp_maxAttempts_throwsLocked() {
            String phoneHash = HashUtil.sha256(PHONE + TestDataFactory.REPORTER_SALT);
            OtpChallenge challenge = TestDataFactory.activeOtpChallenge(
                    phoneHash, OTP_CODE, TestDataFactory.REPORTER_SALT);
            challenge.setAttempts(5);
            when(otpRepo.findTopByPhoneHashAndConsumedFalseOrderByIdDesc(phoneHash))
                    .thenReturn(Optional.of(challenge));
            when(otpRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ApiException ex = assertThrows(ApiException.class,
                    () -> otpService.verifyOtp(PHONE, OTP_CODE));

            assertEquals("OTP_LOCKED", ex.getCode());
        }
    }

    @Test
    @DisplayName("phoneHash() should return consistent SHA-256 hash")
    void phoneHash_isConsistent() {
        String hash1 = otpService.phoneHash(PHONE);
        String hash2 = otpService.phoneHash("  " + PHONE + "  ");

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }
}
