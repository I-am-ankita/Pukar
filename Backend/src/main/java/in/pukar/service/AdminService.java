package in.pukar.service;

import in.pukar.common.ApiException;
import in.pukar.dto.AdminDtos.*;
import in.pukar.entity.*;
import in.pukar.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AdminService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final DepartmentRepository departmentRepo;
    private final WardRepository wardRepo;
    private final SlaRuleRepository slaRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminService(UserRepository userRepo, RoleRepository roleRepo,
                        DepartmentRepository departmentRepo, WardRepository wardRepo,
                        SlaRuleRepository slaRepo,
                        PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.departmentRepo = departmentRepo;
        this.wardRepo = wardRepo;
        this.slaRepo = slaRepo;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    // ---- Users ----

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepo.findAll().stream().map(this::toUserDto).toList();
    }

    @Transactional
    public UserDto createUser(CreateUserRequest req, String actorId) {
        if (userRepo.existsByUsername(req.username()))
            throw ApiException.conflict("USERNAME_TAKEN", "Username already exists");
        if (userRepo.existsByEmail(req.email()))
            throw ApiException.conflict("EMAIL_TAKEN", "Email already exists");

        RoleName roleName = parseRole(req.role());
        Role role = roleRepo.findByName(roleName)
                .orElseThrow(() -> ApiException.badRequest("ROLE_NOT_FOUND", "Role not configured"));

        User u = new User();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setFullName(req.fullName());
        u.getRoles().add(role);
        if (req.departmentId() != null) {
            departmentRepo.findById(req.departmentId()).ifPresent(u::setDepartment);
        }
        if (req.wardId() != null) {
            wardRepo.findById(req.wardId()).ifPresent(u::setWard);
        }
        User saved = userRepo.save(u);
        auditService.recordChange("USER_CREATED", "USER", saved.getId(), actorId, "ADMIN",
                "{\"username\":\"" + saved.getUsername() + "\",\"role\":\"" + roleName + "\"}",
                null, Map.of("username", saved.getUsername(), "role", roleName.name(),
                        "departmentId", nzs(req.departmentId()), "wardId", nzs(req.wardId())));
        return toUserDto(saved);
    }

    @Transactional
    public UserDto updateRole(String userId, UpdateRoleRequest req, String actorId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        Role role = roleRepo.findByName(parseRole(req.role()))
                .orElseThrow(() -> ApiException.badRequest("ROLE_NOT_FOUND", "Role not configured"));
        String oldRole = u.getRoles().stream().map(Role::getName).map(Enum::name).findFirst().orElse("");
        String oldDeptId = u.getDepartment() != null ? u.getDepartment().getId() : "";
        String oldWardId = u.getWard() != null ? u.getWard().getId() : "";

        u.getRoles().clear();
        u.getRoles().add(role);
        if (req.departmentId() != null) {
            departmentRepo.findById(req.departmentId()).ifPresent(u::setDepartment);
        }
        if (req.wardId() != null) {
            wardRepo.findById(req.wardId()).ifPresent(u::setWard);
        }
        User saved = userRepo.save(u);
        auditService.recordChange("USER_ROLE_CHANGED", "USER", saved.getId(), actorId, "ADMIN",
                "{\"role\":\"" + req.role() + "\"}",
                Map.of("role", oldRole, "departmentId", oldDeptId, "wardId", oldWardId),
                Map.of("role", req.role(), "departmentId", nzs(req.departmentId()), "wardId", nzs(req.wardId())));
        return toUserDto(saved);
    }

    // ---- SLA rules ----

    @Transactional(readOnly = true)
    public List<SlaRuleDto> listSlaRules() {
        return slaRepo.findAll().stream()
                .map(r -> new SlaRuleDto(r.getId(), r.getCategory(),
                        r.getLevel1Hours(), r.getLevel2Hours(), r.getLevel3Hours(), r.isActive()))
                .toList();
    }

    @Transactional
    public SlaRuleDto createSlaRule(SlaRuleRequest req, String actorId) {
        SlaRule r = new SlaRule();
        r.setCategory(req.category().toUpperCase());
        r.setLevel1Hours(req.level1Hours());
        r.setLevel2Hours(req.level2Hours());
        r.setLevel3Hours(req.level3Hours());
        r.setCreatedBy(actorId);
        SlaRule saved = slaRepo.save(r);
        auditService.recordChange("SLA_RULE_CREATED", "SLA_RULE", saved.getId(), actorId, "ADMIN",
                "{\"category\":\"" + saved.getCategory() + "\"}", null,
                Map.of("level1Hours", saved.getLevel1Hours(), "level2Hours", saved.getLevel2Hours(),
                        "level3Hours", saved.getLevel3Hours()));
        return new SlaRuleDto(saved.getId(), saved.getCategory(),
                saved.getLevel1Hours(), saved.getLevel2Hours(), saved.getLevel3Hours(), saved.isActive());
    }

    @Transactional
    public SlaRuleDto updateSlaRule(String id, SlaRuleRequest req, String actorId) {
        SlaRule r = slaRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("SLA_NOT_FOUND", "SLA rule not found"));
        Map<String, Object> before = Map.of("level1Hours", r.getLevel1Hours(),
                "level2Hours", r.getLevel2Hours(), "level3Hours", r.getLevel3Hours());
        r.setLevel1Hours(req.level1Hours());
        r.setLevel2Hours(req.level2Hours());
        r.setLevel3Hours(req.level3Hours());
        SlaRule saved = slaRepo.save(r);
        auditService.recordChange("SLA_RULE_UPDATED", "SLA_RULE", saved.getId(), actorId, "ADMIN",
                "{\"category\":\"" + saved.getCategory() + "\"}", before,
                Map.of("level1Hours", saved.getLevel1Hours(), "level2Hours", saved.getLevel2Hours(),
                        "level3Hours", saved.getLevel3Hours()));
        return new SlaRuleDto(saved.getId(), saved.getCategory(),
                saved.getLevel1Hours(), saved.getLevel2Hours(), saved.getLevel3Hours(), saved.isActive());
    }

    // ---- Departments ----

    @Transactional(readOnly = true)
    public List<DepartmentDto> listDepartments() {
        return departmentRepo.findAll().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName(), d.getCode(),
                        d.getDistrict(), d.getState()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDto> officersInDepartment(String departmentId) {
        return userRepo.findByDepartmentId(departmentId).stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.OFFICER))
                .map(this::toUserDto).toList();
    }

    // ---- Wards ----

    @Transactional(readOnly = true)
    public List<WardDto> listWards() {
        return wardRepo.findAll().stream().map(this::toWardDto).toList();
    }

    @Transactional
    public WardDto createWard(CreateWardRequest req, String actorId) {
        if (wardRepo.existsByCode(req.code()))
            throw ApiException.conflict("WARD_CODE_TAKEN", "A ward with this code already exists");
        Ward w = new Ward();
        w.setCode(req.code());
        w.setName(req.name());
        w.setZone(req.zone());
        Ward saved = wardRepo.save(w);
        auditService.recordChange("WARD_CREATED", "WARD", saved.getId(), actorId, "ADMIN",
                "{\"code\":\"" + saved.getCode() + "\"}", null,
                Map.of("code", saved.getCode(), "name", saved.getName()));
        return toWardDto(saved);
    }

    @Transactional
    public WardDto linkDepartment(String wardId, LinkDepartmentRequest req, String actorId) {
        Ward w = wardRepo.findById(wardId)
                .orElseThrow(() -> ApiException.notFound("WARD_NOT_FOUND", "Ward not found"));
        Department d = departmentRepo.findById(req.departmentId())
                .orElseThrow(() -> ApiException.notFound("DEPARTMENT_NOT_FOUND", "Department not found"));
        List<String> before = w.getDepartments().stream().map(Department::getCode).toList();
        w.getDepartments().add(d);
        Ward saved = wardRepo.save(w);
        auditService.recordChange("WARD_DEPT_LINKED", "WARD", saved.getId(), actorId, "ADMIN",
                "{\"departmentId\":\"" + d.getId() + "\"}",
                Map.of("departments", before),
                Map.of("departments", saved.getDepartments().stream().map(Department::getCode).toList()));
        return toWardDto(saved);
    }

    @Transactional
    public WardDto unlinkDepartment(String wardId, String departmentId, String actorId) {
        Ward w = wardRepo.findById(wardId)
                .orElseThrow(() -> ApiException.notFound("WARD_NOT_FOUND", "Ward not found"));
        List<String> before = w.getDepartments().stream().map(Department::getCode).toList();
        w.getDepartments().removeIf(d -> d.getId().equals(departmentId));
        Ward saved = wardRepo.save(w);
        auditService.recordChange("WARD_DEPT_UNLINKED", "WARD", saved.getId(), actorId, "ADMIN",
                "{\"departmentId\":\"" + departmentId + "\"}",
                Map.of("departments", before),
                Map.of("departments", saved.getDepartments().stream().map(Department::getCode).toList()));
        return toWardDto(saved);
    }

    private WardDto toWardDto(Ward w) {
        List<DepartmentDto> depts = w.getDepartments().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getName(), d.getCode(), d.getDistrict(), d.getState()))
                .toList();
        return new WardDto(w.getId(), w.getCode(), w.getName(), w.getZone(), depts);
    }

    private UserDto toUserDto(User u) {
        String role = u.getRoles().stream().map(Role::getName).map(Enum::name).findFirst().orElse("");
        String deptId = u.getDepartment() != null ? u.getDepartment().getId() : null;
        String deptName = u.getDepartment() != null ? u.getDepartment().getName() : null;
        String wardId = u.getWard() != null ? u.getWard().getId() : null;
        String wardName = u.getWard() != null ? u.getWard().getName() : null;
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(),
                role, deptId, deptName, wardId, wardName, u.isActive());
    }

    private String nzs(String s) { return s == null ? "" : s; }

    private RoleName parseRole(String role) {
        try { return RoleName.valueOf(role.toUpperCase()); }
        catch (Exception e) { throw ApiException.badRequest("INVALID_ROLE", "Unknown role: " + role); }
    }
}
