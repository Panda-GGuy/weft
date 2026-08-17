package dev.weft.services.path;

import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.NavView;
import dev.weft.api.path.PathQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HierarchicalPathfinderTest {

    static final NavView FLAT = (x, y, z) -> y == 64 ? 0 : -1;

    private final HierarchicalPathfinder hpa = new HierarchicalPathfinder();
    private final GridPathfinder grid = new GridPathfinder();

    static PathQuery longQuery(int tx, int tz) {
        return new PathQuery(0, 64, 0, tx, 64, tz, 1.0, 60_000, 1024);
    }

    @Test
    void shortQueriesDelegateToGrid() {
        ComputedPath path = hpa.findPath(new PathQuery(0, 64, 0, 10, 64, 0, 0.5, 20_000, 512), FLAT);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        assertEquals(11, path.nodeCount());
    }

    @Test
    void longPathFoundAndContinuous() {
        ComputedPath path = hpa.findPath(longQuery(300, 120), FLAT);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        assertEquals(0, path.x(0));
        // Continuity: every step moves by at most 1 in each axis.
        for (int i = 1; i < path.nodeCount(); i++) {
            assertTrue(Math.abs(path.x(i) - path.x(i - 1)) <= 1
                            && Math.abs(path.y(i) - path.y(i - 1)) <= 1
                            && Math.abs(path.z(i) - path.z(i - 1)) <= 1,
                    "discontinuity at node " + i);
        }
        // End within accept radius.
        double dx = path.x(path.nodeCount() - 1) - 300;
        double dz = path.z(path.nodeCount() - 1) - 120;
        assertTrue(dx * dx + dz * dz <= 1.0 + 1e-9);
    }

    @Test
    void longDiagonalVisitsFarFewerNodesThanFlatGrid() {
        // A pure straight line is A*'s best case (it visits ~path length and
        // the hierarchy cannot beat it), so compare on a long diagonal-ish
        // query, where flat A*'s euclidean heuristic under octile step costs
        // expands a broad tie band and the corridor structure pays off.
        ComputedPath hierarchical = hpa.findPath(longQuery(397, 165), FLAT);
        ComputedPath flat = grid.findPath(longQuery(397, 165), FLAT);
        assertEquals(ComputedPath.Status.FOUND, hierarchical.status());
        assertEquals(ComputedPath.Status.FOUND, flat.status());
        assertTrue(hierarchical.visitedNodes() * 2 < flat.visitedNodes(),
                "hierarchy should expand far fewer nodes: "
                        + hierarchical.visitedNodes() + " vs " + flat.visitedNodes());
    }

    @Test
    void longPathCostStaysNearOptimal() {
        ComputedPath path = hpa.findPath(longQuery(400, 0), FLAT);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        assertTrue(path.nodeCount() - 1 <= 400 * 1.25,
                "path length " + (path.nodeCount() - 1) + " should stay within 25% of the 400-step optimum");
    }

    @Test
    void wallAcrossCorridorFallsBackAndDetours() {
        // Wall at x=100 (off chunk centers, so coarse probes plan straight
        // through it), spanning |z| <= 60: the refinement leg cannot detour
        // within its small budget, so the full-grid fallback must find it.
        NavView walled = (x, y, z) ->
                y != 64 ? -1 : (x == 100 && Math.abs(z) <= 60) ? -1 : 0;
        ComputedPath path = hpa.findPath(longQuery(200, 0), walled);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        for (int i = 0; i < path.nodeCount(); i++) {
            if (path.x(i) == 100) {
                assertTrue(Math.abs(path.z(i)) > 60, "must cross outside the wall");
            }
        }
    }

    @Test
    void unreachableTargetReportsUnreachable() {
        NavView island = (x, y, z) ->
                y == 64 && Math.abs(x) <= 2 && Math.abs(z) <= 2 ? 0 : -1;
        ComputedPath path = hpa.findPath(longQuery(300, 0), island);
        assertEquals(ComputedPath.Status.UNREACHABLE, path.status());
    }
}
