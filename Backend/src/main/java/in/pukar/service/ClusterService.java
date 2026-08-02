package in.pukar.service;

import in.pukar.entity.Complaint;
import in.pukar.entity.ComplaintCluster;
import in.pukar.repository.ComplaintClusterRepository;
import in.pukar.repository.ComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Complaint clustering engine (Intelligence tier).
 * Groups complaints with the same category within ~500m and a 7-day window.
 */
@Service
public class ClusterService {

    private static final double RADIUS_METERS = 500.0;
    private static final long WINDOW_DAYS = 7;

    private final ComplaintRepository complaintRepo;
    private final ComplaintClusterRepository clusterRepo;

    public ClusterService(ComplaintRepository complaintRepo, ComplaintClusterRepository clusterRepo) {
        this.complaintRepo = complaintRepo;
        this.clusterRepo = clusterRepo;
    }

    @Transactional
    public void assignCluster(Complaint complaint) {
        if (complaint.getLatitude() == null || complaint.getLongitude() == null) return;

        List<ComplaintCluster> candidates = clusterRepo.findByCategory(complaint.getCategory());
        for (ComplaintCluster cluster : candidates) {
            if (cluster.getLatitudeCenter() == null || cluster.getLongitudeCenter() == null) continue;
            double dist = haversine(
                    complaint.getLatitude().doubleValue(), complaint.getLongitude().doubleValue(),
                    cluster.getLatitudeCenter().doubleValue(), cluster.getLongitudeCenter().doubleValue());
            boolean withinWindow = cluster.getCreatedAt() != null
                    && Duration.between(cluster.getCreatedAt(), complaint.getCreatedAt()).abs().toDays() <= WINDOW_DAYS;
            if (dist <= RADIUS_METERS && withinWindow) {
                cluster.setMemberCount(cluster.getMemberCount() + 1);
                clusterRepo.save(cluster);
                complaint.setClusterId(cluster.getId());
                return;
            }
        }

        // no matching cluster -> start a new one
        ComplaintCluster cluster = new ComplaintCluster();
        cluster.setPrimaryComplaintId(complaint.getId());
        cluster.setCategory(complaint.getCategory());
        cluster.setLatitudeCenter(complaint.getLatitude());
        cluster.setLongitudeCenter(complaint.getLongitude());
        cluster.setMemberCount(1);
        cluster.setClusterLabel(buildLabel(complaint));
        clusterRepo.save(cluster);
        complaint.setClusterId(cluster.getId());
    }

    private String buildLabel(Complaint c) {
        String where = c.getWard() != null ? c.getWard()
                : (c.getLocationText() != null ? c.getLocationText() : "Unknown area");
        return c.getCategory() + " · " + where;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // metres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
