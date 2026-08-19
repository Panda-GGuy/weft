package dev.weft.engine.telemetry.export;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the {@code tick_outlier} trigger (RFC-0009 §5). The point of the event
 * is that a human sees the spikes and only the spikes — a detector that fires on
 * warmup, or on every tick of a struggling server, gets its alert rule muted and
 * then the whole feature is worthless.
 */
class TickOutlierDetectorTest {

    private static final long MS = 1_000_000L;

    private static TickOutlierDetector warmedUpAt(long tickNanos, double factor) {
        TickOutlierDetector detector = new TickOutlierDetector(factor);
        for (int i = 0; i < TickOutlierDetector.REFRESH_TICKS * 2; i++) {
            detector.observe(tickNanos);
        }
        return detector;
    }

    @Test
    void aFactorAtOrBelowOneIsRefused() {
        // Would flag roughly half of all ticks, i.e. flag nothing usefully.
        assertThrows(IllegalArgumentException.class, () -> new TickOutlierDetector(1.0));
        assertThrows(IllegalArgumentException.class, () -> new TickOutlierDetector(0.5));
    }

    @Test
    void nothingFiresBeforeTheWindowHasEnoughSamples() {
        TickOutlierDetector detector = new TickOutlierDetector(4.0);
        // A server's first ticks are chunk loading and mob spawning. Reporting
        // those as anomalies is the noise that gets an alert rule turned off.
        for (int i = 0; i < TickOutlierDetector.REFRESH_TICKS - 1; i++) {
            assertFalse(detector.observe(500 * MS), "fired during warmup at tick " + i);
        }
    }

    @Test
    void aSpikePastTheFactorFires() {
        TickOutlierDetector detector = warmedUpAt(50 * MS, 4.0);
        assertEquals(50 * MS, detector.medianNanos());

        assertFalse(detector.observe(150 * MS), "3x the median is under a 4x factor");
        assertTrue(detector.observe(400 * MS), "8x the median must fire");
    }

    @Test
    void aSustainedSlowdownMovesTheBaselineInsteadOfCryingWolf() {
        TickOutlierDetector detector = warmedUpAt(50 * MS, 4.0);
        // A server that settles at 300ms/tick is broken, but every tick is not
        // an "outlier" — the median has to follow, or the stream becomes a
        // 20-per-second firehose of the same finding.
        int fired = 0;
        for (int i = 0; i < TickOutlierDetector.WINDOW_TICKS * 2; i++) {
            if (detector.observe(300 * MS)) {
                fired++;
            }
        }
        assertEquals(300 * MS, detector.medianNanos(),
                "the median must track the new regime");
        assertTrue(fired < TickOutlierDetector.WINDOW_TICKS / 4,
                "fired " + fired + " times on a steady (if slow) server");
        // And a real spike above the NEW baseline still fires.
        assertTrue(detector.observe(2_000 * MS));
    }

    @Test
    void theMedianIsAValueActuallyObservedNotAnInterpolation() {
        TickOutlierDetector detector = new TickOutlierDetector(4.0);
        long[] durations = {10 * MS, 20 * MS, 30 * MS, 40 * MS};
        for (int repeat = 0; repeat < TickOutlierDetector.REFRESH_TICKS; repeat++) {
            for (long duration : durations) {
                detector.observe(duration);
            }
        }
        // Nearest-rank, no interpolation: every number reported is a duration
        // that really happened, which keeps it quotable as a measurement. Same
        // rule SectionSamples uses.
        long median = detector.medianNanos();
        assertTrue(median == 20 * MS || median == 30 * MS,
                "median was " + median / MS + "ms, expected an observed value");
    }

    @Test
    void theWindowIsBoundedSoAnUptimeOfDaysCostsNothingExtra() {
        TickOutlierDetector detector = new TickOutlierDetector(4.0);
        for (int i = 0; i < TickOutlierDetector.WINDOW_TICKS * 5; i++) {
            detector.observe(50 * MS);
        }
        assertEquals(TickOutlierDetector.WINDOW_TICKS, detector.sampleCount());
    }
}
