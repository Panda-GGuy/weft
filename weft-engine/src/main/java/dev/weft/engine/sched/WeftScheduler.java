package dev.weft.engine.sched;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.engine.mail.Mailbox;
import dev.weft.engine.mail.Message;
import dev.weft.engine.region.ChunkKey;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.region.ShardKey;
import dev.weft.engine.shard.EntityEffects;
import dev.weft.engine.shard.ShardContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * The phased tick pipeline (RFC-0001 §4.3). One call to {@link #tick} is one
 * server tick. Phases are separated by hard barriers (we await every phase's
 * futures before starting the next); work within a phase runs on the
 * work-stealing pool.
 *
 * <p>This is the engine skeleton: the loader module supplies the actual
 * simulation content as {@link Region.Tickable}s, the legacy runner, the
 * global runner, and the egress runner.
 */
public final class WeftScheduler implements AutoCloseable {

    /** Serialized hook slots the loader fills in. */
    public interface Hooks {
        /** Phase 4: run all Tier-2 (legacy) mod work, single-threaded semantics. */
        default void runLegacy(long tick) {}
        /** Phase 5: global lane. */
        default void runGlobal(long tick) {}
        /** Phase 6: trackers/network/save handoff. */
        default void runEgress(long tick) {}
    }

    private final ExecutorService pool;
    private final int parallelism;
    private final RegionManager regions;
    private final GraphScheduler graphs;
    private final Hooks hooks;
    private final Mailbox<Message> globalInbox = new Mailbox<>();
    private final Map<TickPhase, Long> lastPhaseNanos = new EnumMap<>(TickPhase.class);
    private long currentTick;

    // WS-10 (RFC-0004): intra-region entity sharding. Off by default (R1);
    // when off, the REGION phase is byte-for-byte today's serial-per-region
    // path (R6 zero residue).
    private volatile boolean shardingEnabled;
    private volatile int shardMinBatch = 64;
    private volatile EntityEffects.Applier effectApplier = new EntityEffects.Applier() {};
    private volatile int lastShardedRegions;
    private volatile int lastMaxShards;

    public WeftScheduler(int parallelism, RegionManager regions, GraphScheduler graphs, Hooks hooks) {
        this.pool = Executors.newWorkStealingPool(parallelism);
        this.parallelism = parallelism;
        this.regions = regions;
        this.graphs = graphs;
        this.hooks = hooks;
    }

    /** WS-10 switch (RFC-0004, RFC-0003 R1). Safe to flip between ticks. */
    public void setEntitySharding(boolean enabled, int minBatch) {
        this.shardingEnabled = enabled;
        this.shardMinBatch = Math.max(1, minBatch);
    }

    /** Loader-supplied binding for resolved entity effects (RFC-0004 §2.3). */
    public void setEntityEffectApplier(EntityEffects.Applier applier) {
        this.effectApplier = applier;
    }

    /** Regions that ran more than one shard last tick (R5 status line). */
    public int lastShardedRegions() {
        return lastShardedRegions;
    }

    /** Largest shard fan-out any region used last tick (R5 status line). */
    public int lastMaxShards() {
        return lastMaxShards;
    }

    // P2 increment 1 (RFC-0001 §11): vanilla tick sections run *through* the
    // engine — same thread, same order — so the ownership seam exists before
    // any semantics change. Counted for engagement checks (WS-8 vacuous-run
    // guards) and the R5 status line.
    private final LongAdder ownedSerialSections = new LongAdder();

    /**
     * Run one vanilla tick section under engine ownership: serially, on the
     * calling thread, in vanilla's own iteration order. This is deliberately
     * the degenerate case of the REGION phase — bit-identical to vanilla by
     * construction — establishing the thread-context/guard seam that later
     * P2 increments move into the parallel REGION phase proper.
     *
     * <p>The section runs under a {@link ThreadContext.Kind#REGION} context
     * for {@code regionOwnerId}, so guard-instrumented mutation paths see a
     * real owner instead of {@code NONE}. The context is restored (and the
     * section counted) even when the section throws.
     */
    public void runOwnedSerial(long regionOwnerId, Runnable section) {
        ThreadContext.enter(ThreadContext.Kind.REGION, regionOwnerId);
        try {
            section.run();
        } finally {
            ThreadContext.exit();
            ownedSerialSections.increment();
        }
    }

    /** Sections run through {@link #runOwnedSerial} since boot. */
    public long ownedSerialSections() {
        return ownedSerialSections.sum();
    }

    /** Entry point for cross-thread submissions (network threads, console). */
    public void submit(Message message) {
        globalInbox.post(message);
    }

    /** Post a task to the owner of a block position; runs in its next MAIL drain. */
    public void runOnOwner(int blockX, int blockZ, Runnable task) {
        Region r = regions.regionAtBlock(blockX, blockZ);
        if (r != null) {
            r.mailbox().post(new Message.Task(task));
        } else {
            globalInbox.post(new Message.Task(task));
        }
    }

    public long currentTick() {
        return currentTick;
    }

    public Map<TickPhase, Long> lastPhaseTimings() {
        return lastPhaseNanos;
    }

    /** Execute one full pipeline tick. */
    public void tick() throws InterruptedException {
        long tick = ++currentTick;

        // Phase 0 — INGEST: drain the global inbox, route to owners.
        timed(TickPhase.INGEST, () -> {
            for (Message m : globalInbox.drain()) {
                route(m);
            }
        });

        // Phase 1 — REGION: parallel region ticks + graph computes (overlapped).
        // With WS-10 sharding on, a big region additionally fans its tickables
        // out over shards (RFC-0004 §2.1) — round-robin partition, one serial
        // loop per shard, SHARD thread context, per-shard RNG substream and
        // effect log. Effect logs are applied at the head of MAIL.
        List<GraphScheduler.CommitOp> commits = new ArrayList<>();
        List<RegionEffects> pendingEffects = new ArrayList<>();
        int shardedRegions = 0;
        int maxShards = 1;
        long t1 = System.nanoTime();
        {
            List<Future<?>> regionFutures = new ArrayList<>();
            for (Region region : regions.all()) {
                List<Region.Tickable> tickables = tickablesOf(region);
                int shardCount = shardCountFor(tickables.size());
                if (shardCount <= 1) {
                    regionFutures.add(pool.submit(() -> {
                        ThreadContext.enter(ThreadContext.Kind.REGION, region.id());
                        try {
                            for (Region.Tickable t : tickables) {
                                t.tick(region, tick);
                            }
                        } finally {
                            ThreadContext.exit();
                        }
                    }));
                    continue;
                }
                shardedRegions++;
                maxShards = Math.max(maxShards, shardCount);
                // Pre-split the region's RNG into one child stream per shard,
                // on the coordinator, in shard-index order: single-threaded
                // and deterministic (RFC-0004 §2.4).
                RegionEffects effects = new RegionEffects(region, shardCount);
                pendingEffects.add(effects);
                for (int s = 0; s < shardCount; s++) {
                    final int shardIndex = s;
                    final long shardKey = ShardKey.pack(region.id(), s);
                    final ShardContext ctx = new ShardContext(
                            shardKey, region.random().split(), effects.logs.get(s));
                    regionFutures.add(pool.submit(() -> {
                        ThreadContext.enter(ThreadContext.Kind.SHARD, shardKey);
                        try {
                            for (int i = shardIndex; i < tickables.size(); i += shardCount) {
                                tickables.get(i).tick(region, tick, ctx);
                            }
                        } finally {
                            ThreadContext.exit();
                        }
                    }));
                }
            }
            // Graph computes share the pool and the phase (RFC §4.3/§5.2).
            commits.addAll(graphs.computeAll(pool, tick));
            awaitAll(regionFutures);
        }
        lastPhaseNanos.put(TickPhase.REGION, System.nanoTime() - t1);
        lastShardedRegions = shardedRegions;
        lastMaxShards = maxShards;

        // Phase 2 — MAIL. First the coordinator applies entity effect logs,
        // region by region in id order, each op stream already sorted by
        // (source, seq): deterministic regardless of shard finish order
        // (RFC-0004 §2.3). Then each region drains its mailbox on a worker.
        long t2 = System.nanoTime();
        pendingEffects.sort((a, b) -> Long.compare(a.region.id(), b.region.id()));
        for (RegionEffects effects : pendingEffects) {
            ThreadContext.enter(ThreadContext.Kind.REGION, effects.region.id());
            try {
                EntityEffects.applyAll(effects.logs, effectApplier);
            } finally {
                ThreadContext.exit();
            }
        }
        timedParallelPerRegion(TickPhase.MAIL, region -> {
            for (Message m : region.mailbox().drain()) {
                if (m instanceof Message.Task task) {
                    task.action().run();
                }
                // EntityHandoff / BlockWrite / GraphTopologyDelta are bound by
                // the loader adapter; the engine only guarantees delivery here.
            }
        });
        // Fold the effect-apply time into the MAIL phase number (RFC-0004
        // puts the step alongside MAIL; the seven-phase contract is unchanged).
        lastPhaseNanos.merge(TickPhase.MAIL, System.nanoTime() - t2, Math::max);

        // Phase 3 — COMMIT: apply graph commit logs, parallel across regions,
        // deterministic order within each (already sorted by graphId).
        Map<Long, List<GraphScheduler.CommitOp>> byChunk = GraphScheduler.groupByChunk(commits);
        long t3 = System.nanoTime();
        {
            List<Future<?>> commitFutures = new ArrayList<>();
            for (Map.Entry<Long, List<GraphScheduler.CommitOp>> e : byChunk.entrySet()) {
                Region owner = regions.regionAt(ChunkKey.x(e.getKey()), ChunkKey.z(e.getKey()));
                long ownerId = owner != null ? owner.id() : -1;
                commitFutures.add(pool.submit(() -> {
                    ThreadContext.enter(ThreadContext.Kind.REGION, ownerId);
                    try {
                        e.getValue().forEach(op -> op.apply().run());
                    } finally {
                        ThreadContext.exit();
                    }
                }));
            }
            awaitAll(commitFutures);
        }
        lastPhaseNanos.put(TickPhase.COMMIT, System.nanoTime() - t3);

        // Phase 4 — LEGACY: single-threaded semantics; every worker is parked
        // (we are between barriers; nothing else is submitted).
        timed(TickPhase.LEGACY, () -> {
            ThreadContext.enter(ThreadContext.Kind.LEGACY, 0);
            try {
                hooks.runLegacy(tick);
            } finally {
                ThreadContext.exit();
            }
        });

        // Phase 5 — GLOBAL.
        timed(TickPhase.GLOBAL, () -> {
            ThreadContext.enter(ThreadContext.Kind.GLOBAL, 0);
            try {
                hooks.runGlobal(tick);
            } finally {
                ThreadContext.exit();
            }
        });

        // Phase 6 — EGRESS.
        timed(TickPhase.EGRESS, () -> hooks.runEgress(tick));

        // Between ticks: region topology maintenance (never mid-tick, RFC §4.2).
        regions.recomputeSplits();
    }

    private void route(Message m) {
        if (m instanceof Message.Task task) {
            task.action().run(); // ingest-time tasks run on the coordinator
        } else if (m instanceof Message.BlockWrite bw) {
            Region r = regions.regionAtBlock(bw.x(), bw.z());
            if (r != null) {
                r.mailbox().post(m);
            }
        } else if (m instanceof Message.EntityHandoff eh) {
            Region r = regions.regionAt(ChunkKey.x(eh.toChunk()), ChunkKey.z(eh.toChunk()));
            if (r != null) {
                r.mailbox().post(m);
            }
        }
        // GraphTopologyDelta routing binds in the loader adapter.
    }

    private List<Region.Tickable> tickablesOf(Region region) {
        // Package-private accessor kept engine-internal via reflection-free path:
        return region.tickablesView();
    }

    /**
     * Shards for a region with {@code tickableCount} tickables: 1 (serial,
     * today's exact path) unless sharding is on and the region is big enough
     * to be worth splitting; then one shard per {@code shardMinBatch}
     * tickables, capped at the pool's parallelism (RFC-0004 §2.1).
     */
    private int shardCountFor(int tickableCount) {
        if (!shardingEnabled || tickableCount < shardMinBatch) {
            return 1;
        }
        return Math.min(parallelism, tickableCount / shardMinBatch);
    }

    /** One sharded region's effect logs, in shard-index order. */
    private static final class RegionEffects {
        final Region region;
        final List<EntityEffects.ShardLog> logs;

        RegionEffects(Region region, int shardCount) {
            this.region = region;
            this.logs = new ArrayList<>(shardCount);
            for (int i = 0; i < shardCount; i++) {
                logs.add(new EntityEffects.ShardLog());
            }
        }
    }

    private void timed(TickPhase phase, Runnable r) {
        long start = System.nanoTime();
        r.run();
        lastPhaseNanos.put(phase, System.nanoTime() - start);
    }

    private void timedParallelPerRegion(TickPhase phase, java.util.function.Consumer<Region> perRegion)
            throws InterruptedException {
        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        for (Region region : regions.all()) {
            futures.add(pool.submit(() -> {
                ThreadContext.enter(ThreadContext.Kind.REGION, region.id());
                try {
                    perRegion.accept(region);
                } finally {
                    ThreadContext.exit();
                }
            }));
        }
        awaitAll(futures);
        lastPhaseNanos.put(phase, System.nanoTime() - start);
    }

    private void awaitAll(List<Future<?>> futures) throws InterruptedException {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IllegalStateException("Phase task failed", e.getCause());
            }
        }
    }

    @Override
    public void close() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
