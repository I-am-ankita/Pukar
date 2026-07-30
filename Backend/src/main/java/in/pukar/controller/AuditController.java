package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AuditDtos.*;
import in.pukar.service.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('WATCHDOG','ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{complaintId}")
    public ApiResponse<List<AuditEntry>> trail(@PathVariable String complaintId) {
        return ApiResponse.success(auditService.trail(complaintId));
    }

    @GetMapping("/verify")
    public ApiResponse<AuditVerifyResult> verify() {
        return ApiResponse.success(auditService.verifyChain());
    }
}
