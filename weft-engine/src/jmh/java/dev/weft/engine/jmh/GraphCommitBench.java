package dev.weft.engine.jmh;

import dev.weft.api.graph.GraphDefinition;
import dev.weft.api.graph.GraphTicker;
import dev.weft.engine.graph.GraphScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * WS-8: graph compute dispatch + commit routing (RFC-0001 §5.2) — 8 graphs
 * emit 64 block writes each; one op is computeAll (parallel compute, sorted
 * deterministic commit log) plus groupByChunk (the routing step the COMMIT
 * phase consumes).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class GraphCommitBench {

    private static final int GRAPHS = 8;
    private static final int WRITES_PER_GRAPH = 64;

    private GraphScheduler graphs;
    private ExecutorService pool;
    private long tick;

    @Setup
    public void setUp() {
        pool = Executors.newWorkStealingPool(4);
        graphs = new GraphScheduler((graph, t) -> null);
        for (int g = 0; g < GRAPHS; g++) {
            final int graphIndex = g;
            String graphId = "bench:graph" + g;
            GraphTicker ticker = (snapshot, commits) -> {
                // Writes spread over 16 chunk columns per graph.
                for (int i = 0; i < WRITES_PER_GRAPH; i++) {
                    commits.setBlock(i * 16, 64, graphIndex * 16, 1L);
                }
            };
            graphs.register(new GraphDefinition() {
                @Override
                public String graphId() {
                    return graphId;
                }

                @Override
                public Set<Long> interestChunks() {
                    return Set.of();
                }

                @Override
                public GraphTicker ticker() {
                    return ticker;
                }
            });
        }
    }

    @TearDown
    public void tearDown() {
        pool.shutdownNow();
    }

    @Benchmark
    public int computeAndRoute() throws InterruptedException {
        var commits = graphs.computeAll(pool, ++tick);
        return GraphScheduler.groupByChunk(commits).size() + commits.size();
    }
}
