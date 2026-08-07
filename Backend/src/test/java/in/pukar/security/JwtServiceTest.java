package in.pukar.security;

import in.pukar.support.TestDataFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtService}.
 * Validates JWT token generation, parsing, and citizen token handling.
 */
@DisplayName("JwtService - JWT Token Management")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                TestDataFactory.JWT_SECRET,
                60,   // access token: 60 minutes
                7,    // refresh token: 7 days
                30    // citizen token: 30 minutes
        );
    }

    @Nested
    @DisplayName("Access Token")
    class AccessTokenTests {

        @Test
        @DisplayName("Should generate a valid access token with custom claims")
        void generateAccessToken_success() {
            Map<String, Object> claims = Map.of("role", "OFFICER", "uid", "user-1");

            String token = jwtService.generateAccessToken("officer1", claims);

            assertNotNull(token);
            assertFalse(token.isBlank());
            assertEquals("officer1", jwtService.getUsername(token));

            Claims parsed = jwtService.parse(token);
            assertEquals("OFFICER", parsed.get("role", String.class));
            assertEquals("user-1", parsed.get("uid", String.class));
        }

        @Test
        @DisplayName("Should return access token expiry in seconds")
        void getAccessTokenSeconds_returnsConfiguredValue() {
            assertEquals(3600, jwtService.getAccessTokenSeconds());
        }
    }

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should generate a valid refresh token with type claim")
        void generateRefreshToken_success() {
            String token = jwtService.generateRefreshToken("citizen1");

            assertNotNull(token);
            Claims claims = jwtService.parse(token);
            assertEquals("citizen1", claims.getSubject());
            assertEquals("refresh", claims.get("type", String.class));
        }
    }

    @Nested
    @DisplayName("Citizen Token")
    class CitizenTokenTests {

        @Test
        @DisplayName("Should generate and parse citizen token with reporter hash")
        void generateAndParseCitizenToken_success() {
            String reporterHash = "hashed-reporter-identity";

            String token = jwtService.generateCitizenToken(reporterHash);
            String parsed = jwtService.parseCitizenReporterHash(token);

            assertEquals(reporterHash, parsed);
            assertEquals(1800, jwtService.getCitizenTokenSeconds());
        }

        @Test
        @DisplayName("Should reject access token when parsing as citizen token")
        void parseCitizenReporterHash_rejectsAccessToken() {
            String accessToken = jwtService.generateAccessToken("user", Map.of("role", "CITIZEN"));

            assertThrows(JwtException.class,
                    () -> jwtService.parseCitizenReporterHash(accessToken));
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class ValidationTests {

        @Test
        @DisplayName("Should reject tampered token")
        void parse_rejectsTamperedToken() {
            String token = jwtService.generateAccessToken("user", Map.of());
            String tampered = token.substring(0, token.length() - 5) + "xxxxx";

            assertThrows(Exception.class, () -> jwtService.parse(tampered));
        }
    }
}
