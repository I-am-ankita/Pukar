package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AdminDtos.*;
import in.pukar.security.AppUserDetails;
import in.pukar.service.AdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final AdminService adminService;

    public DepartmentController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/public/all")
    public ApiResponse<List<DepartmentDto>> publicList() {
        return ApiResponse.success(adminService.listDepartments());
    }

    @GetMapping("/{departmentId}/officers")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ApiResponse<List<UserDto>> officers(@PathVariable String departmentId) {
        return ApiResponse.success(adminService.officersInDepartment(departmentId));
    }

    @GetMapping("/my/officers")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ApiResponse<List<UserDto>> myDeptOfficers(@AuthenticationPrincipal AppUserDetails me) {
        return ApiResponse.success(adminService.officersInDepartment(me.getDepartmentId()));
    }
}
