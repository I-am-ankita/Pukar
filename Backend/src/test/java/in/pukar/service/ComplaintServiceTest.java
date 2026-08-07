package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.dto.ComplaintDtos.*;
import in.pukar.entity.*;
import in.pukar.repository.*;
import in.pukar.security.AppUserDetails;
import in.pukar.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ComplaintService}.
 * Covers complaint submission, tracking, feedback, and staff status updates.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplaintService - Civic Complaint Lifecycle")
class ComplaintServiceTest {

    @Mock private ComplaintRepository complaintRepo;
    @Mock private ComplaintEvidenceRepository evidenceRepo;
    @Mock private ComplaintStatusHistoryRepository historyRepo;
    @Mock private EscalationEventRepository escalationRepo;
    @Mock private SlaRuleRepository slaRepo;
    @Mock private DepartmentRepository departmentRepo;
    @Mock private WardRepository wardRepo;
    @Mock private UserRepository userRepo;
    @Mock private TrackingCodeGenerator codeGenerator;
    @Mock private PriorityScoringService scoringService;
    @Mock private ClusterService clusterService;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;

    private ComplaintService complaintService;

    private Complaint complaint;

    @BeforeEach
    void setUp() {
        complaintService = new ComplaintService(
                complaintRepo, evidenceRepo, historyRepo, escalationRepo, slaRepo,
                departmentRepo, wardRepo, userRepo, codeGenerator, scoringService,
                clusterService, auditService, notificationService, fileStorageService,
                TestDataFactory.REPORTER_SALT,
                18.89, 19.27, 72.77, 72.98
        );
        complaint = TestDataFactory.sampleComplaint();
    }

    @Nested
    @DisplayName("submit()")
    class SubmitTests {

        @Test
        @DisplayName("Should submit complaint with phone and return tracking code")
        void submit_withPhone_success() {
            SubmitRequest request = new SubmitRequest(
                    "9876543210", "BRIBERY", "Bribe demand",
                    "Officer demanded money at counter", "Andheri", "Ward 45",
                    null, new BigDecimal("19.10"), new BigDecimal("72.85"), null);

            when(codeGenerator.generate()).thenReturn("PUK-2026-000001");
            when(complaintRepo.findByTrackingCode("PUK-2026-000001")).thenReturn(Optional.empty());
            when(departmentRepo.findByCode("REVENUE"))
                    .thenReturn(Optional.of(TestDataFactory.department("dept-revenue", "REVENUE", "Revenue")));
            when(scoringService.computeScore(any(), eq(false))).thenReturn(new BigDecimal("7.50"));
            when(scoringService.labelFor(any())).thenReturn(ComplaintPriority.HIGH);
            when(complaintRepo.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId("complaint-new");
                return c;
            });

            SubmitResponse response = complaintService.submit(request, "127.0.0.1");

            assertEquals("PUK-2026-000001", response.trackingCode());
            assertNull(response.reporterToken(), "Phone reporters should not get anonymous token");
            assertEquals(ComplaintPriority.HIGH, response.priority());
            assertEquals(72, response.estimatedSlaHours());

            verify(clusterService).assignCluster(any(Complaint.class));
            verify(notificationService).notifyCitizen(anyString(), anyString(), anyString());
            verify(auditService).recordChange(eq("COMPLAINT_CREATED"), eq("COMPLAINT"),
                    anyString(), isNull(), eq("CITIZEN"), anyString(), anyString(),
                    eq("127.0.0.1"), isNull(), anyMap());
        }

