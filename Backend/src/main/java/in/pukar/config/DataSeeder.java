package in.pukar.config;

import in.pukar.dto.ComplaintDtos.SubmitRequest;
import in.pukar.entity.*;
import in.pukar.repository.*;
import in.pukar.service.ComplaintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Seeds reference data and a demo dataset on first run.
 * Controlled by pukar.seed.enabled. Idempotent: skips if roles already exist.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final List<String> STAFFED_WARDS = List.of("A", "K/W", "H/W", "G/N", "L");
    private static final List<String> STAFFED_DEPTS = List.of("REVENUE", "WATER", "SWM");
    private static final Set<String> CORE_DEPTS = Set.of("REVENUE", "WATER", "SWM", "PWD", "ELEC", "GRIEVANCE");

    private final boolean enabled;
    private final RoleRepository roleRepo;
    private final DepartmentRepository deptRepo;
    private final WardRepository wardRepo;
    private final UserRepository userRepo;
    private final SlaRuleRepository slaRepo;
    private final ComplaintRepository complaintRepo;
    private final ComplaintService complaintService;
    private final PasswordEncoder encoder;

    public DataSeeder(@Value("${pukar.seed.enabled:true}") boolean enabled,
                      RoleRepository roleRepo, DepartmentRepository deptRepo, WardRepository wardRepo,
                      UserRepository userRepo, SlaRuleRepository slaRepo,
                      ComplaintRepository complaintRepo, ComplaintService complaintService,
                      PasswordEncoder encoder) {
        this.enabled = enabled;
        this.roleRepo = roleRepo;
        this.deptRepo = deptRepo;
        this.wardRepo = wardRepo;
        this.userRepo = userRepo;
        this.slaRepo = slaRepo;
        this.complaintRepo = complaintRepo;
        this.complaintService = complaintService;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        if (roleRepo.count() > 0) return; // already seeded

        Map<RoleName, Role> roles = seedRoles();
        Map<String, Department> depts = seedDepartments();
        Map<String, Ward> wards = seedWards(depts);
        seedUsers(roles, depts, wards);
        seedSlaRules();
        seedComplaints(wards);
    }

    private Map<RoleName, Role> seedRoles() {
        Map<RoleName, Role> map = new EnumMap<>(RoleName.class);
        for (RoleName rn : RoleName.values()) {
            Role r = new Role();
            r.setName(rn);
            map.put(rn, roleRepo.save(r));
        }
        return map;
    }

    private Map<String, Department> seedDepartments() {
        String district = "Mumbai";
        String state = "Maharashtra";
        Object[][] data = {
                {"REVENUE", "Revenue & Anti-Corruption Cell"},
                {"WATER", "Water Supply & Sewerage Board"},
                {"SWM", "Solid Waste Management"},
                {"PWD", "Public Works (Roads)"},
                {"ELEC", "Electricity Board"},
                {"GRIEVANCE", "General Grievance Cell"},
                {"TRAFFIC", "Traffic Police Liaison Cell"}, // seeded unlinked to any ward — demo target for admin's "add department to ward" feature
        };
        Map<String, Department> map = new HashMap<>();
        for (Object[] d : data) {
            Department dept = new Department();
            dept.setCode((String) d[0]);
            dept.setName((String) d[1]);
            dept.setDistrict(district);
            dept.setState(state);
            map.put((String) d[0], deptRepo.save(dept));
        }
        return map;
    }

    private record WardSeed(String code, String name, String zone) {}

    private Map<String, Ward> seedWards(Map<String, Department> depts) {
        List<WardSeed> data = List.of(
                new WardSeed("A", "Colaba, Fort, Churchgate", "Island City"),
                new WardSeed("B", "Masjid Bunder, Dongri", "Island City"),
                new WardSeed("C", "Marine Lines, Kalbadevi", "Island City"),
                new WardSeed("D", "Grant Road, Malabar Hill", "Island City"),
                new WardSeed("E", "Byculla, Mazgaon", "Island City"),
                new WardSeed("F/N", "Matunga, Sion", "Island City"),
                new WardSeed("F/S", "Parel, Lower Parel", "Island City"),
                new WardSeed("G/N", "Dadar, Dharavi", "Island City"),
                new WardSeed("G/S", "Worli, Prabhadevi", "Island City"),
                new WardSeed("H/E", "Bandra East, Khar East", "Western Suburbs"),
                new WardSeed("H/W", "Bandra West, Khar West", "Western Suburbs"),
                new WardSeed("K/E", "Andheri East, Chakala", "Western Suburbs"),
                new WardSeed("K/W", "Andheri West, Juhu", "Western Suburbs"),
                new WardSeed("L", "Kurla", "Eastern Suburbs"),
                new WardSeed("M/E", "Chembur East, Govandi", "Eastern Suburbs"),
                new WardSeed("M/W", "Chembur West", "Eastern Suburbs"),
                new WardSeed("N", "Ghatkopar", "Eastern Suburbs"),
                new WardSeed("P/N", "Malad", "Western Suburbs"),
                new WardSeed("P/S", "Goregaon", "Western Suburbs"),
                new WardSeed("R/C", "Borivali", "Western Suburbs"),
                new WardSeed("R/N", "Dahisar", "Western Suburbs"),
                new WardSeed("R/S", "Kandivali", "Western Suburbs"),
                new WardSeed("S", "Bhandup, Powai", "Eastern Suburbs"),
                new WardSeed("T", "Mulund", "Eastern Suburbs")
        );
        Map<String, Ward> map = new HashMap<>();
        for (WardSeed w : data) {
            Ward ward = new Ward();
            ward.setCode(w.code());
            ward.setName(w.name());
            ward.setZone(w.zone());
            for (String code : CORE_DEPTS) {
                Department d = depts.get(code);
                if (d != null) ward.getDepartments().add(d);
            }
            map.put(w.code(), wardRepo.save(ward));
        }
        return map;
    }

    private void seedUsers(Map<RoleName, Role> roles, Map<String, Department> depts, Map<String, Ward> wards) {
        createUser("admin", "admin@pukar.in", "System Administrator", "demo1234",
                Set.of(roles.get(RoleName.ADMIN)), null, null);

        // Ward A keeps the original well-known demo usernames for backward compatibility.
        Ward wardA = wards.get("A");
        createUser("watchdog01", "watchdog@pukar.in", "A-Ward Watchdog", "demo1234",
                Set.of(roles.get(RoleName.WATCHDOG)), null, wardA);
        createUser("supervisor01", "supervisor@pukar.in", "A-Ward Revenue Supervisor", "demo1234",
                Set.of(roles.get(RoleName.SUPERVISOR)), depts.get("REVENUE"), wardA);
        createUser("officer01", "officer@pukar.in", "A-Ward Revenue Officer", "demo1234",
                Set.of(roles.get(RoleName.OFFICER)), depts.get("REVENUE"), wardA);
        createUser("supervisor_a_water", "supervisor.a.water@pukar.in", "A-Ward Water Supervisor", "demo1234",
                Set.of(roles.get(RoleName.SUPERVISOR)), depts.get("WATER"), wardA);
        createUser("officer02", "officer2@pukar.in", "A-Ward Water Officer", "demo1234",
                Set.of(roles.get(RoleName.OFFICER)), depts.get("WATER"), wardA);
        createUser("supervisor_a_swm", "supervisor.a.swm@pukar.in", "A-Ward SWM Supervisor", "demo1234",
                Set.of(roles.get(RoleName.SUPERVISOR)), depts.get("SWM"), wardA);
        createUser("officer03", "officer3@pukar.in", "A-Ward SWM Officer", "demo1234",
                Set.of(roles.get(RoleName.OFFICER)), depts.get("SWM"), wardA);

        // Remaining staffed wards get descriptively-named staff.
        for (String wardCode : STAFFED_WARDS) {
            if (wardCode.equals("A")) continue;
            Ward ward = wards.get(wardCode);
            String slug = wardCode.toLowerCase().replace("/", "");

            createUser("watchdog_" + slug, "watchdog." + slug + "@pukar.in",
                    wardCode + "-Ward Watchdog", "demo1234",
                    Set.of(roles.get(RoleName.WATCHDOG)), null, ward);

            for (String deptCode : STAFFED_DEPTS) {
                Department dept = depts.get(deptCode);
                String deptSlug = deptCode.toLowerCase();
                createUser("supervisor_" + slug + "_" + deptSlug,
                        "supervisor." + slug + "." + deptSlug + "@pukar.in",
                        wardCode + "-Ward " + dept.getName() + " Supervisor", "demo1234",
                        Set.of(roles.get(RoleName.SUPERVISOR)), dept, ward);
                createUser("officer_" + slug + "_" + deptSlug,
                        "officer." + slug + "." + deptSlug + "@pukar.in",
                        wardCode + "-Ward " + dept.getName() + " Officer", "demo1234",
                        Set.of(roles.get(RoleName.OFFICER)), dept, ward);
            }
        }
    }

    private void createUser(String username, String email, String fullName, String rawPassword,
                            Set<Role> roles, Department dept, Ward ward) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setRoles(new HashSet<>(roles));
        u.setDepartment(dept);
        u.setWard(ward);
        u.setActive(true);
        userRepo.save(u);
    }

    private void seedSlaRules() {
        // category, level1 (officer->supervisor), level2 (->watchdog), level3 (->critical) in hours
        Object[][] rules = {
                {"BRIBERY", 24, 72, 120},
                {"CORRUPTION", 24, 72, 120},
                {"LAND_ENCROACHMENT", 120, 240, 480},
                {"WATER", 72, 168, 336},
                {"SANITATION", 72, 168, 336},
                {"GARBAGE", 48, 120, 240},
                {"ROAD", 168, 336, 504},
                {"ELECTRICITY", 48, 120, 240},
                {"OTHER", 72, 168, 336},
        };
        for (Object[] r : rules) {
            SlaRule rule = new SlaRule();
            rule.setCategory((String) r[0]);
            rule.setLevel1Hours((int) r[1]);
            rule.setLevel2Hours((int) r[2]);
            rule.setLevel3Hours((int) r[3]);
            rule.setActive(true);
            rule.setCreatedBy("SEED");
            slaRepo.save(rule);
        }
    }

    private record Sample(String category, String sub, String desc, String wardCode,
                          double lat, double lng, ComplaintStatus targetStatus) {}

    private void seedComplaints(Map<String, Ward> wards) {
        List<Sample> samples = List.of(
                new Sample("BRIBERY", "Property registration",
                        "Sub-registrar demanded bribe to clear property transfer.", "A",
                        18.9067, 72.8147, ComplaintStatus.ESCALATED),
                new Sample("BRIBERY", "Trade license",
                        "Bribe demanded for renewing shop trade license.", "A",
                        18.9220, 72.8330, ComplaintStatus.IN_PROGRESS),
                new Sample("CORRUPTION", "Fund misuse",
                        "Ward development funds diverted, no work done.", "A",
                        18.9350, 72.8354, ComplaintStatus.SUBMITTED),
                new Sample("WATER", "No supply",
                        "No water supply for 6 days, tanker mafia demanding money.", "A",
                        18.9100, 72.8180, ComplaintStatus.WATCHDOG_REVIEW),
                new Sample("GARBAGE", "Not collected",
                        "Garbage not collected for two weeks, contractor absent.", "A",
                        18.9280, 72.8300, ComplaintStatus.ASSIGNED),
                new Sample("WATER", "Leakage",
                        "Major pipeline leakage flooding the street near Juhu.", "K/W",
                        19.1075, 72.8263, ComplaintStatus.CLOSED),
                new Sample("BRIBERY", "Building permission",
                        "Officer demanded cash for construction NOC.", "K/W",
                        19.1197, 72.8464, ComplaintStatus.IN_PROGRESS),
                new Sample("GARBAGE", "Illegal dumping",
                        "Construction debris dumped in public park.", "K/W",
                        19.1160, 72.8400, ComplaintStatus.SUBMITTED),
                new Sample("SANITATION", "Sewage overflow",
                        "Open sewage flowing on main road for weeks.", "H/W",
                        19.0596, 72.8295, ComplaintStatus.ESCALATED),
                new Sample("CORRUPTION", "Fund misuse",
                        "Local body funds diverted for a fake beautification project.", "H/W",
                        19.0550, 72.8330, ComplaintStatus.RESOLVED_CLAIMED),
                new Sample("WATER", "No supply",
                        "Erratic water supply for over a week.", "G/N",
                        19.0178, 72.8478, ComplaintStatus.IN_PROGRESS),
                new Sample("LAND_ENCROACHMENT", "Slum encroachment",
                        "Illegal structure built on drainage reserve land with official nexus.", "G/N",
                        19.0403, 72.8526, ComplaintStatus.WATCHDOG_REVIEW),
                new Sample("GARBAGE", "Not collected",
                        "Garbage piling up near market for days.", "L",
                        19.0728, 72.8826, ComplaintStatus.SUBMITTED),
                new Sample("BRIBERY", "Property registration",
                        "Agent asked for cash to speed up document verification.", "L",
                        19.0700, 72.8790, ComplaintStatus.ASSIGNED),
                // unstaffed wards — demonstrate fallback routing when no ward officer/supervisor exists yet
                new Sample("ROAD", "Pothole",
                        "Large potholes causing accidents near Marine Drive.", "C",
                        18.9432, 72.8235, ComplaintStatus.SUBMITTED),
                new Sample("ELECTRICITY", "Streetlight",
                        "Streetlights non-functional for a month, safety risk.", "N",
                        19.0864, 72.9081, ComplaintStatus.SUBMITTED)
        );

        for (Sample s : samples) {
            Ward ward = wards.get(s.wardCode());
            SubmitRequest req = new SubmitRequest(
                    null, s.category(), s.sub(), s.desc(),
                    ward.getName() + ", Mumbai", ward.getName(), ward.getId(),
                    BigDecimal.valueOf(s.lat()), BigDecimal.valueOf(s.lng()),
                    LocalDate.now().minusDays(new Random().nextInt(20) + 1));
            var resp = complaintService.submit(req, "127.0.0.1");
            advance(resp.trackingCode(), s.targetStatus());
        }
    }

    /** Directly nudges a seeded complaint into a target lifecycle state for demo dashboards. */
    private void advance(String trackingCode, ComplaintStatus target) {
        if (target == ComplaintStatus.SUBMITTED) return;
        Complaint c = complaintRepo.findByTrackingCode(trackingCode).orElse(null);
        if (c == null) return;

        Instant now = Instant.now();
        c.setStatus(target);
        // backdate creation so dashboards show realistic spread
        c.setCreatedAt(now.minus(new Random().nextInt(15) + 1, ChronoUnit.DAYS));

        switch (target) {
            case RESOLVED_CLAIMED -> {
                c.setResolutionNote("Issue addressed by field team; awaiting citizen verification.");
                c.setResolutionEvidenceUrl("/files/sample-resolution.jpg");
            }
            case CLOSED -> {
                c.setResolutionNote("Resolved and verified by citizen.");
                c.setCitizenFeedback(Boolean.TRUE);
                c.setCitizenFeedbackAt(now.minus(1, ChronoUnit.DAYS));
                c.setPublicVisible(true);
            }
            case ESCALATED, WATCHDOG_REVIEW -> {
                c.setEscalationCount(target == ComplaintStatus.WATCHDOG_REVIEW ? 2 : 1);
                c.setSlaDeadline(now.minus(2, ChronoUnit.DAYS)); // breached
            }
            default -> { /* ASSIGNED / IN_PROGRESS: leave timing as-is */ }
        }
        complaintRepo.save(c);
    }
}
