package dev.weft.engine.jmh;

import dev.weft.engine.graph.GraphScheduler;
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
 * WS-8: the seven-phase pipeline's per-tick scheduling cost (RFC-0001 §4.3)
 * — region task submission, phase barriers, mailbox drains — over 16 regions
 * on 4 workers. {@code emptyRegions} is the fixed machinery price P1
 * telemetry mode adds to every vanilla tick; {@code tinyWork} includes the
 * dispatch of one trivial tickable per region.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class PipelineTickBench {

    private WeftScheduler emptyScheduler;
    private WeftScheduler workScheduler;
    private final LongAdder sink = new LongAdder();

    private static RegionManager sixteenIslands() {
        RegionManager rm = new RegionManager(2, 42L);
        for (int i = 0; i < 16; i++) {
            rm.addChunk(i * 100, 0);
        }
        return rm;
    }

    @Setup
    public void setUp() {
        emptyScheduler = new WeftScheduler(4, sixteenIslands(),
                new GraphScheduler((graph, tick) -> null), new WeftScheduler.Hooks() {});
        RegionManager withWork = sixteenIslands();
        withWork.all().forEach(region ->
                region.addTickable((r, tick) -> sink.increment()));
        workScheduler = new WeftScheduler(4, withWork,
                new GraphScheduler((graph, tick) -> null), new WeftScheduler.Hooks() {});
    }

    @TearDown
    public void tearDown() {
        emptyScheduler.close();
        workScheduler.close();
    }

    @Benchmark
    public long emptyRegions() throws InterruptedException {
        emptyScheduler.tick();
        return emptyScheduler.currentTick();
    }

    @Benchmark
    public long tinyWork() throws InterruptedException {
        workScheduler.tick();
        return workScheduler.currentTick() + sink.sum();
    }
}
