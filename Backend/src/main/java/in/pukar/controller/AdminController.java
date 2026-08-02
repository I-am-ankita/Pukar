package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AdminDtos.*;
import in.pukar.security.AppUserDetails;
import in.pukar.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---- Users ----

    @GetMapping("/users")
    public ApiResponse<List<UserDto>> listUsers() {
        return ApiResponse.success(adminService.listUsers());
    }

    @PostMapping("/users")
    public ApiResponse<UserDto> createUser(@Valid @RequestBody CreateUserRequest req,
                                           @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.createUser(req, me.getId()));
    }

    @PatchMapping("/users/{userId}/role")
    public ApiResponse<UserDto> updateRole(@PathVariable String userId,
                                           @Valid @RequestBody UpdateRoleRequest req,
                                           @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.updateRole(userId, req, me.getId()));
    }

    // ---- SLA rules ----

    @GetMapping("/sla-rules")
    public ApiResponse<List<SlaRuleDto>> listSlaRules() {
        return ApiResponse.success(adminService.listSlaRules());
    }

    @PostMapping("/sla-rules")
    public ApiResponse<SlaRuleDto> createSlaRule(@Valid @RequestBody SlaRuleRequest req,
                                                 @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.createSlaRule(req, me.getId()));
    }

    @PutMapping("/sla-rules/{id}")
    public ApiResponse<SlaRuleDto> updateSlaRule(@PathVariable String id,
                                                 @Valid @RequestBody SlaRuleRequest req,
                                                 @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.updateSlaRule(id, req, me.getId()));
    }

    // ---- Wards ----

    @GetMapping("/wards")
    public ApiResponse<List<WardDto>> listWards() {
        return ApiResponse.success(adminService.listWards());
    }

    @PostMapping("/wards")
    public ApiResponse<WardDto> createWard(@Valid @RequestBody CreateWardRequest req,
                                           @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.createWard(req, me.getId()));
    }

    @PostMapping("/wards/{wardId}/departments")
    public ApiResponse<WardDto> linkDepartment(@PathVariable String wardId,
                                               @Valid @RequestBody LinkDepartmentRequest req,
                                               @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.linkDepartment(wardId, req, me.getId()));
    }

    @DeleteMapping("/wards/{wardId}/departments/{deptId}")
    public ApiResponse<WardDto> unlinkDepartment(@PathVariable String wardId, @PathVariable String deptId,
                                                 @AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.unlinkDepartment(wardId, deptId, me.getId()));
    }
}
