package dev.weft.sandbox.coexist;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped {@code weft-neighbors.toml} must always parse (a malformed
 * one-line compat PR should fail here, not at a user's startup) and its seed
 * postures must match RFC-0003 §3.
 */
class ShippedNeighborRegistryTest {

    @Test
    void shippedRegistryParsesWithExpectedSeedPostures() throws IOException {
        var stream = NeighborRegistry.class.getResourceAsStream("/weft-neighbors.toml");
        assertNotNull(stream, "weft-neighbors.toml missing from resources");
        NeighborRegistry registry;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            registry = NeighborRegistry.parse(reader);
        }
        Set<String> everyone = Set.of("spark", "lithium", "alternate_current", "forgia");
        assertEquals(Map.of("spark", Posture.COOPERATE),
                registry.posturesFor("profiler", everyone));
        assertEquals(Map.of("lithium", Posture.COOPERATE),
                registry.posturesFor("activation", everyone));
        assertEquals(Map.of("alternate_current", Posture.YIELD),
                registry.posturesFor("ws3_redstone", everyone));
        // RFC-0003 sec. 3: tick-ownership engines refuse (Tier 3), P2+WS-10.
        assertEquals(Map.of("forgia", Posture.REFUSE),
                registry.posturesFor("regionized_ticking", everyone));
        assertEquals(Map.of("forgia", Posture.REFUSE),
                registry.posturesFor("entity_sharding", everyone));
        assertEquals(Map.of("forgia", Posture.REFUSE),
                registry.posturesFor("legacy_lane", everyone));
    }

    /**
     * RESEARCH-0003 §3 rows added 2026-08-18. Both modids were read out of jar
     * metadata on the branch targeting MC 1.21.1 — a wrong modid never matches
     * and the registry silently looks like it works, so pin them here.
     */
    @Test
    void serverCoreYieldsActivationAndSpawnDensity() throws IOException {
        NeighborRegistry registry = shipped();
        Set<String> present = Set.of("servercore");
        assertEquals(Map.of("servercore", Posture.YIELD),
                registry.posturesFor("activation", present));
        assertEquals(Map.of("servercore", Posture.YIELD),
                registry.posturesFor("spawn_density", present));
    }

    /**
     * ScalableLux cooperates with everything Weft registers today and yields
     * WS-4.3's lane. Its {@code regionized_ticking}/{@code entity_sharding}
     * postures are deliberately UNSET pending RFC-0006's light-engine audit item
     * (hazard 19 candidate) — asserted absent so a future edit that seeds an
     * untested posture there has to come through this test.
     */
    @Test
    void scalableLuxCooperatesAndLeavesP2PosturesUnset() throws IOException {
        NeighborRegistry registry = shipped();
        Set<String> present = Set.of("scalablelux");
        assertEquals(Map.of("scalablelux", Posture.COOPERATE),
                registry.posturesFor("profiler", present));
        assertEquals(Map.of("scalablelux", Posture.COOPERATE),
                registry.posturesFor("spawn_density", present));
        assertEquals(Map.of("scalablelux", Posture.YIELD),
                registry.posturesFor("ws4_light", present));
        assertEquals(Map.of(), registry.posturesFor("regionized_ticking", present),
                "regionized_ticking posture must stay unset until the light-engine audit closes");
        assertEquals(Map.of(), registry.posturesFor("entity_sharding", present),
                "entity_sharding posture must stay unset until the light-engine audit closes");
    }

    private static NeighborRegistry shipped() throws IOException {
        var stream = NeighborRegistry.class.getResourceAsStream("/weft-neighbors.toml");
        assertNotNull(stream, "weft-neighbors.toml missing from resources");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return NeighborRegistry.parse(reader);
        }
    }
}
