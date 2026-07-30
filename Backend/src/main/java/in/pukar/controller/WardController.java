package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AdminDtos.WardDto;
import in.pukar.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wards")
public class WardController {

    private final AdminService adminService;

    public WardController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/public/all")
    public ApiResponse<List<WardDto>> publicList() {
        return ApiResponse.success(adminService.listWards());
    }
}
