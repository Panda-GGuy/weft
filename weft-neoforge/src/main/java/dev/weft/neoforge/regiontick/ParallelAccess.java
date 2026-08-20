package dev.weft.neoforge.regiontick;

/**
 * Marks the current thread as a Weft region worker for the duration of one
 * fanned-out bucket (P2 E1, RFC-0006). The safety mixins consult this on
 * their hot paths — chunk reads take the lock-free visible-map route,
 * dimension changes defer to the section-end queue — so the flag must be
 * set exactly around bucket execution and nowhere else.
 *
 * <p>A plain ThreadLocal (not inheritable): anything a bucket spawns on
 * another thread is NOT a region worker and must not borrow the privileges.
 */
public final class ParallelAccess {

    private ParallelAccess() {}

    private static final ThreadLocal<Boolean> REGION_WORKER = ThreadLocal.withInitial(() -> false);

    static void enterWorker() {
        REGION_WORKER.set(true);
    }

    static void exitWorker() {
        REGION_WORKER.set(false);
    }

    /** True only on a pool thread currently running a region bucket. */
    public static boolean isRegionWorker() {
        return REGION_WORKER.get();
    }

    // --- RFC-0006 hazard 22: border reads ---

    private static final java.util.concurrent.atomic.LongAdder BORDER_READS =
            new java.util.concurrent.atomic.LongAdder();

    /**
     * A worker resolved a chunk through the generated-FULL view because no
     * promoted view was available yet (RFC-0006 hazard 22).
     *
     * <p>Counted rather than silent because hazard 4's guard used to make this
     * a hard crash, and the concession that replaced it must stay visible: a
     * <em>small, stable</em> count is the border ring being read as vanilla
     * reads it, while a large or growing one is a worker reaching somewhere it
     * should not — the bug hazard 4 was written to catch. Surfaced by
     * {@code /weft status}.
     */
    public static void recordBorderRead() {
        BORDER_READS.increment();
    }

    public static long borderReads() {
        return BORDER_READS.sum();
    }

    static void resetBorderReads() {
        BORDER_READS.reset();
    }
}
