package dev.weft.sandbox;

import dev.weft.api.CompatTier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Assigns every mod (and, at finer grain, every class) a {@link CompatTier}
 * at load time (RFC-0001 §7.1). Sources of truth, in precedence order:
 *
 * <ol>
 *   <li>Conflict detection — mods whose mixins/patches overlap the tick loop
 *       targets Weft owns (Tier 3, load-time report).</li>
 *   <li>{@code @WeftSafe} annotations shipped by the mod itself (Tier 1).</li>
 *   <li>Signed compat manifests matched to the exact mod version (Tier 1,
 *       possibly per-class).</li>
 *   <li>Default: Tier 2 (legacy lane).</li>
 * </ol>
 *
 * The NeoForge module feeds this from mod scan data; this class is the pure
 * decision core so the precedence rules are unit-testable off-game.
 */
public final class ModClassifier {

    private final Set<String> conflictingModIds;
    private final Set<String> annotatedSafeMods;
    private final Map<String, CompatManifest> manifests;
    private final Map<String, CompatTier> decisionCache = new ConcurrentHashMap<>();

    public record CompatManifest(String modId, String modVersion, Set<String> safeClasses,
                                 boolean wholeModSafe) {}

    public ModClassifier(Set<String> conflictingModIds,
                         Set<String> annotatedSafeMods,
                         Map<String, CompatManifest> manifests) {
        this.conflictingModIds = Set.copyOf(conflictingModIds);
        this.annotatedSafeMods = Set.copyOf(annotatedSafeMods);
        this.manifests = Map.copyOf(manifests);
    }

    public CompatTier tierOfMod(String modId) {
        return decisionCache.computeIfAbsent(modId, id -> {
            if (conflictingModIds.contains(id)) {
                return CompatTier.CONFLICTING;
            }
            if (annotatedSafeMods.contains(id)) {
                return CompatTier.VERIFIED;
            }
            CompatManifest m = manifests.get(id);
            if (m != null && m.wholeModSafe()) {
                return CompatTier.VERIFIED;
            }
            return CompatTier.LEGACY;
        });
    }

    /** Per-class grain: a manifest may verify hot classes of an otherwise-legacy mod. */
    public CompatTier tierOfClass(String modId, String className) {
        CompatTier modTier = tierOfMod(modId);
        if (modTier != CompatTier.LEGACY) {
            return modTier;
        }
        CompatManifest m = manifests.get(modId);
        if (m != null && m.safeClasses().contains(className)) {
            return CompatTier.VERIFIED;
        }
        return CompatTier.LEGACY;
    }
}
