package dev.weft.sandbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lane's per-tick contract (RFC-0001 §7.2): registered work first in
 * registration order, then one-shots in submission order (vanilla iteration
 * order); one-shots run exactly once; self-submissions land in the next pass;
 * costs and units attribute per source mod; a throwing unit is still charged.
 */
class LegacyLaneTest {

    @Test
    void oneShotsRunOnceInSubmissionOrderAfterRegistered() {
        LegacyLane lane = new LegacyLane();
        StringBuilder order = new StringBuilder();
        lane.register("regmod", () -> order.append("R"));
        lane.submit("modA", () -> order.append("a"));
        lane.submit("modB", () -> order.append("b"));
        lane.submit("modA", () -> order.append("c"));

        assertEquals(4, lane.runTick());
        assertEquals("Rabc", order.toString(), "registered first, then FIFO one-shots");
        assertEquals(0, lane.pending());

        // Next pass: one-shots are gone, registered work recurs.
        assertEquals(1, lane.runTick());
        assertEquals("RabcR", order.toString());
        assertEquals(5, lane.unitsRun());
    }

    @Test
    void submissionsDuringDrainDeferToNextPass() {
        LegacyLane lane = new LegacyLane();
        StringBuilder order = new StringBuilder();
        lane.submit("modA", () -> {
            order.append("1");
            lane.submit("modA", () -> order.append("2"));
        });

        assertEquals(1, lane.runTick(), "self-submission must not run in the same pass");
        assertEquals("1", order.toString());
        assertEquals(1, lane.pending());
        assertEquals(1, lane.runTick());
        assertEquals("12", order.toString());
    }

    @Test
    void costAndUnitsAttributePerMod() {
        LegacyLane lane = new LegacyLane();
        lane.submit("xyztech", () -> {});
        lane.submit("xyztech", () -> {});
        lane.submit("othermod", () -> {});
        lane.runTick();

        assertEquals(2L, lane.unitsByMod().get("xyztech"));
        assertEquals(1L, lane.unitsByMod().get("othermod"));
        assertTrue(lane.costByModNanos().get("xyztech") >= 0);
        assertTrue(lane.lastTickNanos() >= 0);
    }

    @Test
    void throwingUnitIsChargedAndPropagates() {
        LegacyLane lane = new LegacyLane();
        lane.submit("crashmod", () -> {
            throw new IllegalStateException("boom");
        });
        lane.submit("innocent", () -> fail("must not run after the lane propagates a crash"));

        assertThrows(IllegalStateException.class, lane::runTick);
        assertEquals(1L, lane.unitsByMod().get("crashmod"),
                "the crashing unit is still attributed (the crash report names the mod)");
        // Vanilla semantics: the crash propagates; the remaining unit stays
        // queued (the server is going down anyway).
        assertEquals(1, lane.pending());
    }

    @Test
    void clearDropsQueuedWork() {
        LegacyLane lane = new LegacyLane();
        lane.register("regmod", () -> fail("cleared"));
        lane.submit("modA", () -> fail("cleared"));
        lane.clear();
        assertEquals(0, lane.runTick());
        assertEquals(0, lane.pending());
    }
}
