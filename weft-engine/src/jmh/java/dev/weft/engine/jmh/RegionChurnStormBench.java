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

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/**
 * The pregen churn storm (found 2026-08-17: Chunky pregen ran ~20 cps slower
 * with the always-on topology feed): a serpentine sweep over fresh chunks
 * with a trailing unload window, split recompute attempted every 100 loads —
 * the load/unload shape {@code RegionTopology} sees during world pregen, and
 * the worst case for both the addChunk merge scan and split maintenance.
 * One op is the whole storm (6,400 loads, 2,048-chunk working set), so the
 * score is dominated by per-event cost at a realistic working-set size.
 * {@code loadgen_fresh_chunk_load} measures steady loads and misses this;
 * {@code RegionChurnTest} pins the algorithmic properties.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class RegionChurnStormBench {

    private static final int MERGE_DISTANCE = 8;
    private static final int GRID = 80;
    private static final int WINDOW = 2048;
    private static final int SPLIT_EVERY = 100;

    @Benchmark
    public long pregenChurnStorm() {
        RegionManager rm = new RegionManager(MERGE_DISTANCE, 42L);
        ArrayDeque<long[]> loaded = new ArrayDeque<>();
        int adds = 0;
        for (int z = 0; z < GRID; z++) {
            for (int i = 0; i < GRID; i++) {
                int x = (z % 2 == 0) ? i : GRID - 1 - i;
                rm.addChunk(x, z);
                adds++;
                loaded.addLast(new long[]{x, z});
                if (loaded.size() > WINDOW) {
                    long[] old = loaded.pollFirst();
                    rm.removeChunk((int) old[0], (int) old[1]);
                }
                if (adds % SPLIT_EVERY == 0) {
                    rm.recomputeSplits();
                }
            }
        }
        return rm.all().size() + rm.chunkCount();
    }
}
