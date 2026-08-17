package dev.weft.engine.telemetry;

import dev.weft.engine.region.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegionizabilityAnalyzerTest {

    private static TickSample entity(String type, int cx, int cz, long nanos) {
        return new TickSample(TickSample.Source.ENTITY, type, ChunkKey.pack(cx, cz), nanos);
    }

    @Test
    void twoDistantBasesFormTwoRegionsAndParallelize() {
        List<TickSample> samples = new ArrayList<>();
        // Base A: two chunks near origin, 6ms total.
        samples.add(entity("mod:quarry", 0, 0, 3_000_000));
        samples.add(entity("mod:quarry", 1, 0, 3_000_000));
        // Base B: far away, 6ms total.
        samples.add(entity("mod:farm", 100, 100, 6_000_000));
        // Global work: 2ms serial.
        samples.add(new TickSample(TickSample.Source.GLOBAL, "global:time",
                TickSample.NO_CHUNK, 2_000_000));

        RegionizabilityAnalyzer analyzer =
                new RegionizabilityAnalyzer(2, new int[]{1, 2, 8}, 10);
        RegionizabilityAnalyzer.Report r = analyzer.analyze(samples);

        assertEquals(14_000_000, r.totalNanos());
        assertEquals(12_000_000, r.spatialNanos());
        assertEquals(2_000_000, r.globalNanos());
        assertEquals(2, r.regions().size(), "two spatially distinct bases -> two regions");

        // 1 worker: no speedup (14 / (2 + 12) = 1.0)
        assertEquals(1.0, r.speedupByWorkers().get(1), 1e-9);
        // 2 workers: regions run concurrently -> 14 / (2 + 6) = 1.75
        assertEquals(1.75, r.speedupByWorkers().get(2), 1e-9);
        // 8 workers: no better than 2 — only two regions exist (Amdahl)
        assertEquals(1.75, r.speedupByWorkers().get(8), 1e-9);
    }

    @Test
    void oneMegaBaseCannotParallelizeAcrossRegions() {
        List<TickSample> samples = new ArrayList<>();
        // One contiguous 5-chunk factory: merge distance keeps it one region.
        for (int x = 0; x < 5; x++) {
            samples.add(entity("mod:machine", x, 0, 2_000_000));
        }
        RegionizabilityAnalyzer analyzer =
                new RegionizabilityAnalyzer(2, new int[]{8}, 10);
        RegionizabilityAnalyzer.Report r = analyzer.analyze(samples);

        assertEquals(1, r.regions().size(), "contiguous chunks form one region");
        assertEquals(1.0, r.speedupByWorkers().get(8), 1e-9,
                "a single region is the unit of parallelism — no speedup (this is what the graph layer is for)");
    }

    @Test
    void topTypesSortedByCostAndCounted() {
        List<TickSample> samples = List.of(
                entity("mod:cheap", 0, 0, 1_000),
                entity("mod:hot", 5, 5, 9_000_000),
                entity("mod:hot", 5, 6, 8_000_000),
                entity("mod:mid", 90, 90, 3_000_000));
        RegionizabilityAnalyzer analyzer =
                new RegionizabilityAnalyzer(2, new int[]{4}, 2);
        RegionizabilityAnalyzer.Report r = analyzer.analyze(samples);

        assertEquals(2, r.topTypes().size(), "trimmed to topN");
        assertEquals("mod:hot", r.topTypes().get(0).typeId());
        assertEquals(2, r.topTypes().get(0).count());
        assertEquals(17_000_000, r.topTypes().get(0).nanos());
        assertEquals("mod:mid", r.topTypes().get(1).typeId());
    }

    @Test
    void lptMakespanSchedulesLargestFirst() {
        List<RegionizabilityAnalyzer.RegionCost> regions = List.of(
                new RegionizabilityAnalyzer.RegionCost(1, 1, 8),
                new RegionizabilityAnalyzer.RegionCost(2, 1, 5),
                new RegionizabilityAnalyzer.RegionCost(3, 1, 4),
                new RegionizabilityAnalyzer.RegionCost(4, 1, 3));
        // 2 workers, LPT: {8,3}=11 vs {5,4}=9 -> makespan 11
        assertEquals(11, RegionizabilityAnalyzer.lptMakespan(regions, 2));
        // 4 workers: each its own -> 8
        assertEquals(8, RegionizabilityAnalyzer.lptMakespan(regions, 4));
    }

    @Test
    void profilerWindowRollsAndSnapshotIsSafe() {
        TickProfiler p = new TickProfiler(3);
        for (int t = 1; t <= 5; t++) {
            p.tickBoundary(t, t * 50_000_000L);
            p.record(TickSample.Source.ENTITY, "mod:e", ChunkKey.pack(0, 0), 1000);
        }
        p.tickBoundary(6, 6 * 50_000_000L);

        var window = p.snapshotWindow();
        assertEquals(3, window.size(), "window capped at 3");
        assertEquals(3, window.get(0).tickNumber(), "oldest retained tick is #3");
        assertEquals(5, window.get(2).tickNumber());
        assertEquals(50_000_000L, window.get(1).tickNanos(), "tick wall time measured between boundaries");
        assertEquals(1, window.get(2).samples().size());
    }
}
