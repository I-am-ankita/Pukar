package in.pukar.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HashUtil}.
 * Validates SHA-256 hashing used for reporter identity and OTP verification.
 */
@DisplayName("HashUtil - SHA-256 Utility")
class HashUtilTest {

    @Test
    @DisplayName("Should produce a 64-character lowercase hex digest")
    void sha256_producesValidHexDigest() {
        String hash = HashUtil.sha256("hello-world");

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("Should produce deterministic output for the same input")
    void sha256_isDeterministic() {
        String input = "9876543210test-salt";

        assertEquals(HashUtil.sha256(input), HashUtil.sha256(input));
    }

    @Test
    @DisplayName("Should produce different hashes for different inputs")
    void sha256_differsForDifferentInputs() {
        String hash1 = HashUtil.sha256("phone1");
        String hash2 = HashUtil.sha256("phone2");

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should handle empty string without error")
    void sha256_handlesEmptyString() {
        assertDoesNotThrow(() -> HashUtil.sha256(""));
        assertEquals(64, HashUtil.sha256("").length());
    }
}
