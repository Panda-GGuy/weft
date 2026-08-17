package dev.weft.services.activation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivationSchedulerTest {

    private static final ActivationScheduler.Tiers TIERS =
            new ActivationScheduler.Tiers(32, 64, 4, 20);

    private static double sq(double blocks) {
        return blocks * blocks;
    }

    @Test
    void tierBoundariesAreInclusive() {
        ActivationScheduler s = new ActivationScheduler(TIERS, Set.of(), Map.of());
        assertEquals(1, s.intervalFor("minecraft:cow", sq(0)));
        assertEquals(1, s.intervalFor("minecraft:cow", sq(32)));
        assertEquals(4, s.intervalFor("minecraft:cow", sq(32.01)));
        assertEquals(4, s.intervalFor("minecraft:cow", sq(64)));
        assertEquals(20, s.intervalFor("minecraft:cow", sq(64.01)));
        assertEquals(20, s.intervalFor("minecraft:cow", Double.POSITIVE_INFINITY));
    }

    @Test
    void exemptTypesAlwaysFullRate() {
        ActivationScheduler s = new ActivationScheduler(
                TIERS, Set.of("minecraft:wither"), Map.of());
        assertEquals(1, s.intervalFor("minecraft:wither", Double.POSITIVE_INFINITY));
    }

    @Test
    void overrideReplacesTierIntervalButNotFullRateRing() {
        ActivationScheduler s = new ActivationScheduler(
                TIERS, Set.of(), Map.of("mod:grinder", 2, "mod:sluggish", 40));
        // Opt-down: throttled softer than the far tier would say.
        assertEquals(2, s.intervalFor("mod:grinder", Double.POSITIVE_INFINITY));
        // Opt-up: throttled harder than the reduced tier would say.
        assertEquals(40, s.intervalFor("mod:sluggish", sq(50)));
        // Near a player, overrides never apply.
        assertEquals(1, s.intervalFor("mod:sluggish", sq(10)));
    }

    @Test
    void overrideOfOneIsAPerTypeOptOut() {
        ActivationScheduler s = new ActivationScheduler(
                TIERS, Set.of(), Map.of("mod:fragile", 1));
        assertEquals(1, s.intervalFor("mod:fragile", Double.POSITIVE_INFINITY));
    }

    @Test
    void staggerRunsEachEntityExactlyOncePerWindow() {
        for (int entityId : new int[]{0, 1, 7, 12345, Integer.MAX_VALUE}) {
            for (int interval : new int[]{2, 4, 20}) {
                int runs = 0;
                for (long tick = 1000; tick < 1000 + interval; tick++) {
                    if (ActivationScheduler.shouldRunThisTick(tick, entityId, interval)) {
                        runs++;
                    }
                }
                assertEquals(1, runs, "entity " + entityId + " interval " + interval);
            }
        }
    }

    @Test
    void staggerSpreadsEntitiesAcrossTheWindow() {
        // With ids 0..19 and interval 20, exactly one of them runs per tick.
        int interval = 20;
        for (long tick = 0; tick < 40; tick++) {
            int runsThisTick = 0;
            for (int id = 0; id < interval; id++) {
                if (ActivationScheduler.shouldRunThisTick(tick, id, interval)) {
                    runsThisTick++;
                }
            }
            assertEquals(1, runsThisTick, "tick " + tick);
        }
    }

    @Test
    void intervalOneAlwaysRuns() {
        assertTrue(ActivationScheduler.shouldRunThisTick(0, 0, 1));
        assertTrue(ActivationScheduler.shouldRunThisTick(999, 42, 0));
    }

    @Test
    void invalidConfigurationRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivationScheduler.Tiers(64, 32, 4, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivationScheduler.Tiers(32, 64, 20, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivationScheduler.Tiers(32, 64, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> new ActivationScheduler(TIERS, Set.of(), Map.of("mod:x", 0)));
    }
}
