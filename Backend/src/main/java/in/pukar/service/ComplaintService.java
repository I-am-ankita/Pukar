package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.common.HashUtil;
import in.pukar.dto.ComplaintDtos.*;
import in.pukar.entity.*;
import in.pukar.repository.*;
import in.pukar.security.AppUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ComplaintService {

    private static final Map<String, String> CATEGORY_TO_DEPT = Map.of(
            "BRIBERY", "REVENUE",
            "CORRUPTION", "REVENUE",
            "LAND_ENCROACHMENT", "REVENUE",
            "WATER", "WATER",
            "SANITATION", "WATER",
            "GARBAGE", "SWM",
            "ROAD", "PWD",
            "ELECTRICITY", "ELEC",
            "OTHER", "GRIEVANCE"
    );

    private final ComplaintRepository complaintRepo;
    private final ComplaintEvidenceRepository evidenceRepo;
    private final ComplaintStatusHistoryRepository historyRepo;
    private final EscalationEventRepository escalationRepo;
    private final SlaRuleRepository slaRepo;
    private final DepartmentRepository departmentRepo;
    private final WardRepository wardRepo;
    private final UserRepository userRepo;
    private final TrackingCodeGenerator codeGenerator;
    private final PriorityScoringService scoringService;
    private final ClusterService clusterService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final String reporterSalt;
    private final double minLat;
    private final double maxLat;
    private final double minLng;
    private final double maxLng;

    public ComplaintService(ComplaintRepository complaintRepo,
                            ComplaintEvidenceRepository evidenceRepo,
                            ComplaintStatusHistoryRepository historyRepo,
                            EscalationEventRepository escalationRepo,
                            SlaRuleRepository slaRepo,
                            DepartmentRepository departmentRepo,
                            WardRepository wardRepo,
                            UserRepository userRepo,
                            TrackingCodeGenerator codeGenerator,
                            PriorityScoringService scoringService,
                            ClusterService clusterService,
                            AuditService auditService,
                            NotificationService notificationService,
                            FileStorageService fileStorageService,
                            @Value("${pukar.security.reporter-salt}") String reporterSalt,
                            @Value("${pukar.geofence.min-lat:18.89}") double minLat,
                            @Value("${pukar.geofence.max-lat:19.27}") double maxLat,
                            @Value("${pukar.geofence.min-lng:72.77}") double minLng,
                            @Value("${pukar.geofence.max-lng:72.98}") double maxLng) {
        this.complaintRepo = complaintRepo;
        this.evidenceRepo = evidenceRepo;
        this.historyRepo = historyRepo;
        this.escalationRepo = escalationRepo;
        this.slaRepo = slaRepo;
        this.departmentRepo = departmentRepo;
        this.wardRepo = wardRepo;
        this.userRepo = userRepo;
        this.codeGenerator = codeGenerator;
        this.scoringService = scoringService;
        this.clusterService = clusterService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.fileStorageService = fileStorageService;
        this.reporterSalt = reporterSalt;
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
    }

    // ===================== PUBLIC / CITIZEN =====================

    @Transactional
    public SubmitResponse submit(SubmitRequest req, String ipAddress) {
        if (req.latitude() != null && req.longitude() != null) {
            double lat = req.latitude().doubleValue();
            double lng = req.longitude().doubleValue();
            if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) {
                throw ApiException.badRequest("OUTSIDE_SERVICE_AREA",
                        "PUKAR currently only accepts complaints within Mumbai city limits");
            }
        }

        String reporterToken = null;
        String reporterHash;
        if (req.reporterPhone() != null && !req.reporterPhone().isBlank()) {
            reporterHash = HashUtil.sha256(req.reporterPhone().trim() + reporterSalt);
        } else {
            reporterToken = UUID.randomUUID().toString();
            reporterHash = HashUtil.sha256(reporterToken + reporterSalt);
        }

        Complaint c = new Complaint();
        c.setTrackingCode(uniqueTrackingCode());
        c.setReporterHash(reporterHash);
        c.setCategory(req.category().toUpperCase());
        c.setSubCategory(req.subCategory());
        c.setDescription(req.description());
        c.setLocationText(req.locationText());
        c.setWard(req.ward());
        c.setLatitude(req.latitude());
        c.setLongitude(req.longitude());
        c.setIncidentDate(req.incidentDate());
        c.setStatus(ComplaintStatus.SUBMITTED);

        // route to department by category
        String deptCode = CATEGORY_TO_DEPT.getOrDefault(c.getCategory(), "GRIEVANCE");
        departmentRepo.findByCode(deptCode).ifPresent(d -> c.setDepartmentId(d.getId()));

        if (req.wardId() != null && !req.wardId().isBlank()) {
            wardRepo.findById(req.wardId()).ifPresent(w -> {
                c.setWardId(w.getId());
                if (c.getWard() == null || c.getWard().isBlank()) c.setWard(w.getName());
            });
        }

        // SLA deadline from rule (level 1)
        long slaHours = slaRepo.findFirstByCategoryAndActiveTrue(c.getCategory())
                .map(SlaRule::getLevel1Hours).orElse(72);
        c.setSlaDeadline(Instant.now().plus(slaHours, ChronoUnit.HOURS));

        // priority score (no evidence yet at submit time)
        BigDecimal score = scoringService.computeScore(c, false);
        c.setPriorityScore(score);
        c.setPriority(scoringService.labelFor(score));

        // auto-assign only when exactly one officer serves this (ward, department) pair
        if (c.getWardId() != null && c.getDepartmentId() != null) {
            List<User> officers = userRepo.findByWardIdAndDepartmentId(c.getWardId(), c.getDepartmentId()).stream()
                    .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.OFFICER))
                    .toList();
            if (officers.size() == 1) {
                c.setAssignedOfficerId(officers.get(0).getId());
                c.setStatus(ComplaintStatus.ASSIGNED);
            }
        }

        Complaint saved = complaintRepo.save(c);
        clusterService.assignCluster(saved);
        complaintRepo.save(saved);

        recordHistory(saved.getId(), null, saved.getStatus(), null, reporterHash,
                saved.getStatus() == ComplaintStatus.ASSIGNED
                        ? "Complaint submitted by citizen and auto-assigned" : "Complaint submitted by citizen");
        auditService.recordChange("COMPLAINT_CREATED", "COMPLAINT", saved.getId(), null, "CITIZEN",
                reporterHash, "{\"trackingCode\":\"" + saved.getTrackingCode() + "\",\"category\":\""
                        + saved.getCategory() + "\"}", ipAddress, null,
                Map.of("trackingCode", saved.getTrackingCode(), "category", saved.getCategory(),
                        "wardId", nzs(saved.getWardId()), "departmentId", nzs(saved.getDepartmentId())));
        notificationService.notifyCitizen(reporterHash, "Complaint registered",
                "Your complaint " + saved.getTrackingCode() + " has been registered.");
        if (saved.getAssignedOfficerId() != null) {
            notificationService.notifyUser(saved.getAssignedOfficerId(), "New complaint assigned",
                    "Complaint " + saved.getTrackingCode() + " has been assigned to you.");
        }

        return new SubmitResponse(saved.getTrackingCode(), reporterToken,
                "Your complaint has been submitted. Use this code to track progress.",
                slaHours, saved.getPriority());
    }

    @Transactional(readOnly = true)
    public TrackResponse track(String trackingCode) {
        Complaint c = complaintRepo.findByTrackingCode(trackingCode)
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND",
                        "No complaint found with the given tracking code"));
        String deptName = deptName(c.getDepartmentId());
        List<StatusEntry> history = historyRepo.findByComplaintIdOrderByChangedAtAsc(c.getId()).stream()
                .map(h -> new StatusEntry(h.getToStatus(), h.getChangedAt(), h.getReason()))
                .toList();
        String supervisorName = displayName(wardSupervisor(c.getWardId(), c.getDepartmentId()));
        String watchdogName = displayName(wardWatchdog(c.getWardId()));
        return new TrackResponse(
                c.getTrackingCode(), c.getCategory(), c.getSubCategory(), c.getStatus(), c.getPriority(),
                c.getLocationText(), c.getWard(), deptName, watchdogName,
                officerName(c.getAssignedOfficerId()), supervisorName,
                c.getCreatedAt(), c.getUpdatedAt(),
                c.getSlaDeadline(), c.getEscalationCount(),
                c.getStatus() == ComplaintStatus.RESOLVED_CLAIMED
                        || c.getStatus() == ComplaintStatus.CLOSED ? c.getResolutionNote() : null,
                history, evidenceFor(c.getId()));
    }

    @Transactional
    public EvidenceDto addCitizenEvidence(String trackingCode, MultipartFile file, String ipAddress) {
        Complaint c = complaintRepo.findByTrackingCode(trackingCode)
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND", "Tracking code not found"));
        FileStorageService.StoredFile stored = fileStorageService.store(file);

        ComplaintEvidence ev = new ComplaintEvidence();
        ev.setComplaintId(c.getId());
        ev.setUploaderHash(c.getReporterHash());
        ev.setFileName(stored.originalName());
        ev.setFileUrl(stored.url());
        ev.setFileType(stored.contentType());
        ev.setFileSizeBytes(stored.size());
        ev.setUploadType("CITIZEN_EVIDENCE");
        evidenceRepo.save(ev);

        // evidence strengthens the priority score
        BigDecimal score = scoringService.computeScore(c, true);
        c.setPriorityScore(score);
        c.setPriority(scoringService.labelFor(score));
        complaintRepo.save(c);

        auditService.record("EVIDENCE_UPLOAD", "COMPLAINT", c.getId(), null, "CITIZEN",
                c.getReporterHash(), "{\"file\":\"" + stored.originalName() + "\"}", ipAddress);
        return toEvidenceDto(ev);
    }

    @Transactional
    public void submitFeedback(String trackingCode, FeedbackRequest req,
                               String citizenReporterHash, String ipAddress) {
        Complaint c = complaintRepo.findByTrackingCode(trackingCode)
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND", "Tracking code not found"));

        // Resolve who is asking: either an OTP-verified citizen token (phone-based reporters)
        // or the reporter token issued at submission (anonymous, no-phone reporters).
        String effectiveHash = citizenReporterHash;
        if (effectiveHash == null && req.reporterToken() != null && !req.reporterToken().isBlank()) {
            effectiveHash = HashUtil.sha256(req.reporterToken().trim() + reporterSalt);
        }
        if (effectiveHash == null) {
            throw ApiException.forbidden("IDENTITY_REQUIRED",
                    "Verify your identity (OTP or reporter token) before confirming the resolution");
        }
        if (!effectiveHash.equals(c.getReporterHash())) {
            throw ApiException.forbidden("NOT_REPORTER",
                    "Only the citizen who filed this complaint can mark it resolved or not resolved");
        }

        if (c.getStatus() != ComplaintStatus.RESOLVED_CLAIMED) {
            throw ApiException.badRequest("FEEDBACK_NOT_ALLOWED",
                    "Feedback can only be given once a resolution is claimed");
        }
        c.setCitizenFeedback(req.isResolved());
        c.setCitizenFeedbackAt(Instant.now());

        if (req.isResolved()) {
            transition(c, ComplaintStatus.CITIZEN_VERIFIED, null, c.getReporterHash(),
                    "Citizen verified the resolution");
            transition(c, ComplaintStatus.CLOSED, null, c.getReporterHash(),
                    "Complaint closed after citizen verification");
            auditService.record("CITIZEN_VERIFIED", "COMPLAINT", c.getId(), null, "CITIZEN",
                    c.getReporterHash(), "{\"resolved\":true}", ipAddress);
        } else {
            transition(c, ComplaintStatus.CITIZEN_REJECTED, null, c.getReporterHash(),
                    req.remarks() != null ? req.remarks() : "Citizen rejected the resolution");
            // auto-reopen and escalate
            escalate(c, "Citizen rejected the claimed resolution", "CITIZEN_REJECT", null, "SYSTEM");
            auditService.record("CITIZEN_REJECTED", "COMPLAINT", c.getId(), null, "CITIZEN",
                    c.getReporterHash(), "{\"resolved\":false}", ipAddress);
        }
        complaintRepo.save(c);
    }

    // ===================== STAFF =====================

    @Transactional(readOnly = true)
    public Page<ComplaintView> listForOfficer(AppUserDetails me, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "priorityScore"));
        Page<Complaint> result = complaintRepo.findByAssignedOfficerIdAndDeletedFalse(me.getId(), pageable);
        return result.map(this::toView);
    }

    @Transactional(readOnly = true)
    public Page<ComplaintView> listForDepartment(String departmentId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "priorityScore"));
        return complaintRepo.findByDepartmentIdAndDeletedFalse(departmentId, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public Page<ComplaintView> listAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return complaintRepo.findAll(pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public ComplaintView getDetail(String id) {
        Complaint c = complaintRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND", "Complaint not found"));
        return toView(c);
    }

    @Transactional
    public void updateStatus(String id, StatusUpdateRequest req, AppUserDetails me, String ipAddress) {
        Complaint c = mustFind(id);
        ComplaintStatus target = parseStatus(req.status());
        String role = primaryRole(me);
        ComplaintStatus from = c.getStatus();

        // officers may only act on their own complaints
        if ("OFFICER".equals(role) && !me.getId().equals(c.getAssignedOfficerId())) {
            throw ApiException.forbidden("NOT_ASSIGNED", "This complaint is not assigned to you");
        }

        if (target == ComplaintStatus.RESOLVED_CLAIMED) {
            boolean hasProof = (req.resolutionEvidenceUrl() != null && !req.resolutionEvidenceUrl().isBlank())
                    || evidenceRepo.findByComplaintIdOrderByUploadedAtAsc(c.getId()).stream()
                        .anyMatch(e -> "RESOLUTION_PROOF".equals(e.getUploadType()));
            if (req.resolutionNote() == null || req.resolutionNote().isBlank() || !hasProof) {
                throw ApiException.badRequest("RESOLUTION_PROOF_REQUIRED",
                        "A resolution note and proof evidence are required before marking resolved");
            }
            c.setResolutionNote(req.resolutionNote());
            c.setResolutionEvidenceUrl(req.resolutionEvidenceUrl());
        }

        transition(c, target, me.getId(), null,
                req.reason() != null ? req.reason() : "Status updated by " + role);
        complaintRepo.save(c);
        auditService.recordChange("STATUS_CHANGED", "COMPLAINT", c.getId(), me.getId(), role,
                null, "{\"to\":\"" + target + "\"}", ipAddress,
                Map.of("status", from.name()), Map.of("status", target.name()));
    }

    /** Officers eligible for manual assignment, scoped to the complaint's (ward, department) pair. */
    @Transactional(readOnly = true)
    public List<in.pukar.dto.AdminDtos.UserDto> assignableOfficers(String complaintId) {
        Complaint c = mustFind(complaintId);
        List<User> candidates;
        if (c.getWardId() != null && c.getDepartmentId() != null) {
            candidates = userRepo.findByWardIdAndDepartmentId(c.getWardId(), c.getDepartmentId());
        } else if (c.getDepartmentId() != null) {
            candidates = userRepo.findByDepartmentId(c.getDepartmentId());
        } else {
            candidates = List.of();
        }
        return candidates.stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.OFFICER))
                .map(u -> new in.pukar.dto.AdminDtos.UserDto(u.getId(), u.getUsername(), u.getEmail(),
                        u.getFullName(), "OFFICER",
                        u.getDepartment() != null ? u.getDepartment().getId() : null,
                        u.getDepartment() != null ? u.getDepartment().getName() : null,
                        u.getWard() != null ? u.getWard().getId() : null,
                        u.getWard() != null ? u.getWard().getName() : null,
                        u.isActive()))
                .toList();
    }

    @Transactional
    public void assign(String id, AssignRequest req, AppUserDetails me, String ipAddress) {
        Complaint c = mustFind(id);
        String previousOfficerId = c.getAssignedOfficerId();
        User officer = userRepo.findById(req.officerId())
                .orElseThrow(() -> ApiException.notFound("OFFICER_NOT_FOUND", "Officer not found"));
        c.setAssignedOfficerId(officer.getId());
        if (officer.getDepartment() != null) c.setDepartmentId(officer.getDepartment().getId());
        if (c.getStatus() == ComplaintStatus.SUBMITTED || c.getStatus() == ComplaintStatus.ESCALATED) {
            transition(c, ComplaintStatus.ASSIGNED, me.getId(), null,
                    "Assigned to " + (officer.getFullName() != null ? officer.getFullName() : officer.getUsername()));
        }
        complaintRepo.save(c);
        auditService.recordChange("ASSIGNED", "COMPLAINT", c.getId(), me.getId(), primaryRole(me),
                null, "{\"officerId\":\"" + officer.getId() + "\"}", ipAddress,
                Map.of("assignedOfficerId", nzs(previousOfficerId)), Map.of("assignedOfficerId", officer.getId()));
        notificationService.notifyUser(officer.getId(), "New complaint assigned",
                "Complaint " + c.getTrackingCode() + " has been assigned to you.");
    }

    @Transactional
    public void approve(String id, AppUserDetails me, String ipAddress) {
        Complaint c = mustFind(id);
        if (c.getStatus() != ComplaintStatus.RESOLVED_CLAIMED) {
            throw ApiException.badRequest("NOTHING_TO_APPROVE",
                    "Only a claimed resolution can be approved");
        }
        c.setSupervisorApprovalId(me.getId());
        c.setSupervisorApprovedAt(Instant.now());
        complaintRepo.save(c);
        auditService.recordChange("RESOLUTION_APPROVED", "COMPLAINT", c.getId(), me.getId(), primaryRole(me),
                null, "{}", ipAddress, Map.of("supervisorApprovalId", ""), Map.of("supervisorApprovalId", me.getId()));
        notificationService.notifyCitizen(c.getReporterHash(), "Please verify resolution",
                "Complaint " + c.getTrackingCode() + " was marked resolved. Please confirm within 5 days.");
    }

    @Transactional
    public void escalateManual(String id, EscalateRequest req, AppUserDetails me, String ipAddress) {
        Complaint c = mustFind(id);
        int before = c.getEscalationCount();
        escalate(c, req.reason(),
                req.triggeredBy() != null ? req.triggeredBy() : "MANUAL",
                me.getId(), primaryRole(me));
        complaintRepo.save(c);
        auditService.recordChange("ESCALATED", "COMPLAINT", c.getId(), me.getId(), primaryRole(me),
                null, "{\"reason\":\"" + safe(req.reason()) + "\"}", ipAddress,
                Map.of("escalationCount", before), Map.of("escalationCount", c.getEscalationCount()));
    }

    @Transactional
    public ComplaintView addResolutionEvidence(String id, MultipartFile file, AppUserDetails me, String ipAddress) {
        Complaint c = mustFind(id);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        ComplaintEvidence ev = new ComplaintEvidence();
        ev.setComplaintId(c.getId());
        ev.setUploaderId(me.getId());
        ev.setFileName(stored.originalName());
        ev.setFileUrl(stored.url());
        ev.setFileType(stored.contentType());
        ev.setFileSizeBytes(stored.size());
        ev.setUploadType("RESOLUTION_PROOF");
        evidenceRepo.save(ev);
        auditService.record("EVIDENCE_UPLOAD", "COMPLAINT", c.getId(), me.getId(), primaryRole(me),
                null, "{\"file\":\"" + stored.originalName() + "\",\"type\":\"RESOLUTION_PROOF\"}", ipAddress);
        return toView(c);
    }

    // ===================== internal helpers (also used by scheduler) =====================

    @Transactional
    public void escalate(Complaint c, String reason, String triggeredBy, String actorId, String actorRole) {
        String fromRole;
        String toRole;
        int count = c.getEscalationCount();
        if (count == 0) { fromRole = "OFFICER"; toRole = "SUPERVISOR"; }
        else if (count == 1) { fromRole = "SUPERVISOR"; toRole = "WATCHDOG"; }
        else { fromRole = "WATCHDOG"; toRole = "WATCHDOG"; }

        c.setEscalationCount(count + 1);

        // extend SLA deadline to the next level window
        long nextHours = slaRepo.findFirstByCategoryAndActiveTrue(c.getCategory())
                .map(r -> count == 0 ? r.getLevel2Hours() : r.getLevel3Hours())
                .orElse(72);
        c.setSlaDeadline(Instant.now().plus(nextHours, ChronoUnit.HOURS));

        ComplaintStatus target = (count >= 1) ? ComplaintStatus.WATCHDOG_REVIEW : ComplaintStatus.ESCALATED;
        transition(c, target, actorId, null, reason);

        // resolve the actual person this escalates to: ward-scoped supervisor/watchdog,
        // falling back department-wide/citywide, then to every admin if nobody is staffed at all
        User escalationTarget = (count == 0)
                ? wardSupervisor(c.getWardId(), c.getDepartmentId())
                : wardWatchdog(c.getWardId());

        EscalationEvent e = new EscalationEvent();
        e.setComplaintId(c.getId());
        e.setFromRole(fromRole);
        e.setToRole(toRole);
        e.setToUserId(escalationTarget != null ? escalationTarget.getId() : null);
        e.setEscalationReason(reason);
        e.setTriggeredBy(triggeredBy);
        escalationRepo.save(e);

        if (escalationTarget != null) {
            notificationService.notifyUser(escalationTarget.getId(), "Complaint escalated to you",
                    "Complaint " + c.getTrackingCode() + " was escalated to you as " + toRole + ".");
        } else {
            notifyAllAdmins("Unstaffed escalation: " + toRole + " needed",
                    "Complaint " + c.getTrackingCode() + " escalated to " + toRole
                            + " but no staff is assigned for its ward/department. Please assign one.");
        }

        // priority recalculated on escalation
        BigDecimal score = scoringService.computeScore(c,
                evidenceRepo.findByComplaintIdOrderByUploadedAtAsc(c.getId()).stream()
                        .anyMatch(ev -> "CITIZEN_EVIDENCE".equals(ev.getUploadType())));
        c.setPriorityScore(score);
        c.setPriority(scoringService.labelFor(score));

        notificationService.notifyCitizen(c.getReporterHash(), "Complaint escalated",
                "Complaint " + c.getTrackingCode() + " was escalated to " + toRole + ".");
    }

    private void transition(Complaint c, ComplaintStatus to, String actorId, String actorHash, String reason) {
        ComplaintStatus from = c.getStatus();
        c.setStatus(to);
        recordHistory(c.getId(), from, to, actorId, actorHash, reason);
    }

    private void recordHistory(String complaintId, ComplaintStatus from, ComplaintStatus to,
                               String actorId, String actorHash, String reason) {
        ComplaintStatusHistory h = new ComplaintStatusHistory();
        h.setComplaintId(complaintId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setChangedById(actorId);
        h.setChangedByHash(actorHash);
        h.setReason(reason);
        historyRepo.save(h);
    }

    private Complaint mustFind(String id) {
        return complaintRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("COMPLAINT_NOT_FOUND", "Complaint not found"));
    }

    private String uniqueTrackingCode() {
        String code;
        do { code = codeGenerator.generate(); }
        while (complaintRepo.findByTrackingCode(code).isPresent());
        return code;
    }

    private ComplaintStatus parseStatus(String s) {
        try { return ComplaintStatus.valueOf(s.toUpperCase()); }
        catch (Exception e) { throw ApiException.badRequest("INVALID_STATUS", "Unknown status: " + s); }
    }

    private String primaryRole(AppUserDetails me) {
        return me.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "")).orElse("UNKNOWN");
    }

    private String deptName(String deptId) {
        if (deptId == null) return null;
        return departmentRepo.findById(deptId).map(Department::getName).orElse(null);
    }

    /** Preferred: the supervisor for this exact (ward, department) pair. Falls back department-wide if unstaffed. */
    private User wardSupervisor(String wardId, String deptId) {
        if (wardId != null && deptId != null) {
            User u = userRepo.findByWardIdAndDepartmentId(wardId, deptId).stream()
                    .filter(x -> x.getRoles().stream().anyMatch(r -> r.getName() == RoleName.SUPERVISOR))
                    .findFirst().orElse(null);
            if (u != null) return u;
        }
        return deptSupervisor(deptId);
    }

    /** Fallback: first supervisor found anywhere in the department (any ward). */
    private User deptSupervisor(String deptId) {
        if (deptId == null) return null;
        return userRepo.findByDepartmentId(deptId).stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.SUPERVISOR))
                .findFirst().orElse(null);
    }

    /** Preferred: the watchdog assigned to this ward. Falls back to any citywide watchdog if unstaffed. */
    private User wardWatchdog(String wardId) {
        if (wardId != null) {
            User u = userRepo.findByWardId(wardId).stream()
                    .filter(x -> x.getRoles().stream().anyMatch(r -> r.getName() == RoleName.WATCHDOG))
                    .findFirst().orElse(null);
            if (u != null) return u;
        }
        return anyWatchdog();
    }

    /** Fallback: first watchdog found anywhere (citywide). */
    private User anyWatchdog() {
        return userRepo.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.WATCHDOG))
                .findFirst().orElse(null);
    }

    /** Last-resort fallback: notify every admin so an escalation with nobody staffed is never silently dropped. */
    private void notifyAllAdmins(String subject, String body) {
        userRepo.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN))
                .forEach(admin -> notificationService.notifyUser(admin.getId(), subject, body));
    }

    private String displayName(in.pukar.entity.User u) {
        if (u == null) return null;
        return u.getFullName() != null ? u.getFullName() : u.getUsername();
    }

    private String officerName(String officerId) {
        if (officerId == null) return null;
        return userRepo.findById(officerId).map(this::displayName).orElse(null);
    }

    private List<EvidenceDto> evidenceFor(String complaintId) {
        return evidenceRepo.findByComplaintIdOrderByUploadedAtAsc(complaintId).stream()
                .map(this::toEvidenceDto).toList();
    }

    private EvidenceDto toEvidenceDto(ComplaintEvidence e) {
        return new EvidenceDto(e.getId(), e.getFileName(), e.getFileUrl(),
                e.getFileType(), e.getUploadType(), e.getUploadedAt());
    }

    private ComplaintView toView(Complaint c) {
        String deptName = deptName(c.getDepartmentId());
        String officerName = officerName(c.getAssignedOfficerId());
        User supervisor = wardSupervisor(c.getWardId(), c.getDepartmentId());
        User watchdog = wardWatchdog(c.getWardId());
        String wardName = c.getWardId() != null
                ? wardRepo.findById(c.getWardId()).map(Ward::getName).orElse(null) : null;
        List<StatusEntry> history = historyRepo.findByComplaintIdOrderByChangedAtAsc(c.getId()).stream()
                .map(h -> new StatusEntry(h.getToStatus(), h.getChangedAt(), h.getReason()))
                .toList();
        boolean breached = c.getSlaDeadline() != null && c.getSlaDeadline().isBefore(Instant.now())
                && isActive(c.getStatus());

        // who this complaint was actually escalated to, most recently (a specific person, not just a role)
        String escalatedToName = null;
        String escalatedToRole = null;
        var lastEscalation = escalationRepo.findTopByComplaintIdOrderByEscalatedAtDesc(c.getId()).orElse(null);
        if (lastEscalation != null) {
            escalatedToRole = lastEscalation.getToRole();
            if (lastEscalation.getToUserId() != null) {
                escalatedToName = userRepo.findById(lastEscalation.getToUserId()).map(this::displayName).orElse(null);
            }
        }

        return new ComplaintView(
                c.getId(), c.getTrackingCode(), c.getCategory(), c.getSubCategory(), c.getDescription(),
                c.getStatus().name(), c.getPriority().name(), c.getPriorityScore(),
                c.getLocationText(), c.getWard(), c.getWardId(), wardName, c.getLatitude(), c.getLongitude(),
                c.getDepartmentId(), deptName, c.getAssignedOfficerId(), officerName,
                supervisor != null ? supervisor.getId() : null, displayName(supervisor),
                watchdog != null ? watchdog.getId() : null, displayName(watchdog),
                escalatedToName, escalatedToRole,
                c.getClusterId(),
                c.getEscalationCount(), c.getSlaDeadline(), breached, c.getCitizenFeedback(),
                c.getResolutionNote(), c.getCreatedAt(), c.getUpdatedAt(), history, evidenceFor(c.getId()));
    }

    private String nzs(String s) { return s == null ? "" : s; }

    private boolean isActive(ComplaintStatus s) {
        return s == ComplaintStatus.SUBMITTED || s == ComplaintStatus.ASSIGNED
                || s == ComplaintStatus.IN_PROGRESS || s == ComplaintStatus.ESCALATED
                || s == ComplaintStatus.WATCHDOG_REVIEW;
    }

    private String safe(String s) { return s == null ? "" : s.replace("\"", "'"); }
}
