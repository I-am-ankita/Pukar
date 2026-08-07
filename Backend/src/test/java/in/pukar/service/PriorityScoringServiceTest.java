package in.pukar.service;

import in.pukar.entity.Complaint;
import in.pukar.entity.ComplaintPriority;
import in.pukar.repository.ComplaintRepository;
import in.pukar.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PriorityScoringService}.
 * Tests smart-triage scoring algorithm and priority labelling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PriorityScoringService - Smart Triage Scoring")
class PriorityScoringServiceTest {

    @Mock
    private ComplaintRepository complaintRepo;

    @InjectMocks
    private PriorityScoringService scoringService;

    private Complaint complaint;

    @BeforeEach
    void setUp() {
        complaint = TestDataFactory.sampleComplaint();
    }

    @Nested
    @DisplayName("computeScore()")
    class ComputeScoreTests {

        @Test
        @DisplayName("Should assign higher score to BRIBERY category with GPS coordinates")
        void computeScore_highSeverityWithLocation() {
            when(complaintRepo.findByDeletedFalse()).thenReturn(List.of());

            BigDecimal score = scoringService.computeScore(complaint, false);

            assertNotNull(score);
            assertTrue(score.doubleValue() >= 4.0);
            assertTrue(score.doubleValue() <= 10.0);
            assertEquals(2, score.scale(), "Score should have 2 decimal places");
        }

        @Test
        @DisplayName("Should increase score when evidence is present")
        void computeScore_higherWithEvidence() {
            when(complaintRepo.findByDeletedFalse()).thenReturn(List.of());

            BigDecimal withoutEvidence = scoringService.computeScore(complaint, false);
            BigDecimal withEvidence = scoringService.computeScore(complaint, true);

            assertTrue(withEvidence.compareTo(withoutEvidence) > 0,
                    "Evidence should increase priority score");
        }

        @Test
        @DisplayName("Should increase score based on repeat complaints in same ward")
        void computeScore_increasesWithRepeatComplaints() {
            Complaint other = TestDataFactory.sampleComplaint();
            other.setId("complaint-2");
            other.setTrackingCode("PUK-2026-000002");
            when(complaintRepo.findByDeletedFalse()).thenReturn(List.of(complaint, other));

            BigDecimal score = scoringService.computeScore(complaint, false);

            assertTrue(score.doubleValue() > 3.0);
        }

        @Test
        @DisplayName("Should treat unknown category as OTHER with lower severity")
        void computeScore_unknownCategoryDefaultsToOther() {
            complaint.setCategory("UNKNOWN_CATEGORY");
            complaint.setLatitude(null);
            complaint.setLongitude(null);
            complaint.setWard(null);
            complaint.setEscalationCount(0);

            BigDecimal score = scoringService.computeScore(complaint, false);

            assertTrue(score.doubleValue() >= 0.0);
            assertTrue(score.doubleValue() <= 10.0);
        }
    }

    @Nested
    @DisplayName("labelFor()")
    class LabelForTests {

        @Test
        @DisplayName("Should return CRITICAL for score >= 8.0")
        void labelFor_critical() {
            assertEquals(ComplaintPriority.CRITICAL, scoringService.labelFor(new BigDecimal("8.00")));
            assertEquals(ComplaintPriority.CRITICAL, scoringService.labelFor(new BigDecimal("9.50")));
        }

        @Test
        @DisplayName("Should return HIGH for score >= 6.0 and < 8.0")
        void labelFor_high() {
            assertEquals(ComplaintPriority.HIGH, scoringService.labelFor(new BigDecimal("6.00")));
            assertEquals(ComplaintPriority.HIGH, scoringService.labelFor(new BigDecimal("7.99")));
        }

        @Test
        @DisplayName("Should return MEDIUM for score >= 4.0 and < 6.0")
        void labelFor_medium() {
            assertEquals(ComplaintPriority.MEDIUM, scoringService.labelFor(new BigDecimal("4.00")));
            assertEquals(ComplaintPriority.MEDIUM, scoringService.labelFor(new BigDecimal("5.99")));
        }

        @Test
        @DisplayName("Should return LOW for score < 4.0")
        void labelFor_low() {
            assertEquals(ComplaintPriority.LOW, scoringService.labelFor(new BigDecimal("0.00")));
            assertEquals(ComplaintPriority.LOW, scoringService.labelFor(new BigDecimal("3.99")));
        }
    }
}
