package in.pukar.dto;

import in.pukar.entity.ComplaintPriority;
import in.pukar.entity.ComplaintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class ComplaintDtos {

    public record SubmitRequest(
            String reporterPhone,            // hashed server-side; optional anonymous token
            @NotBlank String category,
            String subCategory,
            @NotBlank @Size(min = 5, max = 4000) String description,
            String locationText,
            String ward,
            String wardId,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDate incidentDate) {}

    public record SubmitResponse(
            String trackingCode,
            String reporterToken,
            String message,
            long estimatedSlaHours,
            ComplaintPriority priority) {}

    public record StatusEntry(
            ComplaintStatus status,
            Instant at,
            String reason) {}

    public record TrackResponse(
            String trackingCode,
            String category,
            String subCategory,
            ComplaintStatus status,
            ComplaintPriority priority,
            String locationText,
            String ward,
            String departmentName,
            String watchdogName,
            String assignedOfficerName,
            String supervisorName,
            Instant submittedAt,
            Instant lastUpdatedAt,
            Instant slaDeadline,
            int escalationCount,
            String resolutionNote,
            List<StatusEntry> statusHistory,
            List<EvidenceDto> evidence) {}

    public record EvidenceDto(
            String id,
            String fileName,
            String fileUrl,
            String fileType,
            String uploadType,
            Instant uploadedAt) {}

    public record FeedbackRequest(
            boolean isResolved,
            String remarks,
            String reporterToken) {}   // fallback proof for anonymous (no-phone) reporters

    public record StatusUpdateRequest(
            @NotBlank String status,
            String resolutionNote,
            String resolutionEvidenceUrl,
            String reason) {}

    public record AssignRequest(
            @NotBlank String officerId,
            String reason) {}

    public record EscalateRequest(
            @NotBlank String reason,
            String triggeredBy) {}

    // staff-facing list/detail view
    public record ComplaintView(
            String id,
            String trackingCode,
            String category,
            String subCategory,
            String description,
            String status,
            String priority,
            BigDecimal priorityScore,
            String locationText,
            String ward,
            String wardId,
            String wardName,
            BigDecimal latitude,
            BigDecimal longitude,
            String departmentId,
            String departmentName,
            String assignedOfficerId,
            String assignedOfficerName,
            String supervisorId,
            String supervisorName,
            String watchdogId,
            String watchdogName,
            String escalatedToName,
            String escalatedToRole,
            String clusterId,
            int escalationCount,
            Instant slaDeadline,
            boolean slaBreached,
            Boolean citizenFeedback,
            String resolutionNote,
            Instant submittedAt,
            Instant lastUpdatedAt,
            List<StatusEntry> statusHistory,
            List<EvidenceDto> evidence) {}
}
