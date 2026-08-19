package dev.weft.engine.telemetry;

import java.util.Arrays;

/**
 * A fixed-capacity collector of per-tick section durations, plus the order
 * statistics a benchmark should quote instead of a mean.
 *
 * <p>Exists because P2's first throughput attempt measured <em>full-tick</em>
 * MSPT to judge a change confined to the block-entity section. At the
 * workloads involved the section is a small slice of a tick that is mostly
 * other things, so the effect was swamped: six same-run A/B runs produced
 * ratios spanning 0.85x–1.31x, i.e. the instrument could not tell a win from
 * a loss. This class is the narrower ruler — one sample per tick, covering
 * only the section under test.
 *
 * <p>Deliberately median-and-percentile rather than mean: tick durations are
 * right-skewed (one GC pause or one chunk load drags a mean but barely moves
 * a median), and the honest claim available for block-entity sharding so far
 * is about the <em>tail</em>, which a mean hides.
 *
 * <p>Not thread-safe. Callers record from one thread — for the block-entity
 * section that is the server thread, which is where the section begins and
 * ends even when its buckets fan out.
 */
public final class SectionSamples {

    /** Order statistics of one measurement window, in nanoseconds. */
    public record Stats(int count, long min, long median, long p95, long max, double meanNanos) {

        public double medianMillis() {
            return median / 1_000_000.0;
        }

        public double p95Millis() {
            return p95 / 1_000_000.0;
        }

        public double minMillis() {
            return min / 1_000_000.0;
        }

        public double maxMillis() {
            return max / 1_000_000.0;
        }
    }

    private final long[] samples;
    private int count;
    private int dropped;

    public SectionSamples(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.samples = new long[capacity];
    }

    /**
     * Record one section duration. Samples past capacity are counted as
     * dropped rather than overwriting earlier ones — a window that overflows
     * has measured something other than what its owner asked for, and
     * {@link #dropped()} is how the caller finds out instead of silently
     * comparing a truncated window against a whole one.
     */
    public void record(long nanos) {
        if (count < samples.length) {
            samples[count++] = nanos;
        } else {
            dropped++;
        }
    }

    public int count() {
        return count;
    }

    public int dropped() {
        return dropped;
    }

    /** Samples in recording order. */
    public long[] snapshot() {
        return Arrays.copyOf(samples, count);
    }

    /**
     * Order statistics over the window, optionally discarding the first
     * {@code skipLeading} samples.
     *
     * <p>{@code skipLeading} is not cosmetic. In an interleaved A/B/A/B
     * benchmark the flag flips at a tick boundary, and the tick where it
     * flips (plus the next few, while the new path's branch predictions and
     * caches settle) belongs to neither phase cleanly. Charging those ticks
     * to a phase is exactly the kind of attribution error that produced the
     * retracted 1.59x.
     *
     * @return null when fewer than one sample survives the skip
     */
    public Stats stats(int skipLeading) {
        return statsOf(samples, count, skipLeading);
    }

    /** Same statistics over a caller-held array (all of it). */
    public static Stats statsOf(long[] values) {
        return statsOf(values, values.length, 0);
    }

    private static Stats statsOf(long[] values, int length, int skipLeading) {
        int from = Math.max(0, skipLeading);
        if (from >= length) {
            return null;
        }
        long[] sorted = Arrays.copyOfRange(values, from, length);
        Arrays.sort(sorted);
        long sum = 0;
        for (long v : sorted) {
            sum += v;
        }
        return new Stats(sorted.length, sorted[0], percentile(sorted, 50),
                percentile(sorted, 95), sorted[sorted.length - 1],
                (double) sum / sorted.length);
    }

    /**
     * Nearest-rank percentile on an already-sorted array. No interpolation:
     * every value returned is a duration actually observed, which keeps the
     * reported figures quotable as measurements rather than as estimates.
     */
    private static long percentile(long[] sorted, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        return sorted[Math.min(sorted.length, Math.max(1, rank)) - 1];
    }
}
