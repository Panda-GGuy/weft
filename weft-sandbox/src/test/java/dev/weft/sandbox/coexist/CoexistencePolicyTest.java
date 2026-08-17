package dev.weft.sandbox.coexist;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.weft.sandbox.coexist.CoexistencePolicy.State;
import static dev.weft.sandbox.coexist.CoexistencePolicy.resolve;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoexistencePolicyTest {

    @Test
    void cleanModuleIsActive() {
        var r = resolve(true, false, false, true, Map.of());
        assertEquals(State.ACTIVE, r.state());
        assertTrue(r.active());
    }

    @Test
    void cooperatingNeighborDoesNotPark() {
        var r = resolve(true, false, false, true, Map.of("spark", Posture.COOPERATE));
        assertEquals(State.ACTIVE, r.state());
    }

    @Test
    void yieldingNeighborParksTheModule() {
        var r = resolve(true, false, false, true, Map.of("alternate_current", Posture.YIELD));
        assertEquals(State.YIELDED, r.state());
        assertEquals("to alternate_current", r.detail());
        assertFalse(r.active());
    }

    @Test
    void refuseOutranksYield() {
        var r = resolve(true, false, false, true,
                Map.of("a_engine", Posture.REFUSE, "b_mod", Posture.YIELD));
        assertEquals(State.REFUSED, r.state());
    }

    @Test
    void configSwitchOffWinsOverNeighbors() {
        // A disabled module has no territory claim - no conflict to report.
        var r = resolve(false, false, false, true, Map.of("a_engine", Posture.REFUSE));
        assertEquals(State.DISABLED_CONFIG, r.state());
    }

    @Test
    void missingHooksSelfDisable() {
        var r = resolve(true, false, false, false, Map.of());
        assertEquals(State.SELF_DISABLED, r.state());
        assertFalse(r.active());
    }

    @Test
    void userForceEnableOutranksYieldButNotMissingHooks() {
        var yielded = resolve(true, true, false, true, Map.of("neighbor", Posture.YIELD));
        assertEquals(State.ACTIVE_FORCED, yielded.state());
        assertTrue(yielded.active());

        var noHooks = resolve(true, true, false, false, Map.of());
        assertEquals(State.SELF_DISABLED, noHooks.state());
    }

    @Test
    void userForceEnableCannotOutrankRefuse() {
        // Rung 4 (true tick-ownership conflict) is the one rung R4 must not
        // be able to override: two engines on one tick loop corrupts worlds.
        var r = resolve(false, true, false, true, Map.of("a_engine", Posture.REFUSE));
        assertEquals(State.REFUSED, r.state());
        assertFalse(r.active());

        var alsoEnabled = resolve(true, true, false, true, Map.of("a_engine", Posture.REFUSE));
        assertEquals(State.REFUSED, alsoEnabled.state());
    }

    @Test
    void userForceDisableOutranksEverything() {
        var r = resolve(true, true, true, true, Map.of());
        assertEquals(State.DISABLED_FORCED, r.state());
        assertFalse(r.active());
    }

    @Test
    void yieldReportsDeterministicNeighbor() {
        // TreeMap ordering from NeighborRegistry means the first yield is the
        // alphabetically-first modid; resolve just reports what it is given.
        var r = resolve(true, false, false, true,
                new java.util.TreeMap<>(Map.of("zzz", Posture.YIELD, "aaa", Posture.YIELD)));
        assertEquals("to aaa", r.detail());
    }
}
