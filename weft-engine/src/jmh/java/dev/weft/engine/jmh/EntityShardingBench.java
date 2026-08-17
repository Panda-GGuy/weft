package dev.weft.engine.jmh;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * WS-10 (RFC-0004): the single-region world that flatlines region-level
 * parallelism at 1.00x — 2000 tickables (the profiled solo-play shape) in
 * ONE region. {@code serialOneRegion} is today's path;
 * {@code shardedOneRegion} fans the same work over shards on 8 workers.
 * The gap between the two is the WS-10 acceptance signal (§4 throughput),
 * tracked nightly by the WS-8 gate.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class EntityShardingBench {

    private static final int TICKABLES = 2_000;
    // ~1us of serial-dependency arithmetic per tickable (a cheap mob's AI
    // slice). rotate+xor per round resists the affine folding a pure LCG
    // invites, so the work scales with ROUNDS instead of being JIT-collapsed.
    private static final int WORK_ROUNDS = 800;

    private WeftScheduler serialScheduler;
    private WeftScheduler shardedScheduler;
    private final LongAdder sink = new LongAdder();

    private WeftScheduler build(boolean sharded) {
        RegionManager rm = new RegionManager(2, 42L);
        Region region = rm.addChunk(0, 0);
        for (int i = 0; i < TICKABLES; i++) {
            final long salt = i;
            region.addTickable((r, tick) -> {
                long acc = salt ^ tick;
                for (int round = 0; round < WORK_ROUNDS; round++) {
                    acc = Long.rotateLeft(acc * 6364136223846793005L, 7) ^ round;
                }
                sink.add(acc == 0 ? 1 : 2);
            });
        }
        WeftScheduler scheduler = new WeftScheduler(8, rm,
                new GraphScheduler((g, t) -> null), new WeftScheduler.Hooks() {});
        if (sharded) {
            scheduler.setEntitySharding(true, 64);
        }
        return scheduler;
    }

    @Setup
    public void setUp() {
        serialScheduler = build(false);
        shardedScheduler = build(true);
    }

    @TearDown
    public void tearDown() {
        serialScheduler.close();
        shardedScheduler.close();
    }

    @Benchmark
    public long serialOneRegion() throws InterruptedException {
        serialScheduler.tick();
        return serialScheduler.currentTick() + sink.sum();
    }

    @Benchmark
    public long shardedOneRegion() throws InterruptedException {
        shardedScheduler.tick();
        return shardedScheduler.currentTick() + sink.sum();
    }
}
