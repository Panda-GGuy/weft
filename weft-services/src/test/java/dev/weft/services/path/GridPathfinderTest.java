package dev.weft.services.path;

import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.NavView;
import dev.weft.api.path.PathQuery;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GridPathfinderTest {

    /** Flat open plane at y=64; everything else impassable. */
    static final NavView FLAT = (x, y, z) -> y == 64 ? 0 : -1;

    static PathQuery query(int sx, int sz, int tx, int tz) {
        return new PathQuery(sx, 64, sz, tx, 64, tz, 0.5, 20_000, 512);
    }

    private final GridPathfinder grid = new GridPathfinder();

    @Test
    void straightLineOnOpenGround() {
        ComputedPath path = grid.findPath(query(0, 0, 10, 0), FLAT);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        assertEquals(0, path.x(0));
        assertEquals(10, path.x(path.nodeCount() - 1));
        assertEquals(11, path.nodeCount(), "cardinal straight line, one node per cell");
    }

    @Test
    void diagonalUsesDiagonalSteps() {
        ComputedPath path = grid.findPath(query(0, 0, 8, 8), FLAT);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        assertEquals(9, path.nodeCount(), "8 diagonal steps beat 16 cardinal ones");
    }

    @Test
    void detoursAroundWall() {
        // Wall at x=5 spanning z=-10..10, one gap at z=6.
        NavView walled = (x, y, z) ->
                y != 64 ? -1 : (x == 5 && z >= -10 && z <= 10 && z != 6) ? -1 : 0;
        ComputedPath path = grid.findPath(query(0, 0, 10, 0), walled);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        boolean throughGap = false;
        for (int i = 0; i < path.nodeCount(); i++) {
            if (path.x(i) == 5) {
                assertEquals(6, path.z(i), "the only opening is at z=6");
                throughGap = true;
            }
        }
        assertTrue(throughGap, "path must cross the wall line");
    }

    @Test
    void climbsSteps() {
        // Staircase: walkable y rises by 1 per x.
        NavView stairs = (x, y, z) -> y == 64 + Math.max(0, x) ? 0 : -1;
        ComputedPath path = grid.findPath(
                new PathQuery(0, 64, 0, 6, 70, 0, 0.5, 20_000, 512), stairs);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        assertEquals(70, path.y(path.nodeCount() - 1));
    }

    @Test
    void walledInIsUnreachable() {
        // Only a 3x3 island at origin is walkable; target is far away.
        NavView island = (x, y, z) ->
                y == 64 && Math.abs(x) <= 1 && Math.abs(z) <= 1 ? 0 : -1;
        ComputedPath path = grid.findPath(query(0, 0, 40, 0), island);
        assertEquals(ComputedPath.Status.UNREACHABLE, path.status());
    }

    @Test
    void budgetExhaustionYieldsPartialTowardTarget() {
        // 300-node budget cannot cover 500 blocks even on the optimal line.
        ComputedPath path = grid.findPath(
                new PathQuery(0, 64, 0, 500, 64, 0, 0.5, 300, 1024), FLAT);
        assertEquals(ComputedPath.Status.PARTIAL, path.status());
        assertTrue(path.nodeCount() > 1, "partial path leads somewhere");
        int endX = path.x(path.nodeCount() - 1);
        assertTrue(endX > 0, "closest approach is toward the target, got x=" + endX);
    }

    @Test
    void malusSteersAroundExpensiveGround() {
        // A high-malus strip at z=0 between x=2..8; cheap detour via z=1.
        NavView sticky = (x, y, z) ->
                y != 64 ? -1 : (z == 0 && x >= 2 && x <= 8) ? 8.0f : 0;
        ComputedPath path = grid.findPath(query(0, 0, 10, 0), sticky);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        int stickyCells = 0;
        for (int i = 0; i < path.nodeCount(); i++) {
            if (path.z(i) == 0 && path.x(i) >= 2 && path.x(i) <= 8) {
                stickyCells++;
            }
        }
        assertEquals(0, stickyCells, "malus 8 per cell makes the detour cheaper");
    }

    @Test
    void acceptRadiusStopsShortOfTarget() {
        ComputedPath path = grid.findPath(
                new PathQuery(0, 64, 0, 10, 64, 0, 3.0, 20_000, 512), FLAT);
        assertEquals(ComputedPath.Status.FOUND, path.status());
        int endX = path.x(path.nodeCount() - 1);
        assertTrue(endX >= 7, "stops within accept radius, got x=" + endX);
        assertTrue(endX <= 10);
    }
}
