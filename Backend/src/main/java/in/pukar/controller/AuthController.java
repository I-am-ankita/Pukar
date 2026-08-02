package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AuthDtos.*;
import in.pukar.security.JwtService;
import in.pukar.service.AuthService;
import in.pukar.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, OtpService otpService, JwtService jwtService) {
        this.authService = authService;
        this.otpService = otpService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.success(authService.login(req, clientIp(http)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.success(authService.refresh(req));
    }

    // ---- Citizen OTP (proves phone ownership so only the reporter can give feedback) ----

    @PostMapping("/otp/request")
    public ApiResponse<OtpRequestResponse> requestOtp(@Valid @RequestBody OtpRequest req) {
        var r = otpService.requestOtp(req.phone(), "CITIZEN_FEEDBACK");
        return ApiResponse.success(new OtpRequestResponse(r.sent(), r.maskedPhone(), r.devCode(), r.expiresInSeconds()));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<CitizenAuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest req) {
        String reporterHash = otpService.verifyOtp(req.phone(), req.code());
        String token = jwtService.generateCitizenToken(reporterHash);
        return ApiResponse.success(new CitizenAuthResponse(token, jwtService.getCitizenTokenSeconds(), null));
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        // Stateless JWT: client simply discards tokens. Endpoint kept for symmetry.
        return ApiResponse.success("logged_out");
    }

    static String clientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
