package dev.weft.sandbox.coexist;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborRegistryTest {

    private static NeighborRegistry parse(String toml) throws IOException {
        return NeighborRegistry.parse(new StringReader(toml));
    }

    @Test
    void parsesTablesValuesAndComments() throws IOException {
        NeighborRegistry registry = parse("""
                # header comment
                [spark]
                profiler = "cooperate"  # trailing comment

                [alternate_current]
                ws3_redstone = "yield"
                activation = "refuse"
                """);
        assertEquals(Map.of("spark", Posture.COOPERATE),
                registry.posturesFor("profiler", Set.of("spark", "alternate_current")));
        assertEquals(Map.of("alternate_current", Posture.YIELD),
                registry.posturesFor("ws3_redstone", Set.of("spark", "alternate_current")));
        assertEquals(Map.of("alternate_current", Posture.REFUSE),
                registry.posturesFor("activation", Set.of("alternate_current")));
    }

    @Test
    void absentModsContributeNothing() throws IOException {
        NeighborRegistry registry = parse("""
                [spark]
                profiler = "cooperate"
                """);
        assertTrue(registry.posturesFor("profiler", Set.of("lithium")).isEmpty());
        assertTrue(registry.posturesFor("unknown_module", Set.of("spark")).isEmpty());
        assertTrue(NeighborRegistry.empty().posturesFor("profiler", Set.of("spark")).isEmpty());
    }

    @Test
    void rejectsMalformedInput() {
        assertThrows(IllegalArgumentException.class, () -> parse("profiler = \"cooperate\""));
        assertThrows(IllegalArgumentException.class, () -> parse("[spark]\nprofiler cooperate"));
        assertThrows(IllegalArgumentException.class, () -> parse("[spark]\nprofiler = cooperate"));
        assertThrows(IllegalArgumentException.class, () -> parse("[spark]\nprofiler = \"maybe\""));
        assertThrows(IllegalArgumentException.class, () -> parse("[]\n"));
    }
}
