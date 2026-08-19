package dev.weft.engine.guard;

import dev.weft.api.telemetry.WeftTelemetry;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Ownership assertions on every mutation path (RFC-0001 §4.4).
 *
 * <p>Modes: DEV throws with forensics; DEGRADE routes the violation to the
 * rightful owner as mail (the caller receives {@code false} and must post a
 * {@code Message.BlockWrite}/{@code Task} instead); HARDENED throws always.
 * LEGACY-lane threads pass all checks by construction: they run while every
 * other simulation worker is parked (RFC §7.2).
 */
public final class WeftGuards {

    public enum Mode { DEV, DEGRADE, HARDENED }

    private static volatile Mode mode = Mode.DEV;
    private static final AtomicLong trips = new AtomicLong();

    // --- WS-7 forensics (RFC-0009 §3.8) ---
    //
    // A trip's forensics were being constructed only as an exception message and
    // then discarded: the count survived, the story did not. These make the
    // RFC-0001 §4.4 report structured, without adding a probe — every allocation
    // below happens only on a trip, which is already an exceptional path that
    // either throws or reroutes a mutation as mail.

    /** Which guard tripped. */
    public enum TripKind { REGION_MUTATION, SHARD_MUTATION }

    /**
     * What the trip cost, which is what decides whether a human must be paged:
     * a degraded write is a compat fact, a throw is an outage.
     */
    public enum Severity { DEV_THROW, DEGRADED_TO_MAIL, HARDENED_THROW }

    /** What became of the mutation. */
    public enum Degradation { ROUTED_AS_MAIL, THREW }

    /** One ownership violation, with the RFC-0001 §4.4 report as fields. */
    public record GuardTrip(TripKind kind, Severity severity, String thread,
                            ThreadContext.Kind contextKind, long contextOwner,
                            String targetKind, long targetId, Degradation degradation,
                            List<String> stack) {}

    /**
     * Frames kept from the offending stack. Enough to name the mod and the call
     * path; short enough that a trip storm cannot fill the event file with one
     * server's stack traces.
     */
    private static final int STACK_FRAMES = 12;

    /**
     * Notified per trip. Null unless the observability module is active, and the
     * null check is what keeps the stack walk — the only expensive part — from
     * happening at all when nobody is listening (R6).
     */
    private static volatile Consumer<GuardTrip> tripListener;

    /**
     * The exported trip counter. Registered lazily so that a build with no
     * telemetry never creates it; {@code WeftTelemetry} gates the increment on
     * its own enabled flag, so this costs one volatile read on a path that was
     * about to throw anyway.
     */
    private static volatile WeftTelemetry.Counters tripCounter;

    private WeftGuards() {}

    public static void setMode(Mode m) {
        mode = m;
    }

    public static long tripCount() {
        return trips.get();
    }

    /** Install the WS-7 forensics listener, or {@code null} to detach (R6). */
    public static void setTripListener(Consumer<GuardTrip> listener) {
        tripListener = listener;
    }

    /**
     * Record a trip: bump the exported counter, and build the full forensic
     * record only if something is listening.
     */
    private static void report(TripKind kind, ThreadContext ctx, String targetKind,
                               long targetId, boolean degraded) {
        Severity severity = degraded ? Severity.DEGRADED_TO_MAIL
                : mode == Mode.HARDENED ? Severity.HARDENED_THROW : Severity.DEV_THROW;
        WeftTelemetry.Counters counter = tripCounter;
        if (counter == null) {
            counter = tripCounter = WeftTelemetry.counter("weft_guard_trips_total",
                    "Ownership guard trips, by guard and by what the trip cost.",
                    "kind", "severity");
        }
        counter.inc(wire(kind), wire(severity));

        Consumer<GuardTrip> listener = tripListener;
        if (listener == null) {
            return;
        }
        listener.accept(new GuardTrip(kind, severity, Thread.currentThread().getName(),
                ctx.kind(), ctx.ownerId(), targetKind, targetId,
                degraded ? Degradation.ROUTED_AS_MAIL : Degradation.THREW,
                captureStack()));
    }

    /** Lower-case enum name: the wire spelling the schema and the labels use. */
    public static String wire(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Top frames of the offending stack, with this class's own frames dropped —
     * the first useful line should be the caller that tried the mutation, not
     * the guard that caught it.
     */
    private static List<String> captureStack() {
        return StackWalker.getInstance().walk(frames -> frames
                .dropWhile(f -> f.getClassName().equals(WeftGuards.class.getName()))
                .limit(STACK_FRAMES)
                .map(StackWalker.StackFrame::toString)
                .toList());
    }

    /**
     * Check that the current thread may mutate state owned by
     * {@code regionId}.
     *
     * @return true if the mutation may proceed directly; false if the caller
     *         must route it as mail (DEGRADE mode only).
     * @throws WrongOwnerException in DEV/HARDENED modes on violation.
     */
    public static boolean checkRegionMutation(long regionId) {
        ThreadContext ctx = ThreadContext.current();
        boolean ok = switch (ctx.kind()) {
            case REGION -> ctx.ownerId() == regionId;
            case LEGACY, GLOBAL -> true; // serialized phases: sole mutators by construction
            case GRAPH -> false;         // graphs mutate only via commit logs
            // Strict rule (RFC-0004 §2.2): a shard thread never touches
            // region-shared state directly, even in its own region — the
            // entity list and friends are coordinator-only during fan-out.
            case SHARD -> false;
            case NONE -> false;
        };
        if (ok) {
            return true;
        }
        trips.incrementAndGet();
        boolean degraded = mode == Mode.DEGRADE;
        report(TripKind.REGION_MUTATION, ctx, "region", regionId, degraded);
        if (degraded) {
            return false;
        }
        throw new WrongOwnerException(
                "Thread [" + Thread.currentThread().getName() + "] with context " + ctx.kind()
                + "/" + ctx.ownerId() + " attempted to mutate state owned by region " + regionId);
    }

    /**
     * Check that the current thread may directly mutate entity state owned
     * by the shard identified by {@code shardKey} (RFC-0004 §2.2). Direct
     * mutation is own-shard only; everything cross-entity goes through the
     * {@link dev.weft.api.entity.EntityEffectLog}.
     */
    public static boolean checkShardMutation(long shardKey) {
        ThreadContext ctx = ThreadContext.current();
        boolean ok = switch (ctx.kind()) {
            case SHARD -> ctx.ownerId() == shardKey;
            // Serial (unsharded) path: the region owner owns all its shards.
            case REGION -> ctx.ownerId() == dev.weft.engine.region.ShardKey.regionId(shardKey);
            case LEGACY, GLOBAL -> true;
            case GRAPH -> false;
            case NONE -> false;
        };
        if (ok) {
            return true;
        }
        trips.incrementAndGet();
        boolean degraded = mode == Mode.DEGRADE;
        report(TripKind.SHARD_MUTATION, ctx, "shard", shardKey, degraded);
        if (degraded) {
            return false;
        }
        throw new WrongOwnerException(
                "Thread [" + Thread.currentThread().getName() + "] with context " + ctx.kind()
                + "/" + ctx.ownerId() + " attempted to mutate entity state owned by shard "
                + dev.weft.engine.region.ShardKey.regionId(shardKey) + ":"
                + dev.weft.engine.region.ShardKey.shardIndex(shardKey));
    }

    public static final class WrongOwnerException extends IllegalStateException {
        WrongOwnerException(String msg) {
            super(msg);
        }
    }
}
