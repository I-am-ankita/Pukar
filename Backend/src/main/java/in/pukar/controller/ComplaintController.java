package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.ComplaintDtos.*;
import in.pukar.security.AppUserDetails;
import in.pukar.security.JwtService;
import in.pukar.service.ComplaintService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import static in.pukar.controller.AuthController.clientIp;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;
    private final JwtService jwtService;

    public ComplaintController(ComplaintService complaintService, JwtService jwtService) {
        this.complaintService = complaintService;
        this.jwtService = jwtService;
    }

    // ---- Public (citizen) endpoints ----

    @PostMapping
    public ApiResponse<SubmitResponse> submit(@Valid @RequestBody SubmitRequest req, HttpServletRequest http) {
        return ApiResponse.success(complaintService.submit(req, clientIp(http)));
    }

    @GetMapping("/track/{trackingCode}")
    public ApiResponse<TrackResponse> track(@PathVariable String trackingCode) {
        return ApiResponse.success(complaintService.track(trackingCode));
    }

    @PostMapping("/track/{trackingCode}/evidence")
    public ApiResponse<EvidenceDto> addEvidence(@PathVariable String trackingCode,
                                                @RequestParam("file") MultipartFile file,
                                                HttpServletRequest http) {
        return ApiResponse.success(complaintService.addCitizenEvidence(trackingCode, file, clientIp(http)));
    }

    @PostMapping("/track/{trackingCode}/feedback")
    public ApiResponse<String> feedback(@PathVariable String trackingCode,
                                        @Valid @RequestBody FeedbackRequest req,
                                        @RequestHeader(value = "X-Citizen-Token", required = false) String citizenToken,
                                        HttpServletRequest http) {
        String reporterHash = null;
        if (citizenToken != null && !citizenToken.isBlank()) {
            try {
                reporterHash = jwtService.parseCitizenReporterHash(citizenToken.replaceFirst("(?i)^Bearer ", "").trim());
            } catch (Exception e) {
                throw in.pukar.common.ApiException.unauthorized("CITIZEN_TOKEN_INVALID",
                        "Your verification has expired. Please verify again.");
            }
        }
        complaintService.submitFeedback(trackingCode, req, reporterHash, clientIp(http));
        return ApiResponse.success("feedback_recorded");
    }

    // ---- Staff endpoints ----

    @GetMapping
    @PreAuthorize("hasAnyRole('OFFICER','SUPERVISOR','WATCHDOG','ADMIN')")
    public ApiResponse<Page<ComplaintView>> list(@AuthenticationPrincipal AppUserDetails me,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String departmentId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        boolean elevated = me.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISOR")
                        || a.getAuthority().equals("ROLE_WATCHDOG")
                        || a.getAuthority().equals("ROLE_ADMIN"));
        Page<ComplaintView> result;
        if (!elevated) {
            result = complaintService.listForOfficer(me, status, page, size);
        } else if (departmentId != null && !departmentId.isBlank()) {
            result = complaintService.listForDepartment(departmentId, page, size);
        } else {
            result = complaintService.listAll(page, size);
        }
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OFFICER','SUPERVISOR','WATCHDOG','ADMIN')")
    public ApiResponse<ComplaintView> detail(@PathVariable String id) {
        return ApiResponse.success(complaintService.getDetail(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OFFICER','SUPERVISOR','ADMIN')")
    public ApiResponse<String> updateStatus(@PathVariable String id,
                                            @Valid @RequestBody StatusUpdateRequest req,
                                            @AuthenticationPrincipal AppUserDetails me,
                                            HttpServletRequest http) {
        complaintService.updateStatus(id, req, me, clientIp(http));
        return ApiResponse.success("status_updated");
    }

    @GetMapping("/{id}/assignable-officers")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ApiResponse<java.util.List<in.pukar.dto.AdminDtos.UserDto>> assignableOfficers(@PathVariable String id) {
        return ApiResponse.success(complaintService.assignableOfficers(id));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ApiResponse<String> assign(@PathVariable String id,
                                      @Valid @RequestBody AssignRequest req,
                                      @AuthenticationPrincipal AppUserDetails me,
                                      HttpServletRequest http) {
        complaintService.assign(id, req, me, clientIp(http));
        return ApiResponse.success("assigned");
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public ApiResponse<String> approve(@PathVariable String id,
                                       @AuthenticationPrincipal AppUserDetails me,
                                       HttpServletRequest http) {
        complaintService.approve(id, me, clientIp(http));
        return ApiResponse.success("approved");
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('SUPERVISOR','WATCHDOG','ADMIN')")
    public ApiResponse<String> escalate(@PathVariable String id,
                                        @Valid @RequestBody EscalateRequest req,
                                        @AuthenticationPrincipal AppUserDetails me,
                                        HttpServletRequest http) {
        complaintService.escalateManual(id, req, me, clientIp(http));
        return ApiResponse.success("escalated");
    }

    @PostMapping("/{id}/resolution-evidence")
    @PreAuthorize("hasAnyRole('OFFICER','SUPERVISOR','ADMIN')")
    public ApiResponse<ComplaintView> resolutionEvidence(@PathVariable String id,
                                                         @RequestParam("file") MultipartFile file,
                                                         @AuthenticationPrincipal AppUserDetails me,
                                                         HttpServletRequest http) {
        return ApiResponse.success(complaintService.addResolutionEvidence(id, file, me, clientIp(http)));
    }
}
