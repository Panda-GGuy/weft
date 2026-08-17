package dev.weft.sandbox.coexist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The known-neighbor registry (RFC-0003 R3): a small data file,
 * {@code weft-neighbors.toml}, mapping modids to a default posture per Weft
 * module. Data, not code — compat for a new mod is a one-line PR.
 *
 * <p>Parses the TOML subset the file actually uses (tables + string values +
 * comments), so the engine-side modules stay dependency-free:
 * <pre>
 * [modid]
 * module_id = "cooperate" | "yield" | "refuse"
 * </pre>
 *
 * Unknown mods are simply absent: they default to cooperate (R3), with
 * Tier-3 mixin-overlap scanning as the backstop.
 */
public final class NeighborRegistry {

    /** modid -> (moduleId -> posture). */
    private final Map<String, Map<String, Posture>> byMod;

    private NeighborRegistry(Map<String, Map<String, Posture>> byMod) {
        this.byMod = byMod;
    }

    public static NeighborRegistry empty() {
        return new NeighborRegistry(Map.of());
    }

    /** @throws IllegalArgumentException on any line the subset grammar rejects. */
    public static NeighborRegistry parse(Reader reader) throws IOException {
        Map<String, Map<String, Posture>> byMod = new HashMap<>();
        Map<String, Posture> current = null;
        BufferedReader lines = new BufferedReader(reader);
        String raw;
        int lineNo = 0;
        while ((raw = lines.readLine()) != null) {
            lineNo++;
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                String modId = line.substring(1, line.length() - 1).trim();
                if (modId.isEmpty()) {
                    throw new IllegalArgumentException("Empty modid at line " + lineNo);
                }
                current = byMod.computeIfAbsent(modId, k -> new HashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0 || current == null) {
                throw new IllegalArgumentException(
                        "Expected [modid] table or module = \"posture\" at line " + lineNo + ": " + raw);
            }
            String module = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (module.isEmpty() || value.length() < 2
                    || value.charAt(0) != '"' || value.charAt(value.length() - 1) != '"') {
                throw new IllegalArgumentException(
                        "Expected module = \"posture\" at line " + lineNo + ": " + raw);
            }
            try {
                current.put(module, Posture.parse(value.substring(1, value.length() - 1)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " at line " + lineNo);
            }
        }
        return new NeighborRegistry(byMod);
    }

    private static String stripComment(String line) {
        // The subset never puts '#' inside a string, so a plain scan is safe.
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    /**
     * Postures declared for one Weft module by the neighbors actually present,
     * sorted by modid for deterministic reporting.
     */
    public Map<String, Posture> posturesFor(String moduleId, java.util.Set<String> presentModIds) {
        Map<String, Posture> out = new TreeMap<>();
        byMod.forEach((modId, modules) -> {
            Posture posture = modules.get(moduleId);
            if (posture != null && presentModIds.contains(modId)) {
                out.put(modId, posture);
            }
        });
        return out;
    }
}
