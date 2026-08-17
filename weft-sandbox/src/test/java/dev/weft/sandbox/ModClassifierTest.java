package dev.weft.sandbox;

import dev.weft.api.CompatTier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModClassifierTest {

    @Test
    void precedenceConflictBeatsEverything() {
        ModClassifier c = new ModClassifier(
                Set.of("otherthreadmod"),
                Set.of("otherthreadmod"), // even annotated-safe
                Map.of());
        assertEquals(CompatTier.CONFLICTING, c.tierOfMod("otherthreadmod"));
    }

    @Test
    void unknownModsDefaultToLegacy() {
        ModClassifier c = new ModClassifier(Set.of(), Set.of(), Map.of());
        assertEquals(CompatTier.LEGACY, c.tierOfMod("randommod"));
    }

    @Test
    void manifestVerifiesPerClassWithinLegacyMod() {
        ModClassifier c = new ModClassifier(Set.of(), Set.of(), Map.of(
                "xyztech", new ModClassifier.CompatManifest(
                        "xyztech", "1.2.3",
                        Set.of("com.xyz.ItemPipeBlockEntity"),
                        false)));
        assertEquals(CompatTier.LEGACY, c.tierOfMod("xyztech"));
        assertEquals(CompatTier.VERIFIED, c.tierOfClass("xyztech", "com.xyz.ItemPipeBlockEntity"));
        assertEquals(CompatTier.LEGACY, c.tierOfClass("xyztech", "com.xyz.SomeOtherClass"));
    }

    @Test
    void legacyLaneRunsInRegistrationOrderWithCostAttribution() {
        LegacyLane lane = new LegacyLane();
        StringBuilder order = new StringBuilder();
        lane.register("modA", () -> order.append("A"));
        lane.register("modB", () -> order.append("B"));
        lane.register("modA", () -> order.append("A"));

        assertEquals(3, lane.runTick());
        assertEquals("ABA", order.toString(), "deterministic registration order");
        assertEquals(3, lane.unitsByMod().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(2L, lane.unitsByMod().get("modA"));
        assertTrue(lane.costByModNanos().values().stream().allMatch(n -> n >= 0));
    }
}
