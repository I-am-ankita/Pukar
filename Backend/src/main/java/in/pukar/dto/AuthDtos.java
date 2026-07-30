package in.pukar.dto;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {}

    public record RefreshRequest(
            @NotBlank String refreshToken) {}

    public record OtpRequest(
            @NotBlank String phone) {}

    public record OtpRequestResponse(
            boolean sent,
            String maskedPhone,
            String devCode,            // populated only in dev-mode (no SMS gateway)
            long expiresInSeconds) {}

    public record OtpVerifyRequest(
            @NotBlank String phone,
            @NotBlank String code) {}

    public record CitizenAuthResponse(
            String citizenToken,
            long expiresIn,
            String maskedPhone) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            String username,
            String fullName,
            String role,
            String departmentId,
            String departmentName) {}
}
