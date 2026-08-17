package dev.weft.engine.guard;

import java.util.concurrent.atomic.AtomicLong;

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

    private WeftGuards() {}

    public static void setMode(Mode m) {
        mode = m;
    }

    public static long tripCount() {
        return trips.get();
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
        if (mode == Mode.DEGRADE) {
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
        if (mode == Mode.DEGRADE) {
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
