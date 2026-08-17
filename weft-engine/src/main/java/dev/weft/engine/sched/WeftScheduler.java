package dev.weft.engine.sched;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.engine.mail.Mailbox;
import dev.weft.engine.mail.Message;
import dev.weft.engine.region.ChunkKey;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    private final RegionManager regions;
    private final GraphScheduler graphs;
    private final Hooks hooks;
    private final Mailbox<Message> globalInbox = new Mailbox<>();
    private final Map<TickPhase, Long> lastPhaseNanos = new EnumMap<>(TickPhase.class);
    private long currentTick;

    public WeftScheduler(int parallelism, RegionManager regions, GraphScheduler graphs, Hooks hooks) {
        this.pool = Executors.newWorkStealingPool(parallelism);
        this.regions = regions;
        this.graphs = graphs;
        this.hooks = hooks;
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
        List<GraphScheduler.CommitOp> commits = new ArrayList<>();
        long t1 = System.nanoTime();
        {
            List<Future<?>> regionFutures = new ArrayList<>();
            for (Region region : regions.all()) {
                regionFutures.add(pool.submit(() -> {
                    ThreadContext.enter(ThreadContext.Kind.REGION, region.id());
                    try {
                        for (Region.Tickable t : tickablesOf(region)) {
                            t.tick(region, tick);
                        }
                    } finally {
                        ThreadContext.exit();
                    }
                }));
            }
            // Graph computes share the pool and the phase (RFC §4.3/§5.2).
            commits.addAll(graphs.computeAll(pool, tick));
            awaitAll(regionFutures);
        }
        lastPhaseNanos.put(TickPhase.REGION, System.nanoTime() - t1);

        // Phase 2 — MAIL: each region drains its mailbox on a worker.
        timedParallelPerRegion(TickPhase.MAIL, region -> {
            for (Message m : region.mailbox().drain()) {
                if (m instanceof Message.Task task) {
                    task.action().run();
                }
                // EntityHandoff / BlockWrite / GraphTopologyDelta are bound by
                // the loader adapter; the engine only guarantees delivery here.
            }
        });

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
