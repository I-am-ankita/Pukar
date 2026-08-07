package in.pukar.support;

import in.pukar.entity.*;
import in.pukar.security.AppUserDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * Shared test fixtures for PUKAR backend unit tests.
 * Keeps entity setup consistent across service and controller tests.
 */
public final class TestDataFactory {

    public static final String REPORTER_SALT = "test-reporter-salt";
    public static final String JWT_SECRET =
            "cGtyU3VwZXJTZWNyZXRLZXlGb3JQdWthckNpdmljUGxhdGZvcm0yMDI1MTIzNDU2Nzg5MA==";

    private TestDataFactory() {}

    public static Role role(RoleName name) {
        Role role = new Role();
        role.setId("role-" + name.name());
        role.setName(name);
        return role;
    }

    public static Department department(String id, String code, String name) {
        Department dept = new Department();
        dept.setId(id);
        dept.setCode(code);
        dept.setName(name);
        return dept;
    }

    public static User citizenUser() {
        User user = new User();
        user.setId("user-citizen-1");
        user.setUsername("citizen1");
        user.setEmail("citizen1@pukar.in");
        user.setPasswordHash("hashed");
        user.setFullName("Test Citizen");
        user.setRoles(Set.of(role(RoleName.CITIZEN)));
        user.setActive(true);
        return user;
    }

    public static User officerUser() {
        User user = new User();
        user.setId("officer-1");
        user.setUsername("officer1");
        user.setEmail("officer1@pukar.in");
        user.setPasswordHash("hashed");
        user.setFullName("Test Officer");
        user.setDepartment(department("dept-revenue", "REVENUE", "Revenue Department"));
        user.setRoles(Set.of(role(RoleName.OFFICER)));
        user.setActive(true);
        return user;
    }

    public static AppUserDetails officerDetails() {
        return new AppUserDetails(officerUser());
    }

    public static Complaint sampleComplaint() {
        Complaint c = new Complaint();
        c.setId("complaint-1");
        c.setTrackingCode("PUK-2026-000001");
        c.setReporterHash("abc123reporterhash");
        c.setCategory("BRIBERY");
        c.setSubCategory("Demand for bribe");
        c.setDescription("Officer demanded bribe at ward office");
        c.setLocationText("Andheri East");
        c.setWard("Ward 45");
        c.setLatitude(new BigDecimal("19.1136"));
        c.setLongitude(new BigDecimal("72.8697"));
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setPriority(ComplaintPriority.HIGH);
        c.setPriorityScore(new BigDecimal("7.50"));
        c.setDepartmentId("dept-revenue");
        c.setAssignedOfficerId("officer-1");
        c.setEscalationCount(0);
        c.setSlaDeadline(Instant.now().plusSeconds(72 * 3600));
        c.setCreatedAt(Instant.now());
        c.setUpdatedAt(Instant.now());
        return c;
    }

    public static OtpChallenge activeOtpChallenge(String phoneHash, String code, String salt) {
        OtpChallenge ch = new OtpChallenge();
        ch.setId(1L);
        ch.setPhoneHash(phoneHash);
        ch.setCodeHash(in.pukar.common.HashUtil.sha256(code + salt));
        ch.setPurpose("CITIZEN_FEEDBACK");
        ch.setExpiresAt(Instant.now().plusSeconds(300));
        ch.setConsumed(false);
        ch.setAttempts(0);
        return ch;
    }
}
