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
 * A* over a {@link NavView} (WS-2, RFC-0002): the flat-grid workhorse the
 * hierarchical layer refines with, and the reference answer its tests
 * compare against.
 *
 * <p>Move model: 4 cardinal steps, each at dy in {-1, 0, +1} (step-up /
 * walk / step-down; vertical steps cost slightly extra), plus flat
 * diagonals when both flanking cardinals are passable (no corner cutting).
 * Step cost is euclidean distance plus the entered cell's malus.
 * Vanilla-shaped give-up rules: a node budget that ends the search with a
 * PARTIAL path to the closest approach, and a range bound past which cells
 * are never expanded.
 *
 * <p>All state is method-local; the class is stateless and thread-safe.
 */
public final class GridPathfinder {

    private static final double SQRT2 = Math.sqrt(2);
    private static final double STEP_UP_DOWN = 1.2;
    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Packed cell key: 26-bit x/z, 12-bit y — plenty for pathfinding ranges. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    static int unpackX(long key) {
        return (int) (key >> 38) << 6 >> 6; // sign-extend 26 bits
    }

    static int unpackY(long key) {
        return ((int) (key >> 26) & 0xFFF) << 20 >> 20; // sign-extend 12 bits
    }

    static int unpackZ(long key) {
        return ((int) key & 0x3FFFFFF) << 6 >> 6;
    }

    private static final class Node {
        final int x, y, z;
        double g = Double.MAX_VALUE;
        double f;
        Node parent;
        boolean closed;

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public ComputedPath findPath(PathQuery q, NavView view) {
        long t0 = System.nanoTime();
        Map<Long, Node> nodes = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));

        Node start = node(nodes, q.startX(), q.startY(), q.startZ());
        start.g = 0;
        start.f = heuristic(start, q);
        open.add(start);

        Node best = start;
        double bestH = heuristic(start, q);
        int visited = 0;
        double acceptSq = q.acceptRadius() * q.acceptRadius();
        double rangeSq = q.maxRange() * q.maxRange();

        while (!open.isEmpty() && visited < q.maxVisitedNodes()) {
            Node current = open.poll();
            if (current.closed) {
                continue; // stale queue entry; a cheaper copy was expanded
            }
            current.closed = true;
            visited++;

            double h = heuristic(current, q);
            if (h * h <= acceptSq) {
                return new ComputedPath(ComputedPath.Status.FOUND,
                        rebuild(current), visited, System.nanoTime() - t0);
            }
            if (h < bestH) {
                bestH = h;
                best = current;
            }

            for (int[] c : CARDINAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    step(nodes, open, current, current.x + c[0], current.y + dy,
                            current.z + c[1], dy == 0 ? 1.0 : STEP_UP_DOWN, q, rangeSq, view);
                }
            }
            for (int[] d : DIAGONAL) {
                if (view.malus(current.x + d[0], current.y, current.z) >= 0
                        && view.malus(current.x, current.y, current.z + d[1]) >= 0) {
                    step(nodes, open, current, current.x + d[0], current.y,
                            current.z + d[1], SQRT2, q, rangeSq, view);
                }
            }
        }

        if (best == start) {
            // Never got anywhere: nothing useful to return.
            return new ComputedPath(ComputedPath.Status.UNREACHABLE,
                    new int[0], visited, System.nanoTime() - t0);
        }
        return new ComputedPath(
                open.isEmpty() ? ComputedPath.Status.UNREACHABLE : ComputedPath.Status.PARTIAL,
                rebuild(best), visited, System.nanoTime() - t0);
    }

    private void step(Map<Long, Node> nodes, PriorityQueue<Node> open, Node from,
                      int x, int y, int z, double stepCost, PathQuery q, double rangeSq,
                      NavView view) {
        double sx = x - q.startX(), sy = y - q.startY(), sz = z - q.startZ();
        if (sx * sx + sy * sy + sz * sz > rangeSq) {
            return;
        }
        float malus = view.malus(x, y, z);
        if (malus < 0) {
            return;
        }
        Node n = node(nodes, x, y, z);
        if (n.closed) {
            return;
        }
        double g = from.g + stepCost + malus;
        if (g < n.g) {
            n.g = g;
            n.f = g + heuristic(n, q);
            n.parent = from;
            open.add(n);
        }
    }

    private Node node(Map<Long, Node> nodes, int x, int y, int z) {
        return nodes.computeIfAbsent(pack(x, y, z), k -> new Node(x, y, z));
    }

    private static double heuristic(Node n, PathQuery q) {
        double dx = q.targetX() - n.x, dy = q.targetY() - n.y, dz = q.targetZ() - n.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static int[] rebuild(Node end) {
        List<Node> chain = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            chain.add(n);
        }
        int[] out = new int[chain.size() * 3];
        for (int i = 0; i < chain.size(); i++) {
            Node n = chain.get(chain.size() - 1 - i);
            out[i * 3] = n.x;
            out[i * 3 + 1] = n.y;
            out[i * 3 + 2] = n.z;
        }
        return out;
    }
}
