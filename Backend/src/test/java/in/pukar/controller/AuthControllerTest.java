package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AuthDtos.*;
import in.pukar.security.JwtService;
import in.pukar.service.AuthService;
import in.pukar.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthController}.
 * Tests REST controller logic in isolation without Spring MVC context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - Authentication REST Endpoints")
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private OtpService otpService;
    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthController authController;

    @Nested
    @DisplayName("login()")
    class LoginEndpointTests {

        @Test
        @DisplayName("Should delegate login to AuthService and wrap response")
        void login_delegatesToService() {
            LoginRequest request = new LoginRequest("officer1", "secret");
            AuthResponse authResponse = new AuthResponse(
                    "access", "refresh", 3600, "officer1", "Officer One",
                    "OFFICER", "dept-1", "Revenue");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
            when(authService.login(request, "192.168.1.1")).thenReturn(authResponse);

            ApiResponse<AuthResponse> response = authController.login(request, httpRequest);

            assertTrue(response.isSuccess());
            assertEquals("access", response.getData().accessToken());
            assertEquals("OFFICER", response.getData().role());
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshEndpointTests {

        @Test
        @DisplayName("Should delegate token refresh to AuthService")
        void refresh_delegatesToService() {
            RefreshRequest request = new RefreshRequest("refresh-token");
            AuthResponse authResponse = new AuthResponse(
                    "new-access", "refresh-token", 3600, "officer1", "Officer One",
                    "OFFICER", null, null);
            when(authService.refresh(request)).thenReturn(authResponse);

            ApiResponse<AuthResponse> response = authController.refresh(request);

            assertTrue(response.isSuccess());
            assertEquals("new-access", response.getData().accessToken());
        }
    }

    @Nested
    @DisplayName("OTP endpoints")
    class OtpEndpointTests {

        @Test
        @DisplayName("Should request OTP and return masked phone response")
        void requestOtp_success() {
            OtpRequest request = new OtpRequest("9876543210");
            when(otpService.requestOtp("9876543210", "CITIZEN_FEEDBACK"))
                    .thenReturn(new OtpService.OtpRequestResult(true, "****210", "123456", 300));

            ApiResponse<OtpRequestResponse> response = authController.requestOtp(request);

            assertTrue(response.isSuccess());
            assertTrue(response.getData().sent());
            assertEquals("****210", response.getData().maskedPhone());
        }

        @Test
        @DisplayName("Should verify OTP and return citizen JWT token")
        void verifyOtp_success() {
            OtpVerifyRequest request = new OtpVerifyRequest("9876543210", "123456");
            when(otpService.verifyOtp("9876543210", "123456")).thenReturn("reporter-hash");
            when(jwtService.generateCitizenToken("reporter-hash")).thenReturn("citizen-jwt");
            when(jwtService.getCitizenTokenSeconds()).thenReturn(1800L);

            ApiResponse<CitizenAuthResponse> response = authController.verifyOtp(request);

            assertTrue(response.isSuccess());
            assertEquals("citizen-jwt", response.getData().citizenToken());
            assertEquals(1800L, response.getData().expiresIn());
        }
    }

    @Nested
    @DisplayName("clientIp()")
    class ClientIpTests {

        @Test
        @DisplayName("Should prefer X-Forwarded-For header when present")
        void clientIp_usesForwardedHeader() {
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");

            assertEquals("203.0.113.1", AuthController.clientIp(httpRequest));
        }

        @Test
        @DisplayName("Should fall back to remote address when no forwarded header")
        void clientIp_usesRemoteAddr() {
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            assertEquals("127.0.0.1", AuthController.clientIp(httpRequest));
        }
    }

    @Test
    @DisplayName("logout() should return success message")
    void logout_returnsSuccess() {
        ApiResponse<String> response = authController.logout();

        assertTrue(response.isSuccess());
        assertEquals("logged_out", response.getData());
    }
}
