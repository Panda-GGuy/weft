package dev.weft.engine.telemetry.export;

import java.util.Arrays;

/**
 * Decides which ticks are worth an event (RFC-0009 §5, {@code tick_outlier}).
 *
 * <p>A tick is an outlier when it exceeds {@code factor} times the rolling
 * median of recent ticks. Median rather than mean, for the reason
 * {@code SectionSamples} already records: tick durations are right-skewed, and
 * one GC pause drags a mean while barely moving a median — so a mean baseline
 * makes the spike that moved it look normal.
 *
 * <p><b>Cost.</b> {@link #observe} is one array store and one comparison. The
 * median is recomputed every {@link #REFRESH_TICKS} ticks rather than every
 * tick, so the sort is amortised to a few dozen operations per tick — and the
 * whole class only runs while the {@code observability} module is active.
 *
 * <p>Not thread-safe: observed from the tick boundary, one thread.
 */
public final class TickOutlierDetector {

    /** How often the median is recomputed. 20 ticks = once a second. */
    static final int REFRESH_TICKS = 20;

    /**
     * Ticks a median is computed over. 600 = 30 seconds, long enough that a
     * sustained slowdown moves the baseline (so a struggling server stops
     * crying outlier on every tick) and short enough to track a real regime
     * change within half a minute.
     */
    static final int WINDOW_TICKS = 600;

    private final double factor;
    private final long[] window = new long[WINDOW_TICKS];
    private int filled;
    private int next;
    private int sinceRefresh = REFRESH_TICKS;
    private long medianNanos;
    private long meanNanos;

    /**
     * @param factor multiple of the rolling median above which a tick is an
     *               outlier; {@code <= 1} would flag roughly half of all ticks
     */
    public TickOutlierDetector(double factor) {
        if (!(factor > 1.0)) {
            throw new IllegalArgumentException("tickOutlierFactor must be > 1, got " + factor);
        }
        this.factor = factor;
    }

    /**
     * Record one tick and say whether it is an outlier.
     *
     * <p>Returns false until the window has enough samples to have a meaningful
     * median. Firing on the first few ticks of a server's life would report
     * chunk loading and mob spawning as anomalies, which is the noise that gets
     * an alert rule muted.
     */
    public boolean observe(long tickNanos) {
        window[next] = tickNanos;
        next = (next + 1) % WINDOW_TICKS;
        if (filled < WINDOW_TICKS) {
            filled++;
        }
        if (++sinceRefresh >= REFRESH_TICKS) {
            sinceRefresh = 0;
            medianNanos = computeMedian();
            meanNanos = computeMean();
        }
        return medianNanos > 0 && filled >= REFRESH_TICKS
                && tickNanos > medianNanos * factor;
    }

    /** The current rolling median, in nanoseconds; 0 before the first refresh. */
    public long medianNanos() {
        return medianNanos;
    }

    /**
     * The current rolling <em>mean</em>, in nanoseconds; 0 before the first
     * refresh.
     *
     * <p>The median is the right baseline for outlier detection, for the reason
     * this class's header gives: one GC pause drags a mean while barely moving a
     * median. But it is the wrong basis for a <b>TPS</b> figure, and publishing
     * TPS from it produced a concretely false reading — a live server with 45% of
     * its ticks over 50 ms reported a flat 20 TPS, because its median tick landed
     * just under budget. Overruns are exactly what a rate is supposed to include,
     * so a rate needs the mean.
     *
     * <p>Both are exposed rather than one replacing the other: the median still
     * drives outlier detection, and the pair together says something neither says
     * alone — a mean far above the median means a minority of very slow ticks,
     * while the two converging means a uniformly slow server.
     */
    public long meanNanos() {
        return meanNanos;
    }

    public double factor() {
        return factor;
    }

    /** Samples currently in the window. */
    public int sampleCount() {
        return filled;
    }

    /**
     * Nearest-rank median, no interpolation — every value reported is a duration
     * actually observed, which keeps the number quotable as a measurement rather
     * than an estimate. Same rule {@code SectionSamples} uses.
     */
    private long computeMedian() {
        if (filled == 0) {
            return 0L;
        }
        long[] sorted = Arrays.copyOf(window, filled);
        Arrays.sort(sorted);
        return sorted[(sorted.length - 1) / 2];
    }

    /** Arithmetic mean over the same window, recomputed on the same schedule. */
    private long computeMean() {
        if (filled == 0) {
            return 0L;
        }
        long sum = 0L;
        for (int i = 0; i < filled; i++) {
            sum += window[i];
        }
        return sum / filled;
    }
}
