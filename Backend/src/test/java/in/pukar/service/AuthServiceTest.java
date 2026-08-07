package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.dto.AuthDtos.AuthResponse;
import in.pukar.dto.AuthDtos.LoginRequest;
import in.pukar.dto.AuthDtos.RefreshRequest;
import in.pukar.entity.User;
import in.pukar.repository.UserRepository;
import in.pukar.security.JwtService;
import in.pukar.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService}.
 * Tests staff login and token refresh flows with mocked security dependencies.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Authentication & Token Refresh")
class AuthServiceTest {

    @Mock
    private AuthenticationManager authManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepo;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataFactory.officerUser();
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Should authenticate user and return JWT tokens with profile")
        void login_success() {
            LoginRequest request = new LoginRequest("officer1", "password123");
            when(userRepo.findByUsername("officer1")).thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(eq("officer1"), anyMap()))
                    .thenReturn("access-token");
            when(jwtService.generateRefreshToken("officer1")).thenReturn("refresh-token");
            when(jwtService.getAccessTokenSeconds()).thenReturn(28800L);

            AuthResponse response = authService.login(request, "127.0.0.1");

            assertNotNull(response);
            assertEquals("access-token", response.accessToken());
            assertEquals("refresh-token", response.refreshToken());
            assertEquals("officer1", response.username());
            assertEquals("Test Officer", response.fullName());
            assertEquals("OFFICER", response.role());
            assertEquals("dept-revenue", response.departmentId());

            verify(authManager).authenticate(
                    new UsernamePasswordAuthenticationToken("officer1", "password123"));
            verify(auditService).record(eq("USER_LOGIN"), eq("USER"), eq("officer-1"),
                    eq("officer-1"), eq("OFFICER"), isNull(), anyString(), eq("127.0.0.1"));
        }

        @Test
        @DisplayName("Should propagate authentication failure from AuthenticationManager")
        void login_invalidCredentials_throwsException() {
            LoginRequest request = new LoginRequest("officer1", "wrong");
            doThrow(new BadCredentialsException("Bad credentials"))
                    .when(authManager).authenticate(any());

            assertThrows(BadCredentialsException.class,
                    () -> authService.login(request, "127.0.0.1"));
            verify(userRepo, never()).findByUsername(any());
        }

        @Test
        @DisplayName("Should throw unauthorized when user not found after authentication")
        void login_userNotFoundAfterAuth_throwsUnauthorized() {
            LoginRequest request = new LoginRequest("ghost", "password");
            when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class,
                    () -> authService.login(request, "127.0.0.1"));

            assertEquals("INVALID_CREDENTIALS", ex.getCode());
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        @DisplayName("Should issue new access token from valid refresh token")
        void refresh_success() {
            RefreshRequest request = new RefreshRequest("valid-refresh-token");
            when(jwtService.getUsername("valid-refresh-token")).thenReturn("officer1");
            when(userRepo.findByUsername("officer1")).thenReturn(Optional.of(testUser));
            when(jwtService.generateAccessToken(eq("officer1"), anyMap()))
                    .thenReturn("new-access-token");
            when(jwtService.getAccessTokenSeconds()).thenReturn(28800L);

            AuthResponse response = authService.refresh(request);

            assertEquals("new-access-token", response.accessToken());
            assertEquals("valid-refresh-token", response.refreshToken());
            assertEquals("OFFICER", response.role());
        }

        @Test
        @DisplayName("Should throw unauthorized for invalid refresh token")
        void refresh_invalidToken_throwsUnauthorized() {
            RefreshRequest request = new RefreshRequest("bad-token");
            when(jwtService.getUsername("bad-token")).thenThrow(new RuntimeException("invalid"));

            ApiException ex = assertThrows(ApiException.class,
                    () -> authService.refresh(request));

            assertEquals("INVALID_REFRESH", ex.getCode());
        }

        @Test
        @DisplayName("Should throw unauthorized when user no longer exists")
        void refresh_userNotFound_throwsUnauthorized() {
            RefreshRequest request = new RefreshRequest("valid-refresh-token");
            when(jwtService.getUsername("valid-refresh-token")).thenReturn("deleted-user");
            when(userRepo.findByUsername("deleted-user")).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class,
                    () -> authService.refresh(request));

            assertEquals("INVALID_REFRESH", ex.getCode());
        }
    }
}
