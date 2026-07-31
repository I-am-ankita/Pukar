package in.pukar.service;

import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TrackingCodeGenerator {
    /** Produces citizen-facing codes like PUK-2025-048217. */
    public String generate() {
        int year = Year.now().getValue();
        int n = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format("PUK-%d-%06d", year, n);
    }
}
