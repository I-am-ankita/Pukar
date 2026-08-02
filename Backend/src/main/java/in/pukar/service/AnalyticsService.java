package in.pukar.service;

import in.pukar.dto.AnalyticsDtos.*;
import in.pukar.entity.*;
import in.pukar.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final ComplaintRepository complaintRepo;
    private final ComplaintClusterRepository clusterRepo;
    private final DepartmentRepository departmentRepo;
    private final UserRepository userRepo;

    public AnalyticsService(ComplaintRepository complaintRepo,
                            ComplaintClusterRepository clusterRepo,
                            DepartmentRepository departmentRepo,
                            UserRepository userRepo) {
        this.complaintRepo = complaintRepo;
        this.clusterRepo = clusterRepo;
        this.departmentRepo = departmentRepo;
        this.userRepo = userRepo;
    }

    private static final Set<ComplaintStatus> RESOLVED = Set.of(
            ComplaintStatus.RESOLVED_CLAIMED, ComplaintStatus.CITIZEN_VERIFIED, ComplaintStatus.CLOSED);

    @Transactional(readOnly = true)
    public HeatmapResponse heatmap(String timeWindow, String category, String severity) {
        Instant since = sinceFor(timeWindow);
        List<HeatPoint> points = complaintRepo.findPublicVisible().stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .filter(c -> since == null || (c.getCreatedAt() != null && c.getCreatedAt().isAfter(since)))
                .filter(c -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(c.getCategory()))
                .filter(c -> severity == null || severity.isBlank()
                        || severity.equalsIgnoreCase(c.getPriority().name()))
                .map(c -> new HeatPoint(
                        c.getId(), c.getTrackingCode(), c.getCategory(), c.getStatus().name(),
                        c.getPriority().name(), c.getWard(), c.getLocationText(),
                        c.getLatitude().doubleValue(), c.getLongitude().doubleValue(),
                        weightFor(c.getPriority()), 1))
                .collect(Collectors.toList());
        return new HeatmapResponse(points, Instant.now());
    }

    @Transactional(readOnly = true)
    public PublicStats publicStats() {
        List<Complaint> all = complaintRepo.findByDeletedFalse();
        long total = all.size();
        long resolved = all.stream().filter(c -> RESOLVED.contains(c.getStatus())).count();
        long unresolved = total - resolved;
        double rate = total == 0 ? 0 : (resolved * 100.0) / total;

        // worst wards by unresolved reports
        Map<String, List<Complaint>> byWard = all.stream()
                .filter(c -> c.getWard() != null && !c.getWard().isBlank())
                .collect(Collectors.groupingBy(Complaint::getWard));

        List<WardStat> worst = byWard.entrySet().stream()
                .map(e -> {
                    long reports = e.getValue().size();
                    long res = e.getValue().stream().filter(c -> RESOLVED.contains(c.getStatus())).count();
                    String zone = e.getValue().stream().findFirst()
                            .map(c -> c.getDepartmentId()).map(this::zoneHint).orElse("—");
                    int pct = reports == 0 ? 0 : (int) Math.round(res * 100.0 / reports);
                    return new WardStat(0, e.getKey(), zone, reports, res, pct);
                })
                .sorted(Comparator.comparingLong((WardStat w) -> w.reports() - w.resolved()).reversed())
                .limit(8)
                .collect(Collectors.toList());

        List<WardStat> ranked = new ArrayList<>();
        for (int i = 0; i < worst.size(); i++) {
            WardStat w = worst.get(i);
            ranked.add(new WardStat(i + 1, w.ward(), w.zone(), w.reports(), w.resolved(), w.resolvedPct()));
        }

        Map<String, Long> byCat = all.stream()
                .collect(Collectors.groupingBy(Complaint::getCategory, Collectors.counting()));
        List<CategoryStat> categoryStats = byCat.entrySet().stream()
                .map(e -> new CategoryStat(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(CategoryStat::count).reversed())
                .collect(Collectors.toList());

        return new PublicStats(unresolved, resolved, total, round1(rate), ranked, categoryStats);
    }

    @Transactional(readOnly = true)
    public List<ClusterDto> clusters() {
        return clusterRepo.findAll().stream()
                .filter(c -> c.getMemberCount() >= 1)
                .sorted(Comparator.comparingInt(ComplaintCluster::getMemberCount).reversed())
                .map(c -> new ClusterDto(c.getId(), c.getCategory(), c.getClusterLabel(),
                        c.getLatitudeCenter(), c.getLongitudeCenter(), c.getMemberCount(),
                        c.getPrimaryComplaintId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentScorecard> departmentScorecards() {
        List<Complaint> all = complaintRepo.findByDeletedFalse();
        Map<String, List<Complaint>> byDept = all.stream()
                .filter(c -> c.getDepartmentId() != null)
                .collect(Collectors.groupingBy(Complaint::getDepartmentId));

        List<DepartmentScorecard> result = new ArrayList<>();
        for (Department d : departmentRepo.findAll()) {
            List<Complaint> list = byDept.getOrDefault(d.getId(), List.of());
            long totalC = list.size();
            long resolvedC = list.stream().filter(c -> RESOLVED.contains(c.getStatus())).count();

            double avgHours = list.stream()
                    .filter(c -> RESOLVED.contains(c.getStatus()) && c.getCreatedAt() != null)
                    .mapToDouble(c -> Duration.between(c.getCreatedAt(),
                            c.getUpdatedAt() != null ? c.getUpdatedAt() : Instant.now()).toHours())
                    .average().orElse(0);

            long breached = list.stream()
                    .filter(c -> c.getEscalationCount() > 0)
                    .count();
            double slaCompliance = totalC == 0 ? 100 : (100.0 - (breached * 100.0 / totalC));

            long satisfied = list.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getCitizenFeedback())).count();
            long withFeedback = list.stream().filter(c -> c.getCitizenFeedback() != null).count();
            double satisfaction = withFeedback == 0 ? 0 : (satisfied * 100.0 / withFeedback);

            double resolutionPct = totalC == 0 ? 0 : (resolvedC * 100.0 / totalC);
            double efficiency = round1(0.5 * resolutionPct + 0.3 * slaCompliance + 0.2 * satisfaction) / 10.0;

            result.add(new DepartmentScorecard(d.getId(), d.getName(), d.getCode(),
                    totalC, resolvedC, round1(avgHours), round1(slaCompliance),
                    round1(satisfaction), round1(efficiency)));
        }
        result.sort(Comparator.comparingDouble(DepartmentScorecard::efficiencyIndex).reversed());
        return result;
    }

    /**
     * Corruption graph: officers + departments as nodes, "handled" edges weighted
     * by complaint volume; risk derived from escalations and unresolved load.
     */
    @Transactional(readOnly = true)
    public CorruptionGraph corruptionGraph() {
        List<Complaint> all = complaintRepo.findByDeletedFalse();
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Map<String, Integer> edgeWeights = new LinkedHashMap<>();
        Map<String, GraphEdge> edges = new LinkedHashMap<>();

        // department nodes
        for (Department d : departmentRepo.findAll()) {
            List<Complaint> deptList = all.stream()
                    .filter(c -> d.getId().equals(c.getDepartmentId())).toList();
            long esc = deptList.stream().filter(c -> c.getEscalationCount() > 0).count();
            double risk = deptList.isEmpty() ? 0 : Math.min(10, esc * 10.0 / deptList.size());
            nodes.put("dept-" + d.getId(),
                    new GraphNode("dept-" + d.getId(), "DEPARTMENT", d.getName(), round1(risk)));
        }

        // officer nodes + handled edges
        for (Complaint c : all) {
            if (c.getAssignedOfficerId() == null || c.getDepartmentId() == null) continue;
            String offNode = "officer-" + c.getAssignedOfficerId();
            String deptNode = "dept-" + c.getDepartmentId();
            nodes.computeIfAbsent(offNode, k -> {
                String label = userRepo.findById(c.getAssignedOfficerId())
                        .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                        .orElse("Officer");
                return new GraphNode(offNode, "OFFICER", label, 0);
            });
            String key = offNode + "->" + deptNode;
            edgeWeights.merge(key, 1, Integer::sum);
        }

        edgeWeights.forEach((key, w) -> {
            String[] parts = key.split("->");
            edges.put(key, new GraphEdge(parts[0], parts[1], "handled", w));
        });

        // officer risk = unresolved/escalated share
        Map<String, long[]> officerStats = new HashMap<>();
        for (Complaint c : all) {
            if (c.getAssignedOfficerId() == null) continue;
            long[] s = officerStats.computeIfAbsent(c.getAssignedOfficerId(), k -> new long[2]);
            s[0]++; // total
            if (c.getEscalationCount() > 0) s[1]++; // escalated
        }
        List<GraphNode> finalNodes = nodes.values().stream().map(n -> {
            if (!n.type().equals("OFFICER")) return n;
            String offId = n.id().replace("officer-", "");
            long[] s = officerStats.getOrDefault(offId, new long[]{0, 0});
            double risk = s[0] == 0 ? 0 : Math.min(10, s[1] * 10.0 / s[0]);
            return new GraphNode(n.id(), n.type(), n.label(), round1(risk));
        }).collect(Collectors.toList());

        return new CorruptionGraph(finalNodes, new ArrayList<>(edges.values()));
    }

    // ---- helpers ----

    private Instant sinceFor(String window) {
        if (window == null) return null;
        return switch (window) {
            case "24h" -> Instant.now().minus(24, ChronoUnit.HOURS);
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            default -> null;
        };
    }

    private double weightFor(ComplaintPriority p) {
        return switch (p) {
            case CRITICAL -> 1.0;
            case HIGH -> 0.75;
            case MEDIUM -> 0.5;
            case LOW -> 0.25;
        };
    }

    private String zoneHint(String deptId) {
        return departmentRepo.findById(deptId).map(Department::getDistrict).orElse("—");
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}
