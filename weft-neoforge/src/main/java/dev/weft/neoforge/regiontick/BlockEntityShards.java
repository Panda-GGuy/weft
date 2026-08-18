package dev.weft.neoforge.regiontick;

import dev.weft.engine.region.ShardKey;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.engine.shard.ChunkColoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * WS-10 activation against real ticking (RFC-0008 §3): runs one region's
 * captured block-entity units as barriered, chunk-coloured passes so
 * concurrently-executing chunks are always at least
 * {@link ChunkColoring#MIN_BLOCK_GAP} blocks apart — further than a
 * short-reach block entity can touch. That separation is what makes
 * intra-region parallelism safe <em>without</em> the {@code EntityEffects}
 * log: a sharded unit only mutates its own chunk's state, so there is no
 * cross-shard effect to merge.
 *
 * <p>Execution shape per region, per section:
 * <pre>
 *   for colour in 0..3:        (barriered between colours)
 *       fan that colour's chunks across workers, SHARD contexts
 *   then: wide-reach units, serially, on the region's own thread
 * </pre>
 *
 * <p>Order guarantees: within one chunk, vanilla's capture order is preserved
 * exactly; across chunks of one colour, order cannot matter (they are unable
 * to interact); across colours, order is the fixed colour sequence —
 * deterministic and reproducible, but not vanilla's list order for block
 * entities in adjacent chunks. That is RFC-0008's declared class-E2
 * divergence, and the conservation gate is what keeps it honest.
 */
final class BlockEntityShards {

    private BlockEntityShards() {}

    private static final LongAdder shardedSections = new LongAdder();
    private static final LongAdder shardedUnits = new LongAdder();
    private static final LongAdder wideReachUnits = new LongAdder();
    private static final LongAdder shardPasses = new LongAdder();
    /** Thread names of the most recent section's shard tasks (E2 fan-out probe). */
    private static volatile String[] lastShardThreads = new String[0];
    private static volatile int lastMaxConcurrentShards;

    /**
     * Run {@code units} as coloured passes plus a serial wide-reach tail.
     * Called on the region's bucket thread under its REGION context; each
     * shard task enters a SHARD context for {@code (regionId, colour)}.
     */
    static void runColoured(WeftScheduler engine, long regionId, List<BeTickUnit> units) {
        // Group by colour, then by chunk: a chunk's units stay together and in
        // capture order, which is vanilla's order within that chunk.
        List<Map<Long, List<Runnable>>> byColour = new ArrayList<>(ChunkColoring.COLORS);
        for (int c = 0; c < ChunkColoring.COLORS; c++) {
            byColour.add(new TreeMap<>());
        }
        List<Runnable> wideReach = new ArrayList<>();
        for (BeTickUnit unit : units) {
            if (unit.wideReach()) {
                wideReach.add(unit.unit());
                continue;
            }
            byColour.get(ChunkColoring.ofKey(unit.chunkKey()))
                    .computeIfAbsent(unit.chunkKey(), k -> new ArrayList<>())
                    .add(unit.unit());
        }

        ConcurrentLinkedQueue<String> threads = new ConcurrentLinkedQueue<>();
        int maxConcurrent = 0;
        long sharded = 0;
        for (int colour = 0; colour < ChunkColoring.COLORS; colour++) {
            Map<Long, List<Runnable>> chunks = byColour.get(colour);
            if (chunks.isEmpty()) {
                continue;
            }
            long shardKey = ShardKey.pack(regionId, colour);
            List<WeftScheduler.OwnedSection> tasks = new ArrayList<>(chunks.size());
            for (Map.Entry<Long, List<Runnable>> chunk : chunks.entrySet()) {
                long chunkKey = chunk.getKey();
                List<Runnable> chunkUnits = chunk.getValue();
                sharded += chunkUnits.size();
                tasks.add(new WeftScheduler.OwnedSection(shardKey, () -> {
                    threads.add(Thread.currentThread().getName());
                    // Shard workers need the privileges the increment-5 safety
                    // mixins gate on — above all the lock-free chunk read path:
                    // the thread that would service getChunk's mainThreadProcessor
                    // is parked at this pass's barrier, so the vanilla route
                    // would deadlock (RFC-0006 hazard 1).
                    ShardDomain.enter(chunkKey);
                    ParallelAccess.enterWorker();
                    try {
                        chunkUnits.forEach(Runnable::run);
                    } finally {
                        ParallelAccess.exitWorker();
                        ShardDomain.exit();
                    }
                }));
            }
            if (tasks.size() >= 2) {
                maxConcurrent = Math.max(maxConcurrent, tasks.size());
                shardPasses.increment();
                // One barriered pass. Every task in it is separated from every
                // other by the colouring, so the fan-out is safe by
                // construction rather than by locking.
                engine.runOwnedParallel(tasks, dev.weft.engine.guard.ThreadContext.Kind.SHARD);
            } else {
                // A single chunk of this colour: nothing to parallelize, and
                // the serial path keeps the same shard context and domain guard.
                for (WeftScheduler.OwnedSection task : tasks) {
                    engine.runOwnedAs(dev.weft.engine.guard.ThreadContext.Kind.SHARD,
                            task.ownerId(), task.work());
                }
            }
        }

        if (!wideReach.isEmpty()) {
            wideReachUnits.add(wideReach.size());
            wideReach.forEach(Runnable::run);
        }
        shardedUnits.add(sharded);
        shardedSections.increment();
        lastMaxConcurrentShards = maxConcurrent;
        lastShardThreads = threads.toArray(new String[0]);
    }

    static long shardedSections() {
        return shardedSections.sum();
    }

    static long shardedUnits() {
        return shardedUnits.sum();
    }

    static long wideReachUnits() {
        return wideReachUnits.sum();
    }

    static long shardPasses() {
        return shardPasses.sum();
    }

    static int lastMaxConcurrentShards() {
        return lastMaxConcurrentShards;
    }

    static String[] lastShardThreads() {
        return lastShardThreads.clone();
    }

    static void reset() {
        lastShardThreads = new String[0];
        lastMaxConcurrentShards = 0;
    }

    static String summary() {
        return String.format(
                "be shards: %d sections, %d passes, %d units sharded, %d wide-reach serial, "
                        + "last max %d concurrent chunks, %d domain trips",
                shardedSections.sum(), shardPasses.sum(), shardedUnits.sum(),
                wideReachUnits.sum(), lastMaxConcurrentShards, ShardDomain.trips());
    }
}
