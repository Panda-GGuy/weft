package dev.weft.services.path;

import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.NavView;
import dev.weft.api.path.PathQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * HPA*-style two-level search for long paths (WS-2, RFC-0002): a coarse A*
 * over 16x16 chunk-cells picks a corridor, then each pair of consecutive
 * chunk waypoints is refined with a small budget-bounded {@link
 * GridPathfinder} run and the segments are stitched. Falls back to a full
 * flat-grid search whenever the coarse layer or a refinement leg fails, so
 * the answer is never worse than not having the hierarchy — the hierarchy
 * is purely a node-count optimization for the long-distance case.
 *
 * <p>Chunk passability is probed, not proven: a chunk-cell is enterable at
 * height y if a walkable cell exists near its center within a small y-band.
 * Probes are heuristic by design (the fallback bears the correctness
 * burden), which is what keeps the coarse layer nearly free.
 *
 * <p>Stateless and thread-safe, same as {@link GridPathfinder}.
 */
public final class HierarchicalPathfinder {

    /** Refine with the grid when start and target are within this distance. */
    private static final double DIRECT_DISTANCE = 48;
    private static final int PROBE_Y_BAND = 4;
    private static final int SEGMENT_BUDGET = 2048;

    private final GridPathfinder grid = new GridPathfinder();

    public ComputedPath findPath(PathQuery q, NavView view) {
        if (q.distStartToTarget() <= DIRECT_DISTANCE) {
            return grid.findPath(q, view);
        }
        long t0 = System.nanoTime();
        List<int[]> waypoints = coarseChunkPath(q, view);
        if (waypoints == null) {
            return grid.findPath(q, view); // coarse layer failed: full search
        }

        // Refine leg by leg: start -> wp1 -> ... -> wpN -> target.
        List<int[]> stitched = new ArrayList<>();
        int visited = 0;
        int cx = q.startX(), cy = q.startY(), cz = q.startZ();
        waypoints.add(new int[]{q.targetX(), q.targetY(), q.targetZ()});
        for (int i = 0; i < waypoints.size(); i++) {
            int[] wp = waypoints.get(i);
            boolean lastLeg = i == waypoints.size() - 1;
            PathQuery leg = new PathQuery(cx, cy, cz, wp[0], wp[1], wp[2],
                    lastLeg ? q.acceptRadius() : 2.0, SEGMENT_BUDGET, q.maxRange());
            ComputedPath part = grid.findPath(leg, view);
            visited += part.visitedNodes();
            if (part.status() != ComputedPath.Status.FOUND) {
                return grid.findPath(q, view); // leg failed: full search
            }
            appendNodes(stitched, part, stitched.isEmpty() ? 0 : 1);
            int last = part.nodeCount() - 1;
            cx = part.x(last);
            cy = part.y(last);
            cz = part.z(last);
        }
        return new ComputedPath(ComputedPath.Status.FOUND, flatten(stitched),
                visited, System.nanoTime() - t0);
    }

    private static void appendNodes(List<int[]> out, ComputedPath part, int skip) {
        for (int i = skip; i < part.nodeCount(); i++) {
            out.add(new int[]{part.x(i), part.y(i), part.z(i)});
        }
    }

    private static int[] flatten(List<int[]> nodes) {
        int[] out = new int[nodes.size() * 3];
        for (int i = 0; i < nodes.size(); i++) {
            out[i * 3] = nodes.get(i)[0];
            out[i * 3 + 1] = nodes.get(i)[1];
            out[i * 3 + 2] = nodes.get(i)[2];
        }
        return out;
    }

    // --- coarse layer: A* over chunk cells ---

    private record ChunkNode(int cx, int cz, int y) {}

    /**
     * Chunk-cell corridor from the start's chunk to the target's chunk, as
     * chunk-center waypoints (start and target chunks excluded). Null when
     * no corridor was found within budget.
     */
    private List<int[]> coarseChunkPath(PathQuery q, NavView view) {
        int startCx = q.startX() >> 4, startCz = q.startZ() >> 4;
        int targetCx = q.targetX() >> 4, targetCz = q.targetZ() >> 4;
        // Chunk cells are nearly free to expand; a generous budget just bounds
        // the obstructed worst case before the flat-grid fallback takes over.
        int budget = 32 * (Math.abs(targetCx - startCx) + Math.abs(targetCz - startCz) + 16);
        int maxChunkRange = (int) (q.maxRange() / 16) + 1;

        record Entry(long key, double f) {}
        Map<Long, double[]> gScore = new HashMap<>(); // key -> {g, y}
        Map<Long, Long> parent = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

        long startKey = chunkKey(startCx, startCz);
        gScore.put(startKey, new double[]{0, q.startY()});
        open.add(new Entry(startKey, chunkDist(startCx, startCz, targetCx, targetCz)));
        int expanded = 0;

        while (!open.isEmpty() && expanded < budget) {
            Entry e = open.poll();
            int cx = (int) (e.key() >> 32), cz = (int) e.key();
            expanded++;
            if (cx == targetCx && cz == targetCz) {
                return rebuildChunkWaypoints(parent, gScore, e.key(), startKey);
            }
            double[] cur = gScore.get(e.key());
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cx + d[0], nz = cz + d[1];
                if (Math.abs(nx - startCx) > maxChunkRange || Math.abs(nz - startCz) > maxChunkRange) {
                    continue;
                }
                int walkY = probeWalkableY(nx, nz, (int) cur[1], view);
                if (walkY == Integer.MIN_VALUE) {
                    continue;
                }
                long nKey = chunkKey(nx, nz);
                double g = cur[0] + 16;
                double[] known = gScore.get(nKey);
                if (known == null || g < known[0]) {
                    gScore.put(nKey, new double[]{g, walkY});
                    parent.put(nKey, e.key());
                    open.add(new Entry(nKey, g + chunkDist(nx, nz, targetCx, targetCz)));
                }
            }
        }
        return null;
    }

    /** Walkable y at the chunk's center near yHint, or MIN_VALUE when none. */
    private static int probeWalkableY(int cx, int cz, int yHint, NavView view) {
        int x = (cx << 4) + 8, z = (cz << 4) + 8;
        for (int dy = 0; dy <= PROBE_Y_BAND; dy++) {
            if (view.malus(x, yHint + dy, z) >= 0) {
                return yHint + dy;
            }
            if (dy > 0 && view.malus(x, yHint - dy, z) >= 0) {
                return yHint - dy;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static List<int[]> rebuildChunkWaypoints(Map<Long, Long> parent,
                                                     Map<Long, double[]> gScore,
                                                     long endKey, long startKey) {
        List<int[]> waypoints = new ArrayList<>();
        for (long key = endKey; key != startKey; key = parent.get(key)) {
            int cx = (int) (key >> 32), cz = (int) key;
            waypoints.add(new int[]{(cx << 4) + 8, (int) gScore.get(key)[1], (cz << 4) + 8});
        }
        java.util.Collections.reverse(waypoints);
        if (!waypoints.isEmpty()) {
            waypoints.remove(waypoints.size() - 1); // target chunk: the final leg goes to the real target
        }
        return waypoints;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static double chunkDist(int ax, int az, int bx, int bz) {
        // Manhattan: exact (not just admissible) for the 4-connected coarse
        // grid on open ground, so diagonals don't explode into a tie band.
        return 16.0 * (Math.abs(ax - bx) + Math.abs(az - bz));
    }
}
