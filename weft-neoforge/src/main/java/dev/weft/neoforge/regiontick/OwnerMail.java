package dev.weft.neoforge.regiontick;

import dev.weft.engine.mail.Message;
import dev.weft.engine.region.Region;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.atomic.LongAdder;

/**
 * P2 increment 6 (RFC-0007 §3): owner-mail rerouting. Positionally-owned
 * async work (WS-2 path fills) is delivered to the owning region's own
 * mailbox and drained by that region's bucket at the head of its section
 * run — replacing "the parked main thread's global inbox <em>is</em> owner
 * delivery" (RFC-0006 §2) with delivery that stays correct once regions stop
 * synchronizing on the server thread.
 *
 * <p><b>Where routing happens — and on which thread.</b> {@link #runOwned}
 * is server-thread-only: async producers (path workers) still hand their
 * results to the global inbox, whose INGEST drain calls this as the task
 * body. That keeps the topology lookup on the only thread that may read the
 * region managers (their maps are plain collections, mutated between ticks
 * and at INGEST — an off-thread lookup would be a data race, RFC-0007 §3.1),
 * and it costs nothing in latency: global INGEST at tick head, region drain
 * at bucket head, applied the same tick — identical arrival timing to the
 * pre-increment model. Off-thread routing is the increment-7/v2 step, taken
 * only when posts must resolve without the server thread in the loop.
 *
 * <p>Delivery contract (RFC-0007 §3.2): mail posted before a section began
 * is applied under the owner's REGION context before any of that owner's
 * simulation in the section; unmapped targets and inactive routing run the
 * task inline at INGEST — bit-for-bit today's behavior.
 */
public final class OwnerMail {

    private OwnerMail() {}

    private static final LongAdder routedToRegion = new LongAdder();
    private static final LongAdder inlineFallback = new LongAdder();
    private static final LongAdder drainedTasks = new LongAdder();
    private static final LongAdder flushedTasks = new LongAdder();

    /**
     * Run {@code action} under its positional owner: routed to the owning
     * region's mailbox when {@code ownerMailRouting} is active and the
     * position maps to a real region, else inline on the calling thread
     * (which must be the server thread — see class doc).
     */
    public static void runOwned(ServerLevel level, BlockPos pos, Runnable action) {
        if (RegionizedTicking.isMailRouted()) {
            Region region = RegionTopology.managerFor(level)
                    .regionAtBlock(pos.getX(), pos.getZ());
            if (region != null) {
                region.mailbox().post(new Message.Task(action));
                routedToRegion.increment();
                return;
            }
            inlineFallback.increment();
        }
        action.run();
    }

    /**
     * Drain one region's mailbox at the head of its bucket run. Called under
     * the region's REGION thread context, on whichever thread runs the
     * bucket (a pool worker under parallel mode, the server thread under
     * partitioned-serial) — before any of the bucket's simulation units.
     */
    static void drainInto(Region region) {
        if (region.mailbox().isEmpty()) {
            return;
        }
        for (Message m : region.mailbox().drain()) {
            if (m instanceof Message.Task task) {
                task.action().run();
                drainedTasks.increment();
            }
        }
    }

    /**
     * Deactivation safety net (RFC-0007 §3.3 hazard 5): when routing turns
     * off with mail still queued, every region mailbox is drained inline on
     * the server thread, once, so nothing is stranded behind a flag that no
     * bucket will ever drain again. Called from the flag transitions in
     * {@link RegionizedTicking} (server thread).
     */
    static void flushAllInline() {
        RegionTopology.forEachRegion(region -> {
            for (Message m : region.mailbox().drain()) {
                if (m instanceof Message.Task task) {
                    task.action().run();
                    flushedTasks.increment();
                }
            }
        });
    }

    /** Tasks delivered through a region mailbox since boot (engagement probe). */
    public static long routedToRegion() {
        return routedToRegion.sum();
    }

    /** Routing-active posts that fell back inline (unmapped target) since boot. */
    public static long inlineFallback() {
        return inlineFallback.sum();
    }

    /** Tasks executed by bucket-head drains since boot. */
    public static long drainedTasks() {
        return drainedTasks.sum();
    }

    /** Tasks recovered by a deactivation flush since boot. */
    public static long flushedTasks() {
        return flushedTasks.sum();
    }

    /** One-line routing summary for {@code /weft status} (R5). */
    public static String summary() {
        return String.format("owner mail: %d routed, %d drained, %d inline fallback, %d flushed",
                routedToRegion.sum(), drainedTasks.sum(), inlineFallback.sum(), flushedTasks.sum());
    }
}
