package in.pukar.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AdminDtos {

    public record CreateUserRequest(
            @NotBlank String username,
            @Email @NotBlank String email,
            @NotBlank String password,
            String fullName,
            @NotBlank String role,        // OFFICER | SUPERVISOR | WATCHDOG | ADMIN
            String departmentId,
            String wardId) {}

    public record UserDto(
            String id,
            String username,
            String email,
            String fullName,
            String role,
            String departmentId,
            String departmentName,
            String wardId,
            String wardName,
            boolean active) {}

    public record UpdateRoleRequest(
            @NotBlank String role,
            String departmentId,
            String wardId) {}

    public record SlaRuleRequest(
            @NotBlank String category,
            @Min(1) int level1Hours,
            @Min(1) int level2Hours,
            @Min(1) int level3Hours) {}

    public record SlaRuleDto(
            String id,
            String category,
            int level1Hours,
            int level2Hours,
            int level3Hours,
            boolean active) {}

    public record DepartmentDto(
            String id,
            String name,
            String code,
            String district,
            String state) {}

    public record WardDto(
            String id,
            String code,
            String name,
            String zone,
            List<DepartmentDto> departments) {}

    public record CreateWardRequest(
            @NotBlank String code,
            @NotBlank String name,
            String zone) {}

    public record LinkDepartmentRequest(
            @NotBlank String departmentId) {}
}
