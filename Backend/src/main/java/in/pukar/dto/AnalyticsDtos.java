package in.pukar.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class AnalyticsDtos {

    public record HeatPoint(
            String id,
            String trackingCode,
            String category,
            String status,
            String priority,
            String ward,
            String locationText,
            double lat,
            double lng,
            double weight,
            int count) {}

    public record HeatmapResponse(
            List<HeatPoint> points,
            Instant generatedAt) {}

    public record ClusterDto(
            String id,
            String category,
            String label,
            BigDecimal latitudeCenter,
            BigDecimal longitudeCenter,
            int memberCount,
            String primaryComplaintId) {}

    public record PublicStats(
            long unresolved,
            long resolved,
            long total,
            double resolutionRate,
            List<WardStat> worstWards,
            List<CategoryStat> byCategory) {}

    public record WardStat(
            int rank,
            String ward,
            String zone,
            long reports,
            long resolved,
            int resolvedPct) {}

    public record CategoryStat(
            String category,
            long count) {}

    public record DepartmentScorecard(
            String departmentId,
            String departmentName,
            String code,
            long totalComplaints,
            long resolvedCount,
            double avgResolutionHours,
            double slaCompliancePct,
            double citizenSatisfactionPct,
            double efficiencyIndex) {}

    public record GraphNode(
            String id,
            String type,
            String label,
            double riskScore) {}

    public record GraphEdge(
            String source,
            String target,
            String relation,
            int weight) {}

    public record CorruptionGraph(
            List<GraphNode> nodes,
            List<GraphEdge> edges) {}
}
