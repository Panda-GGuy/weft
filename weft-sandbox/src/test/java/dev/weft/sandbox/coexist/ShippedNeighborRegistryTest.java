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
    }
}
