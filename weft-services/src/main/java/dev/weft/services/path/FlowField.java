package dev.weft.services.path;

import dev.weft.api.path.NavView;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Many-mobs-one-target pathfinding (WS-2, RFC-0002): a single Dijkstra
 * flood from the target over a {@link NavView} answers "which cell do I
 * step to next?" for every mob in range — one compute amortized across a
 * raid, a farm crowd, or a zombie horde, instead of N overlapping A* runs.
 *
 * <p>Immutable after {@link #compute}; safe to share across threads. Mobs
 * outside the computed radius (or standing on unreachable cells) get null
 * from {@link #stepFrom} and should fall back to individual pathfinding.
 */
public final class FlowField {

    private final int targetX, targetY, targetZ;
    /** cell -> the neighboring cell one step closer to the target. */
    private final Map<Long, Long> next;
    private final int cellsComputed;

    private FlowField(int targetX, int targetY, int targetZ,
                      Map<Long, Long> next, int cellsComputed) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.next = next;
        this.cellsComputed = cellsComputed;
    }

    /**
     * Dijkstra flood outward from the target, bounded by {@code radius} and
     * {@code maxCells}. Same move model as {@link GridPathfinder} (cardinals
     * with step-up/down, flat no-corner-cut diagonals), with edges reversed:
     * expanding from cell A to neighbor B records "B's next step toward the
     * target is A".
     */
    public static FlowField compute(int targetX, int targetY, int targetZ,
                                    NavView view, int radius, int maxCells) {
        Map<Long, Long> next = new HashMap<>();
        Map<Long, Double> dist = new HashMap<>();
        PriorityQueue<long[]> open = // {packedKey, distBits, x, y, z}
                new PriorityQueue<>((a, b) -> Double.compare(
                        Double.longBitsToDouble(a[1]), Double.longBitsToDouble(b[1])));

        long targetKey = GridPathfinder.pack(targetX, targetY, targetZ);
        dist.put(targetKey, 0.0);
        open.add(new long[]{targetKey, Double.doubleToLongBits(0.0), targetX, targetY, targetZ});
        int computed = 0;
        double radiusSq = (double) radius * radius;

        int[][] cardinal = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[][] diagonal = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        while (!open.isEmpty() && computed < maxCells) {
            long[] e = open.poll();
            double eDist = Double.longBitsToDouble(e[1]);
            if (eDist > dist.getOrDefault(e[0], Double.MAX_VALUE)) {
                continue; // stale entry
            }
            int ex = (int) e[2], ey = (int) e[3], ez = (int) e[4];
            computed++;

            for (int[] c : cardinal) {
                for (int dy = -1; dy <= 1; dy++) {
                    relax(e[0], eDist, ex + c[0], ey + dy, ez + c[1],
                            dy == 0 ? 1.0 : 1.2,
                            targetX, targetY, targetZ, radiusSq, view, dist, next, open);
                }
            }
            for (int[] d : diagonal) {
                if (view.malus(ex + d[0], ey, ez) >= 0 && view.malus(ex, ey, ez + d[1]) >= 0) {
                    relax(e[0], eDist, ex + d[0], ey, ez + d[1], Math.sqrt(2),
                            targetX, targetY, targetZ, radiusSq, view, dist, next, open);
                }
            }
        }
        return new FlowField(targetX, targetY, targetZ, next, computed);
    }

    private static void relax(long fromKey, double fromDist, int x, int y, int z,
                              double stepCost, int tx, int ty, int tz, double radiusSq,
                              NavView view, Map<Long, Double> dist, Map<Long, Long> next,
                              PriorityQueue<long[]> open) {
        double dx = x - tx, dyT = y - ty, dz = z - tz;
        if (dx * dx + dyT * dyT + dz * dz > radiusSq) {
            return;
        }
        float malus = view.malus(x, y, z);
        if (malus < 0) {
            return;
        }
        long key = GridPathfinder.pack(x, y, z);
        double d = fromDist + stepCost + malus;
        Double known = dist.get(key);
        if (known == null || d < known) {
            dist.put(key, d);
            next.put(key, fromKey);
            open.add(new long[]{key, Double.doubleToLongBits(d), x, y, z});
        }
    }

    /**
     * The cell one step closer to the target from (x, y, z), as {x, y, z},
     * or null when this cell is not covered (out of range / unreachable) —
     * callers fall back to individual pathfinding.
     */
    public int[] stepFrom(int x, int y, int z) {
        Long n = next.get(GridPathfinder.pack(x, y, z));
        if (n == null) {
            return null;
        }
        return new int[]{GridPathfinder.unpackX(n), GridPathfinder.unpackY(n),
                GridPathfinder.unpackZ(n)};
    }

    public boolean covers(int x, int y, int z) {
        long key = GridPathfinder.pack(x, y, z);
        return next.containsKey(key) || key == GridPathfinder.pack(targetX, targetY, targetZ);
    }

    public int targetX() {
        return targetX;
    }

    public int targetY() {
        return targetY;
    }

    public int targetZ() {
        return targetZ;
    }

    public int cellsComputed() {
        return cellsComputed;
    }
}
