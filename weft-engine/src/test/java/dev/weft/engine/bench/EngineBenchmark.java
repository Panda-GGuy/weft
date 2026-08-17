package dev.weft.engine.bench;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.region.ChunkKey;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.engine.telemetry.RegionizabilityAnalyzer;
import dev.weft.engine.telemetry.ReportFormatter;
import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.engine.telemetry.TickSample;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * Engine performance benchmark (plain harness, no JMH dependency). Run with:
 *
 * <pre>./gradlew :weft-engine:benchmark</pre>
 *
 * Four questions, each tied to a claim we make elsewhere:
 * <ol>
 * <li><b>Profiler hook cycle</b> — what one entity/BE tick pays when the P0
 *     profiler is recording (the mixin HEAD/RETURN pattern: two
 *     {@code System.nanoTime()}, a deque push/poll, a sample append).
 *     Claim under test: "negligible overhead" (README).</li>
 * <li><b>Report generation</b> — {@code /weft report} cost over a full
 *     window of a busy pack (analyze + format 200k samples).</li>
 * <li><b>Pipeline overhead</b> — one {@link WeftScheduler#tick()} with
 *     empty regions: the fixed price of the seven-phase machinery that
 *     P1 telemetry mode adds to every vanilla tick.</li>
 * <li><b>Pipeline scaling</b> — synthetic region work at parallelism 1 vs 8:
 *     does the REGION phase actually parallelize (RFC-0001 G1)?</li>
 * </ol>
 *
 * Methodology: fixed warmup rounds, then median of measured rounds. Results
 * accumulate into a blackhole printed at the end so the JIT cannot elide the
 * measured work. Absolute numbers are machine-dependent; the interesting
 * outputs are the per-op costs relative to the 50 ms tick budget and the
 * scaling ratio.
 */
public final class EngineBenchmark {

    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASURE_ROUNDS = 7;

    private static long blackhole;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Weft engine benchmark ===");
        System.out.printf("JVM: %s | cores: %d%n%n",
                System.getProperty("java.version"), Runtime.getRuntime().availableProcessors());

        benchProfilerHookCycle();
        benchReportGeneration();
        benchPipelineOverhead();
        benchPipelineScaling();

        System.out.printf("%n(blackhole %d)%n", blackhole);
    }

    // --- 1. profiler hook cycle -------------------------------------------

    private static void benchProfilerHookCycle() {
        final int OPS = 2_000_000;
        final int SAMPLES_PER_TICK = 2_000;
        TickProfiler profiler = new TickProfiler(100);
        ArrayDeque<Long> stack = new ArrayDeque<>();

        double nsPerOp = medianOf(() -> {
            long t0 = System.nanoTime();
            long tick = 0;
            for (int i = 0; i < OPS; i++) {
                if (i % SAMPLES_PER_TICK == 0) {
                    profiler.tickBoundary(++tick, System.nanoTime());
                }
                // Exactly the mixin hook pattern: HEAD push, RETURN pop+record.
                stack.push(System.nanoTime());
                Long start = stack.poll();
                if (start != null) {
                    profiler.record(TickSample.Source.ENTITY, "bench:entity",
                            ChunkKey.pack(i & 31, (i >> 5) & 31), System.nanoTime() - start);
                }
            }
            blackhole += profiler.snapshotWindow().size();
            return (System.nanoTime() - t0) / (double) OPS;
        });

        double perTickUs = nsPerOp * SAMPLES_PER_TICK / 1_000.0;
        System.out.printf("1. Profiler hook cycle:   %6.0f ns/op  ->  %.0f us/tick at %d tickables (%.3f%% of 50ms budget)%n",
                nsPerOp, perTickUs, SAMPLES_PER_TICK, 100.0 * perTickUs / 50_000.0);
    }

    // --- 2. report generation ---------------------------------------------

    private static void benchReportGeneration() {
        // Synthetic busy pack: 100-tick window, 2000 samples/tick, three
        // spatial clusters (bases) plus 5% global cost.
        SplittableRandom rng = new SplittableRandom(42);
        List<TickSample> all = new ArrayList<>(200_000);
        String[] types = {"mod:machine", "mod:conveyor", "minecraft:zombie",
                "minecraft:item", "mod:solar", "minecraft:villager"};
        for (int i = 0; i < 200_000; i++) {
            if (i % 20 == 0) {
                all.add(new TickSample(TickSample.Source.GLOBAL, "global:misc",
                        TickSample.NO_CHUNK, rng.nextLong(10_000, 80_000)));
            } else {
                int cluster = i % 3;
                int cx = cluster * 1000 + rng.nextInt(20);
                int cz = cluster * 1000 + rng.nextInt(20);
                all.add(new TickSample(TickSample.Source.BLOCK_ENTITY,
                        types[rng.nextInt(types.length)],
                        ChunkKey.pack(cx, cz), rng.nextLong(5_000, 150_000)));
            }
        }

        double msPerReport = medianOf(() -> {
            long t0 = System.nanoTime();
            RegionizabilityAnalyzer analyzer =
                    new RegionizabilityAnalyzer(8, new int[]{2, 4, 8, 16}, 12);
            String report = ReportFormatter.format(analyzer.analyze(all), 100);
            blackhole += report.length();
            return (System.nanoTime() - t0) / 1e6;
        });

        System.out.printf("2. Report generation:     %6.1f ms per /weft report (200k-sample window)%n", msPerReport);
    }

    // --- 3. pipeline overhead ----------------------------------------------

    private static void benchPipelineOverhead() throws Exception {
        RegionManager rm = new RegionManager(2, 42L);
        for (int c = 0; c < 8; c++) {
            rm.addChunk(c * 100, 0); // 8 far-apart single-chunk regions
        }
        final int TICKS = 5_000;
        try (WeftScheduler sched = new WeftScheduler(
                Math.max(2, Runtime.getRuntime().availableProcessors() - 2),
                rm, new GraphScheduler((g, t) -> null), new WeftScheduler.Hooks() {})) {

            double usPerTick = medianOf(() -> {
                long t0 = System.nanoTime();
                try {
                    for (int i = 0; i < TICKS; i++) {
                        sched.tick();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                blackhole += sched.currentTick();
                return (System.nanoTime() - t0) / 1_000.0 / TICKS;
            });

            System.out.printf("3. Pipeline overhead:     %6.1f us/tick empty 7-phase pipeline, 8 regions (%.3f%% of 50ms budget)%n",
                    usPerTick, 100.0 * usPerTick / 50_000.0);
        }
    }

    // --- 4. pipeline scaling -----------------------------------------------

    private static void benchPipelineScaling() throws Exception {
        // 8 regions, each with ~1ms of real work per tick. Serial cost ~8ms.
        final int REGION_COUNT = 8;
        final int TICKS = 200;
        double serial = scalingRun(1, REGION_COUNT, TICKS);
        double parallel = scalingRun(REGION_COUNT, REGION_COUNT, TICKS);
        System.out.printf("4. Pipeline scaling:      %6.2f ms/tick serial vs %.2f ms/tick at %d workers  ->  %.1fx speedup (%d regions x ~1ms work)%n",
                serial, parallel, REGION_COUNT, serial / parallel, REGION_COUNT);
    }

    private static double scalingRun(int parallelism, int regionCount, int ticks) throws Exception {
        RegionManager rm = new RegionManager(2, 42L);
        for (int c = 0; c < regionCount; c++) {
            rm.addChunk(c * 100, 0);
        }
        LongAdder sink = new LongAdder();
        for (Region region : rm.all()) {
            region.addTickable((r, tick) -> {
                // ~1ms of deterministic CPU work (not sleep - real contention).
                long acc = r.id();
                long until = System.nanoTime() + 1_000_000;
                while (System.nanoTime() < until) {
                    acc = acc * 6364136223846793005L + 1442695040888963407L;
                }
                sink.add(acc == 0 ? 1 : 2);
            });
        }
        try (WeftScheduler sched = new WeftScheduler(parallelism, rm,
                new GraphScheduler((g, t) -> null), new WeftScheduler.Hooks() {})) {
            double result = medianOf(() -> {
                long t0 = System.nanoTime();
                try {
                    for (int i = 0; i < ticks; i++) {
                        sched.tick();
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return (System.nanoTime() - t0) / 1e6 / ticks;
            });
            blackhole += sink.sum();
            return result;
        }
    }

    // --- harness -------------------------------------------------------------

    private static double medianOf(java.util.function.DoubleSupplier round) {
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            round.getAsDouble();
        }
        double[] results = new double[MEASURE_ROUNDS];
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            results[i] = round.getAsDouble();
        }
        java.util.Arrays.sort(results);
        return results[MEASURE_ROUNDS / 2];
    }

    private EngineBenchmark() {}
}
