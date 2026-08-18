package dev.weft.neoforge.regiontick;

import dev.weft.engine.region.ChunkKey;
import dev.weft.engine.shard.ChunkColoring;

import java.util.concurrent.atomic.LongAdder;

/**
 * The fail-loud half of RFC-0008's safety argument (§3 point 5). The chunk
 * colouring <em>claims</em> that a sharded block entity only ever touches its
 * own chunk; this enforces the claim instead of trusting it.
 *
 * <p><b>What actually constitutes a violation.</b> The domain is not "this
 * chunk only" — that would be stricter than the hazard and would flag
 * perfectly safe, extremely common access. A hopper sitting at a chunk edge
 * legitimately reaches one block into the <em>adjacent</em> chunk, and by the
 * colouring an adjacent chunk always has a different colour, so it is running
 * in a different pass and is provably not concurrent. The only chunks that
 * execute at the same time are <em>other chunks of the same colour</em>, and
 * those are exactly what a shard must never touch. So a trip means: this
 * access could have raced a concurrently-executing chunk — a ticker whose
 * reach exceeded {@link ChunkColoring#MIN_BLOCK_GAP} and therefore needs a
 * {@link WideReachBlockEntities} entry. That is a classification bug, not an
 * acceptable race, so it is counted with forensics and (in DEV) thrown.
 *
 * <p>Cost when sharding is off: one {@link ThreadLocal} read returning the
 * sentinel, and nothing else. The seam is deliberately placed on block-entity
 * lookup rather than block-state reads — {@code getBlockState} is far hotter
 * and a torn read there is what the colouring itself rules out, whereas
 * cross-chunk <em>container</em> access is precisely the hopper-shaped hazard
 * this increment must not get wrong.
 */
public final class ShardDomain {

    private ShardDomain() {}

    /** No shard domain on this thread (server thread, or sharding off). */
    private static final long NONE = Long.MIN_VALUE;

    private static final ThreadLocal<Long> DOMAIN = ThreadLocal.withInitial(() -> NONE);

    private static final LongAdder trips = new LongAdder();
    private static volatile String lastTrip = "";
    private static volatile boolean throwOnTrip;

    static void enter(long chunkKey) {
        DOMAIN.set(chunkKey);
    }

    static void exit() {
        DOMAIN.set(NONE);
    }

    /** DEV-mode strictness: throw on a trip rather than only counting it. */
    public static void setThrowOnTrip(boolean value) {
        throwOnTrip = value;
    }

    /**
     * Assert that accessing {@code (blockX, blockZ)} cannot race a
     * concurrently-executing shard. No-op unless a shard task is running on
     * this thread; safe (and silent) for the shard's own chunk and for any
     * chunk of a different colour — see the class doc for why that is the
     * exact condition rather than a looser or stricter one.
     */
    public static void check(int blockX, int blockZ, String what) {
        long domain = DOMAIN.get();
        if (domain == NONE) {
            return;
        }
        long target = ChunkKey.fromBlock(blockX, blockZ);
        if (target == domain) {
            return;
        }
        if (ChunkColoring.ofKey(target) != ChunkColoring.ofKey(domain)) {
            return; // different colour: a different pass, provably not concurrent
        }
        trips.increment();
        String message = String.format(
                "shard domain violation: %s at block (%d,%d) in chunk (%d,%d) accessed from a "
                        + "shard owning chunk (%d,%d) - same colour, so those chunks run "
                        + "CONCURRENTLY and this access can race. The ticker's reach exceeds the "
                        + "%d-block colouring gap, so its type needs a WideReachBlockEntities "
                        + "entry (RFC-0008 §3)",
                what, blockX, blockZ, ChunkKey.x(target), ChunkKey.z(target),
                ChunkKey.x(domain), ChunkKey.z(domain), ChunkColoring.MIN_BLOCK_GAP);
        lastTrip = message;
        if (throwOnTrip) {
            throw new IllegalStateException(message);
        }
    }

    /** Out-of-domain accesses observed since boot (gated to 0 by the E2 gate). */
    public static long trips() {
        return trips.sum();
    }

    /** Forensics for the most recent trip, or empty. */
    public static String lastTrip() {
        return lastTrip;
    }
}
