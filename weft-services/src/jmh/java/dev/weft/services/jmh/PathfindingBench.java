package dev.weft.services.jmh;

import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.NavView;
import dev.weft.api.path.PathQuery;
import dev.weft.services.path.FlowField;
import dev.weft.services.path.GridPathfinder;
import dev.weft.services.path.HierarchicalPathfinder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * WS-2 benchmarks (RFC-0002): the two structural claims behind the
 * pathfinding service.
 *
 * <ul>
 * <li><b>Hierarchical vs flat A*</b> on a long diagonal path over lightly
 *     obstructed ground — the "wandering mob crosses the map" shape.</li>
 * <li><b>Flow field vs per-mob A*</b> for 300 mobs converging on one target
 *     — the raid / farm / horde shape. The flow-field number includes the
 *     one-time flood; per-mob A* pays a full search each.</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
public class PathfindingBench {

    private static final int MOBS = 300;

    /** ~8% obstructed flat ground: deterministic hash-based obstacles. */
    static final NavView TERRAIN = (x, y, z) -> {
        if (y != 64) {
            return -1;
        }
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        return (h & 0xFF) < 20 ? -1 : 0;
    };

    private final GridPathfinder grid = new GridPathfinder();
    private final HierarchicalPathfinder hpa = new HierarchicalPathfinder();
    private int[][] mobPositions;

    @Setup
    public void setUp() {
        SplittableRandom rng = new SplittableRandom(7);
        mobPositions = new int[MOBS][];
        for (int i = 0; i < MOBS; i++) {
            int x, z;
            do {
                x = rng.nextInt(-60, 61);
                z = rng.nextInt(-60, 61);
            } while (TERRAIN.malus(x, 64, z) < 0 || (x == 0 && z == 0));
            mobPositions[i] = new int[]{x, z};
        }
    }

    private static PathQuery longQuery() {
        return new PathQuery(0, 64, 0, 397, 64, 165, 1.0, 200_000, 1024);
    }

    @Benchmark
    public ComputedPath longPathFlatGrid() {
        return grid.findPath(longQuery(), TERRAIN);
    }

    @Benchmark
    public ComputedPath longPathHierarchical() {
        return hpa.findPath(longQuery(), TERRAIN);
    }

    /** 300 mobs, one target, each running its own A*. */
    @Benchmark
    public void hordePerMobAStar(Blackhole bh) {
        for (int[] pos : mobPositions) {
            bh.consume(grid.findPath(new PathQuery(pos[0], 64, pos[1], 0, 64, 0,
                    1.0, 20_000, 256), TERRAIN));
        }
    }

    /** 300 mobs, one target, one shared flood; each mob reads its next step. */
    @Benchmark
    public void hordeFlowField(Blackhole bh) {
        FlowField field = FlowField.compute(0, 64, 0, TERRAIN, 96, 200_000);
        for (int[] pos : mobPositions) {
            bh.consume(field.stepFrom(pos[0], 64, pos[1]));
        }
    }
}
