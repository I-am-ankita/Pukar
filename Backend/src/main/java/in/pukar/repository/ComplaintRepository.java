package in.pukar.repository;

import in.pukar.entity.Complaint;
import in.pukar.entity.ComplaintStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComplaintRepository extends JpaRepository<Complaint, String> {

    Optional<Complaint> findByTrackingCode(String trackingCode);

    Page<Complaint> findByAssignedOfficerIdAndDeletedFalse(String officerId, Pageable pageable);

    Page<Complaint> findByDepartmentIdAndDeletedFalse(String departmentId, Pageable pageable);

    List<Complaint> findByDeletedFalse();

    List<Complaint> findByReporterHash(String reporterHash);

    List<Complaint> findByStatusInAndDeletedFalse(List<ComplaintStatus> statuses);

    long countByDepartmentId(String departmentId);

    long countByStatus(ComplaintStatus status);

    @Query("SELECT c FROM Complaint c WHERE c.deleted = false AND c.publicVisible = true")
    List<Complaint> findPublicVisible();

    // candidates for SLA breach: active states with deadline before now
    @Query("SELECT c FROM Complaint c WHERE c.deleted = false AND c.slaDeadline < :now " +
           "AND c.status IN (in.pukar.entity.ComplaintStatus.SUBMITTED, in.pukar.entity.ComplaintStatus.ASSIGNED, " +
           "in.pukar.entity.ComplaintStatus.IN_PROGRESS, in.pukar.entity.ComplaintStatus.ESCALATED)")
    List<Complaint> findSlaBreached(@Param("now") Instant now);

    // candidates for feedback auto-close: claimed/approved past window
    @Query("SELECT c FROM Complaint c WHERE c.deleted = false AND c.status = in.pukar.entity.ComplaintStatus.RESOLVED_CLAIMED " +
           "AND c.supervisorApprovedAt IS NOT NULL AND c.supervisorApprovedAt < :cutoff")
    List<Complaint> findFeedbackExpired(@Param("cutoff") Instant cutoff);
}
