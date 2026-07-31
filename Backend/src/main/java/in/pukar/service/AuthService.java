package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.dto.AuthDtos.*;
import in.pukar.entity.Role;
import in.pukar.entity.User;
import in.pukar.repository.UserRepository;
import in.pukar.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository userRepo;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authManager, JwtService jwtService,
                       UserRepository userRepo, AuditService auditService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.auditService = auditService;
    }

    public AuthResponse login(LoginRequest req, String ip) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));

        User user = userRepo.findByUsername(req.username())
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid credentials"));

        String role = user.getRoles().stream().map(Role::getName)
                .map(Enum::name).findFirst().orElse("CITIZEN");
        String deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        String deptName = user.getDepartment() != null ? user.getDepartment().getName() : null;

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("uid", user.getId());
        if (deptId != null) claims.put("dept", deptId);

        String access = jwtService.generateAccessToken(user.getUsername(), claims);
        String refresh = jwtService.generateRefreshToken(user.getUsername());

        auditService.record("USER_LOGIN", "USER", user.getId(), user.getId(), role,
                null, "{\"username\":\"" + user.getUsername() + "\"}", ip);

        return new AuthResponse(access, refresh, jwtService.getAccessTokenSeconds(),
                user.getUsername(), user.getFullName(), role, deptId, deptName);
    }

    public AuthResponse refresh(RefreshRequest req) {
        String username;
        try {
            username = jwtService.getUsername(req.refreshToken());
        } catch (Exception e) {
            throw ApiException.unauthorized("INVALID_REFRESH", "Refresh token invalid or expired");
        }
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH", "User not found"));

        String role = user.getRoles().stream().map(Role::getName)
                .map(Enum::name).findFirst().orElse("CITIZEN");
        String deptId = user.getDepartment() != null ? user.getDepartment().getId() : null;
        String deptName = user.getDepartment() != null ? user.getDepartment().getName() : null;

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("uid", user.getId());
        if (deptId != null) claims.put("dept", deptId);

        String access = jwtService.generateAccessToken(user.getUsername(), claims);
        return new AuthResponse(access, req.refreshToken(), jwtService.getAccessTokenSeconds(),
                user.getUsername(), user.getFullName(), role, deptId, deptName);
    }
}
