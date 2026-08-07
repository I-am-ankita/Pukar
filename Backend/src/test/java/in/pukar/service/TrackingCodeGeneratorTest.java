package in.pukar.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Year;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TrackingCodeGenerator}.
 * Validates citizen-facing tracking code format: PUK-YYYY-NNNNNN.
 */
@DisplayName("TrackingCodeGenerator - Complaint Tracking Codes")
class TrackingCodeGeneratorTest {

    private static final Pattern CODE_PATTERN =
            Pattern.compile("^PUK-\\d{4}-\\d{6}$");

    private final TrackingCodeGenerator generator = new TrackingCodeGenerator();

    @Test
    @DisplayName("Should generate code matching PUK-YYYY-NNNNNN format")
    void generate_matchesExpectedFormat() {
        String code = generator.generate();

        assertTrue(CODE_PATTERN.matcher(code).matches(),
                "Expected format PUK-YYYY-NNNNNN but got: " + code);
    }

    @Test
    @DisplayName("Should embed the current year in the tracking code")
    void generate_containsCurrentYear() {
        String code = generator.generate();
        int currentYear = Year.now().getValue();

        assertTrue(code.contains("PUK-" + currentYear + "-"),
                "Code should contain current year: " + code);
    }

    @RepeatedTest(5)
    @DisplayName("Should produce non-null codes on repeated generation")
    void generate_isNonNull() {
        assertNotNull(generator.generate());
    }
}