        @Test
        @DisplayName("Should issue anonymous reporter token when phone is not provided")
        void submit_anonymous_generatesReporterToken() {
            SubmitRequest request = new SubmitRequest(
                    null, "WATER", null,
                    "No water supply for 3 days", "Bandra", "Ward 30",
                    null, null, null, null);

            when(codeGenerator.generate()).thenReturn("PUK-2026-000002");
            when(complaintRepo.findByTrackingCode(any())).thenReturn(Optional.empty());
            when(scoringService.computeScore(any(), eq(false))).thenReturn(new BigDecimal("5.00"));
            when(scoringService.labelFor(any())).thenReturn(ComplaintPriority.MEDIUM);
            when(complaintRepo.save(any())).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId("complaint-anon");
                return c;
            });

            SubmitResponse response = complaintService.submit(request, "127.0.0.1");

            assertNotNull(response.reporterToken());
            assertFalse(response.reporterToken().isBlank());
        }

        @Test
        @DisplayName("Should reject complaint outside Mumbai geofence")
        void submit_outsideGeofence_throwsBadRequest() {
            SubmitRequest request = new SubmitRequest(
                    null, "ROAD", null,
                    "Pothole on main road", "Delhi", null,
                    null, new BigDecimal("28.6139"), new BigDecimal("77.2090"), null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.submit(request, "127.0.0.1"));

            assertEquals("OUTSIDE_SERVICE_AREA", ex.getCode());
            verify(complaintRepo, never()).save(any());
        }
    }

    @Nested
    @DisplayName("track()")
    class TrackTests {

        @Test
        @DisplayName("Should return tracking details for valid tracking code")
        void track_success() {
            when(complaintRepo.findByTrackingCode("PUK-2026-000001")).thenReturn(Optional.of(complaint));
            when(historyRepo.findByComplaintIdOrderByChangedAtAsc("complaint-1")).thenReturn(List.of());
            when(departmentRepo.findById("dept-revenue"))
                    .thenReturn(Optional.of(TestDataFactory.department("dept-revenue", "REVENUE", "Revenue")));
            when(userRepo.findById("officer-1")).thenReturn(Optional.of(TestDataFactory.officerUser()));
            when(userRepo.findByDepartmentId("dept-revenue")).thenReturn(List.of());
            when(evidenceRepo.findByComplaintIdOrderByUploadedAtAsc("complaint-1")).thenReturn(List.of());

            TrackResponse response = complaintService.track("PUK-2026-000001");

            assertEquals("PUK-2026-000001", response.trackingCode());
            assertEquals("BRIBERY", response.category());
            assertEquals(ComplaintStatus.SUBMITTED, response.status());
            assertEquals("Revenue", response.departmentName());
        }

        @Test
        @DisplayName("Should throw not found for invalid tracking code")
        void track_invalidCode_throwsNotFound() {
            when(complaintRepo.findByTrackingCode("INVALID")).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.track("INVALID"));

            assertEquals("COMPLAINT_NOT_FOUND", ex.getCode());
        }
    }

    @Nested
    @DisplayName("submitFeedback()")
    class FeedbackTests {

        @Test
        @DisplayName("Should close complaint when citizen confirms resolution")
        void submitFeedback_resolved_success() {
            complaint.setStatus(ComplaintStatus.RESOLVED_CLAIMED);
            when(complaintRepo.findByTrackingCode("PUK-2026-000001")).thenReturn(Optional.of(complaint));
            when(complaintRepo.save(any())).thenReturn(complaint);

            FeedbackRequest request = new FeedbackRequest(true, "Issue resolved", null);
            complaintService.submitFeedback("PUK-2026-000001", request,
                    complaint.getReporterHash(), "127.0.0.1");

            assertEquals(ComplaintStatus.CLOSED, complaint.getStatus());
            assertTrue(complaint.getCitizenFeedback());
            verify(historyRepo, times(2)).save(any());
            verify(auditService).record(eq("CITIZEN_VERIFIED"), eq("COMPLAINT"),
                    eq("complaint-1"), isNull(), eq("CITIZEN"), anyString(), anyString(), eq("127.0.0.1"));
        }

        @Test
        @DisplayName("Should reject feedback from non-reporter")
        void submitFeedback_notReporter_throwsForbidden() {
            complaint.setStatus(ComplaintStatus.RESOLVED_CLAIMED);
            when(complaintRepo.findByTrackingCode("PUK-2026-000001")).thenReturn(Optional.of(complaint));

            FeedbackRequest request = new FeedbackRequest(true, null, null);
            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.submitFeedback("PUK-2026-000001", request,
                            "wrong-hash", "127.0.0.1"));

            assertEquals("NOT_REPORTER", ex.getCode());
        }

        @Test
        @DisplayName("Should reject feedback when status is not RESOLVED_CLAIMED")
        void submitFeedback_wrongStatus_throwsBadRequest() {
            complaint.setStatus(ComplaintStatus.IN_PROGRESS);
            when(complaintRepo.findByTrackingCode("PUK-2026-000001")).thenReturn(Optional.of(complaint));

            FeedbackRequest request = new FeedbackRequest(true, null, null);
            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.submitFeedback("PUK-2026-000001", request,
                            complaint.getReporterHash(), "127.0.0.1"));

            assertEquals("FEEDBACK_NOT_ALLOWED", ex.getCode());
        }
    }

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should forbid officer from updating unassigned complaint")
        void updateStatus_officerNotAssigned_throwsForbidden() {
            complaint.setAssignedOfficerId("other-officer");
            when(complaintRepo.findById("complaint-1")).thenReturn(Optional.of(complaint));

            AppUserDetails me = TestDataFactory.officerDetails();
            StatusUpdateRequest request = new StatusUpdateRequest("IN_PROGRESS", null, null, "Working on it");

            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.updateStatus("complaint-1", request, me, "127.0.0.1"));

            assertEquals("NOT_ASSIGNED", ex.getCode());
        }

        @Test
        @DisplayName("Should require resolution proof when marking RESOLVED_CLAIMED")
        void updateStatus_resolvedWithoutProof_throwsBadRequest() {
            when(complaintRepo.findById("complaint-1")).thenReturn(Optional.of(complaint));
            when(evidenceRepo.findByComplaintIdOrderByUploadedAtAsc("complaint-1")).thenReturn(List.of());

            AppUserDetails me = TestDataFactory.officerDetails();
            StatusUpdateRequest request = new StatusUpdateRequest("RESOLVED_CLAIMED", null, null, "Done");

            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.updateStatus("complaint-1", request, me, "127.0.0.1"));

            assertEquals("RESOLUTION_PROOF_REQUIRED", ex.getCode());
        }
    }

    @Nested
    @DisplayName("getDetail()")
    class GetDetailTests {

        @Test
        @DisplayName("Should return complaint view for valid ID")
        void getDetail_success() {
            when(complaintRepo.findById("complaint-1")).thenReturn(Optional.of(complaint));
            when(departmentRepo.findById("dept-revenue"))
                    .thenReturn(Optional.of(TestDataFactory.department("dept-revenue", "REVENUE", "Revenue")));
            when(userRepo.findById("officer-1")).thenReturn(Optional.of(TestDataFactory.officerUser()));
            when(userRepo.findByDepartmentId("dept-revenue")).thenReturn(List.of());
            when(historyRepo.findByComplaintIdOrderByChangedAtAsc("complaint-1")).thenReturn(List.of());
            when(evidenceRepo.findByComplaintIdOrderByUploadedAtAsc("complaint-1")).thenReturn(List.of());
            when(escalationRepo.findTopByComplaintIdOrderByEscalatedAtDesc("complaint-1"))
                    .thenReturn(Optional.empty());

            ComplaintView view = complaintService.getDetail("complaint-1");

            assertEquals("complaint-1", view.id());
            assertEquals("PUK-2026-000001", view.trackingCode());
            assertEquals("BRIBERY", view.category());
        }

        @Test
        @DisplayName("Should throw not found for invalid complaint ID")
        void getDetail_notFound() {
            when(complaintRepo.findById("missing")).thenReturn(Optional.empty());

            ApiException ex = assertThrows(ApiException.class,
                    () -> complaintService.getDetail("missing"));

            assertEquals("COMPLAINT_NOT_FOUND", ex.getCode());
        }
    }
}
