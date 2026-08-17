package dev.weft.services.path;

import dev.weft.api.path.NavView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowFieldTest {

    static final NavView FLAT = (x, y, z) -> y == 64 ? 0 : -1;

    @Test
    void followingTheFieldReachesTheTarget() {
        FlowField field = FlowField.compute(0, 64, 0, FLAT, 40, 50_000);
        int x = 25, y = 64, z = -18;
        for (int steps = 0; steps < 200; steps++) {
            if (x == 0 && y == 64 && z == 0) {
                return; // arrived
            }
            int[] next = field.stepFrom(x, y, z);
            assertNotNull(next, "covered cell must have a next step at " + x + "," + z);
            x = next[0];
            y = next[1];
            z = next[2];
        }
        fail("did not reach the target within 200 steps");
    }

    @Test
    void stepsAlwaysReduceDistance() {
        FlowField field = FlowField.compute(0, 64, 0, FLAT, 30, 50_000);
        for (int[] from : new int[][]{{20, 5}, {-15, 12}, {8, -22}, {1, 1}}) {
            int[] next = field.stepFrom(from[0], 64, from[1]);
            assertNotNull(next);
            double before = Math.hypot(from[0], from[1]);
            double after = Math.hypot(next[0], next[2]);
            assertTrue(after < before, "step must approach the target");
        }
    }

    @Test
    void wallsRespected() {
        // Wall at x=5, gap at z=8.
        NavView walled = (x, y, z) ->
                y != 64 ? -1 : (x == 5 && z >= -20 && z <= 20 && z != 8) ? -1 : 0;
        FlowField field = FlowField.compute(0, 64, 0, walled, 40, 100_000);
        // From behind the wall, walking the field must pass through the gap.
        int x = 12, y = 64, z = 0;
        boolean crossedAtGap = false;
        for (int steps = 0; steps < 300 && !(x == 0 && z == 0); steps++) {
            int[] next = field.stepFrom(x, y, z);
            assertNotNull(next, "reachable cell lost coverage at " + x + "," + z);
            if (next[0] == 5) {
                assertEquals(8, next[2], "only the gap cell is passable on the wall line");
                crossedAtGap = true;
            }
            x = next[0];
            y = next[1];
            z = next[2];
        }
        assertTrue(crossedAtGap);
        assertEquals(0, x);
        assertEquals(0, z);
    }

    @Test
    void uncoveredCellsReturnNull() {
        FlowField field = FlowField.compute(0, 64, 0, FLAT, 10, 50_000);
        assertNull(field.stepFrom(50, 64, 50), "outside radius");
        assertNull(field.stepFrom(0, 80, 0), "not walkable");
    }

    @Test
    void cacheReusesWithinTtlAndRecomputesAfter() {
        FlowFieldCache cache = new FlowFieldCache(40, 8);
        FlowField first = cache.get(0, 64, 0, 100, FLAT, 20, 50_000);
        assertSame(first, cache.get(0, 64, 0, 120, FLAT, 20, 50_000), "within TTL: shared");
        assertNotSame(first, cache.get(0, 64, 0, 141, FLAT, 20, 50_000), "past TTL: recomputed");
    }

    @Test
    void cacheEvictsDownToCap() {
        FlowFieldCache cache = new FlowFieldCache(1000, 3);
        for (int i = 0; i < 6; i++) {
            cache.get(i * 100, 64, 0, 10 + i, (x, y, z) -> y == 64 ? 0 : -1, 5, 1000);
        }
        assertTrue(cache.size() <= 3, "cap enforced, size=" + cache.size());
    }
}
