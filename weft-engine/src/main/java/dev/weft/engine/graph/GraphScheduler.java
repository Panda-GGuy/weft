package dev.weft.engine.graph;

import dev.weft.api.graph.CommitLog;
import dev.weft.api.graph.GraphDefinition;
import dev.weft.api.graph.WorldSnapshot;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.engine.region.ChunkKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Runs registered graphs' compute steps in parallel during the REGION phase
 * (they read only the settled pre-tick snapshot), then hands their commit
 * logs to the pipeline for the COMMIT phase (RFC-0001 §5.2).
 */
public final class GraphScheduler {

    /** Loader-side factory producing a snapshot scoped to a graph's interest set. */
    @FunctionalInterface
    public interface SnapshotProvider {
        WorldSnapshot snapshotFor(GraphDefinition graph, long tick);
    }

    /** One world write, routed at commit time to the region owning its chunk. */
    public record CommitOp(String graphId, long targetChunk, Runnable apply) {}

    private final Map<String, GraphDefinition> graphs = new ConcurrentHashMap<>();
    private final SnapshotProvider snapshots;

    public GraphScheduler(SnapshotProvider snapshots) {
        this.snapshots = snapshots;
    }

    public void register(GraphDefinition graph) {
        if (graphs.putIfAbsent(graph.graphId(), graph) != null) {
            throw new IllegalArgumentException("Duplicate graph id: " + graph.graphId());
        }
    }

    public void unregister(String graphId) {
        graphs.remove(graphId);
    }

    /**
     * Submit all graph computes to {@code pool}; await completion; return
     * every emitted commit op sorted by (graphId, emission order) so the
     * commit phase is deterministic regardless of compute finishing order.
     */
    public List<CommitOp> computeAll(ExecutorService pool, long tick) throws InterruptedException {
        List<Future<List<CommitOp>>> futures = new ArrayList<>();
        // Deterministic submission order (map iteration order is not).
        List<GraphDefinition> ordered = new ArrayList<>(graphs.values());
        ordered.sort(Comparator.comparing(GraphDefinition::graphId));

        for (GraphDefinition graph : ordered) {
            futures.add(pool.submit(() -> {
                ThreadContext.enter(ThreadContext.Kind.GRAPH, graph.graphId().hashCode());
                try {
                    RecordingCommitLog log = new RecordingCommitLog(graph.graphId());
                    graph.ticker().tick(snapshots.snapshotFor(graph, tick), log);
                    return log.ops();
                } finally {
                    ThreadContext.exit();
                }
            }));
        }

        List<CommitOp> all = new ArrayList<>();
        for (Future<List<CommitOp>> f : futures) {
            try {
                all.addAll(f.get());
            } catch (java.util.concurrent.ExecutionException e) {
                // A failing graph must not take down the tick: isolate, report.
                // (Telemetry hook lands here; for now, propagate as unchecked.)
                throw new IllegalStateException("Graph compute failed", e.getCause());
            }
        }
        all.sort(Comparator.comparing(CommitOp::graphId));
        return all;
    }

    /** Group commit ops by owning chunk's region key for parallel-per-region apply. */
    public static Map<Long, List<CommitOp>> groupByChunk(List<CommitOp> ops) {
        Map<Long, List<CommitOp>> byChunk = new HashMap<>();
        for (CommitOp op : ops) {
            byChunk.computeIfAbsent(op.targetChunk(), k -> new ArrayList<>()).add(op);
        }
        return byChunk;
    }

    /**
     * Engine-side CommitLog: records ops with their target chunk; the
     * NeoForge adapter supplies the real world-mutation runnables.
     */
    private static final class RecordingCommitLog implements CommitLog {
        private final String graphId;
        private final List<CommitOp> ops = new ArrayList<>();
        private final List<Runnable> deferred = new ArrayList<>();
        private long nextWriteId = 1;

        RecordingCommitLog(String graphId) {
            this.graphId = graphId;
        }

        List<CommitOp> ops() {
            return ops;
        }

        @Override
        public void setBlock(int x, int y, int z, long blockStateHandle) {
            ops.add(new CommitOp(graphId, ChunkKey.fromBlock(x, z), () -> {
                // Bound by the loader adapter; engine-level no-op placeholder.
            }));
        }

        @Override
        public long insertItemConditional(int x, int y, int z, int slot, long itemHandle, int count) {
            long id = nextWriteId++;
            ops.add(new CommitOp(graphId, ChunkKey.fromBlock(x, z), () -> {
                // Loader adapter performs compare-against-snapshot insert;
                // rejection is mailed back to the graph (RFC §5.2).
            }));
            return id;
        }

        @Override
        public void deferToNextTick(Runnable graphLocalAction) {
            deferred.add(graphLocalAction);
        }
    }
}
