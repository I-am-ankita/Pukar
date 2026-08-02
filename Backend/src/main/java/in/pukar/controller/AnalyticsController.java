package in.pukar.controller;

import in.pukar.common.ApiResponse;
import in.pukar.dto.AnalyticsDtos.*;
import in.pukar.service.AnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    // ---- Public ----

    @GetMapping("/heatmap")
    public ApiResponse<HeatmapResponse> heatmap(@RequestParam(required = false, defaultValue = "all") String window,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) String severity) {
        return ApiResponse.success(analyticsService.heatmap(window, category, severity));
    }

    @GetMapping("/public/stats")
    public ApiResponse<PublicStats> publicStats() {
        return ApiResponse.success(analyticsService.publicStats());
    }

    @GetMapping("/public/clusters")
    public ApiResponse<List<ClusterDto>> clusters() {
        return ApiResponse.success(analyticsService.clusters());
    }

    // ---- Staff / oversight ----

    @GetMapping("/department-scorecard/all")
    @PreAuthorize("hasAnyRole('SUPERVISOR','WATCHDOG','ADMIN')")
    public ApiResponse<List<DepartmentScorecard>> scorecards() {
        return ApiResponse.success(analyticsService.departmentScorecards());
    }

    @GetMapping("/corruption-graph")
    @PreAuthorize("hasAnyRole('WATCHDOG','ADMIN')")
    public ApiResponse<CorruptionGraph> corruptionGraph() {
        return ApiResponse.success(analyticsService.corruptionGraph());
    }
}
