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
}
