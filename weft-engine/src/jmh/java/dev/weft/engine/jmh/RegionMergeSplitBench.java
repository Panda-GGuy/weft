package dev.weft.engine.jmh;

import dev.weft.engine.region.RegionManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * WS-8: region merge/split — the between-tick topology maintenance cost
 * (RFC-0001 §4.2). One op builds 64 isolated single-chunk regions, bridges
 * them into one region (63 merges), then removes the bridges and splits back
 * apart (one recomputeSplits over 64 components).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class RegionMergeSplitBench {

    private static final int ISLANDS = 64;

    @Benchmark
    public int mergeChainThenSplit() {
        RegionManager rm = new RegionManager(2, 42L);
        // Islands 4 apart (Chebyshev > mergeDistance 2): all separate.
        for (int i = 0; i < ISLANDS; i++) {
            rm.addChunk(i * 4, 0);
        }
        // Bridges at the midpoints (distance 2 to each side): each merges
        // its two neighbors.
        for (int i = 0; i < ISLANDS - 1; i++) {
            rm.addChunk(i * 4 + 2, 0);
        }
        int merged = rm.all().size(); // 1
        // Remove the bridges; recomputeSplits must find 64 components.
        for (int i = 0; i < ISLANDS - 1; i++) {
            rm.removeChunk(i * 4 + 2, 0);
        }
        rm.recomputeSplits();
        return merged + rm.all().size();
    }
}
