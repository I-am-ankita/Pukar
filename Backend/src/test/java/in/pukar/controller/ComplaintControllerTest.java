package in.pukar.controller;

import in.pukar.common.ApiException;
import in.pukar.common.ApiResponse;
import in.pukar.dto.ComplaintDtos.*;
import in.pukar.entity.ComplaintPriority;
import in.pukar.entity.ComplaintStatus;
import in.pukar.security.AppUserDetails;
import in.pukar.security.JwtService;
import in.pukar.service.ComplaintService;
import in.pukar.support.TestDataFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ComplaintController}.
 * Tests complaint REST endpoints with mocked service layer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplaintController - Complaint REST Endpoints")
class ComplaintControllerTest {

    @Mock private ComplaintService complaintService;
    @Mock private JwtService jwtService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private ComplaintController complaintController;

    @Nested
    @DisplayName("Public citizen endpoints")
    class PublicEndpointTests {

        @Test
        @DisplayName("submit() should delegate to ComplaintService")
        void submit_success() {
            SubmitRequest request = new SubmitRequest(
                    "9876543210", "BRIBERY", null, "Bribe at office", "Mumbai", null,
                    null, null, null, null);
            SubmitResponse serviceResponse = new SubmitResponse(
                    "PUK-2026-000001", null, "Submitted", 72, ComplaintPriority.HIGH);
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
            when(complaintService.submit(request, "127.0.0.1")).thenReturn(serviceResponse);

            ApiResponse<SubmitResponse> response = complaintController.submit(request, httpRequest);

            assertTrue(response.isSuccess());
            assertEquals("PUK-2026-000001", response.getData().trackingCode());
        }

        @Test
        @DisplayName("track() should return complaint tracking details")
        void track_success() {
            TrackResponse trackResponse = new TrackResponse(
                    "PUK-2026-000001", "BRIBERY", null, ComplaintStatus.SUBMITTED,
                    ComplaintPriority.HIGH, "Andheri", "Ward 45", "Revenue", null,
                    "Officer One", null, Instant.now(), Instant.now(),
                    Instant.now().plusSeconds(3600), 0, null, List.of(), List.of());
            when(complaintService.track("PUK-2026-000001")).thenReturn(trackResponse);

            ApiResponse<TrackResponse> response = complaintController.track("PUK-2026-000001");

            assertTrue(response.isSuccess());
            assertEquals("BRIBERY", response.getData().category());
        }

        @Test
        @DisplayName("feedback() should parse citizen token and submit feedback")
        void feedback_withCitizenToken_success() {
            FeedbackRequest request = new FeedbackRequest(true, "Resolved", null);
            when(jwtService.parseCitizenReporterHash("valid-token")).thenReturn("reporter-hash");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            ApiResponse<String> response = complaintController.feedback(
                    "PUK-2026-000001", request, "Bearer valid-token", httpRequest);

            assertTrue(response.isSuccess());
            assertEquals("feedback_recorded", response.getData());
            verify(complaintService).submitFeedback("PUK-2026-000001", request,
                    "reporter-hash", "127.0.0.1");
        }

        @Test
        @DisplayName("feedback() should throw unauthorized for invalid citizen token")
        void feedback_invalidCitizenToken_throwsUnauthorized() {
            FeedbackRequest request = new FeedbackRequest(true, null, null);
            when(jwtService.parseCitizenReporterHash("bad-token"))
                    .thenThrow(new RuntimeException("invalid"));

            ApiException ex = assertThrows(ApiException.class, () ->
                    complaintController.feedback("PUK-2026-000001", request, "bad-token", httpRequest));

            assertEquals("CITIZEN_TOKEN_INVALID", ex.getCode());
        }
    }

    @Nested
    @DisplayName("Staff endpoints")
    class StaffEndpointTests {

        @Test
        @DisplayName("list() should route officer to listForOfficer")
        void list_officerRole_usesOfficerList() {
            AppUserDetails officer = TestDataFactory.officerDetails();
            Page<ComplaintView> page = new PageImpl<>(List.of());
            when(complaintService.listForOfficer(officer, null, 0, 20)).thenReturn(page);

            ApiResponse<Page<ComplaintView>> response =
                    complaintController.list(officer, null, null, 0, 20);

            assertTrue(response.isSuccess());
            verify(complaintService).listForOfficer(officer, null, 0, 20);
            verify(complaintService, never()).listAll(anyInt(), anyInt());
        }

        @Test
        @DisplayName("detail() should return complaint by ID")
        void detail_success() {
            ComplaintView view = mock(ComplaintView.class);
            when(complaintService.getDetail("complaint-1")).thenReturn(view);

            ApiResponse<ComplaintView> response = complaintController.detail("complaint-1");

            assertTrue(response.isSuccess());
            assertSame(view, response.getData());
        }

        @Test
        @DisplayName("updateStatus() should delegate status change to service")
        void updateStatus_success() {
            AppUserDetails officer = TestDataFactory.officerDetails();
            StatusUpdateRequest request = new StatusUpdateRequest("IN_PROGRESS", null, null, "Started");
            when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

            ApiResponse<String> response = complaintController.updateStatus(
                    "complaint-1", request, officer, httpRequest);

            assertTrue(response.isSuccess());
            assertEquals("status_updated", response.getData());
            verify(complaintService).updateStatus("complaint-1", request, officer, "127.0.0.1");
        }
    }
}
