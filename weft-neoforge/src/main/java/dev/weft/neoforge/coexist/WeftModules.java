package dev.weft.neoforge.coexist;

import com.mojang.logging.LogUtils;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import dev.weft.neoforge.service.SpawnDensityMode;
import dev.weft.sandbox.coexist.CoexistencePolicy;
import dev.weft.sandbox.coexist.NeighborRegistry;
import dev.weft.sandbox.coexist.Posture;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The RFC-0003 module ledger: every Weft optimization module registers here
 * with its independent switch (R1) and its runtime applied-check (R2).
 * {@link #resolve} walks each module down the coexistence ladder against the
 * known-neighbor registry (R3) and the user override lists (R4), pushes the
 * outcome into the module's active flag, and renders the one-glance posture
 * table (R5) — logged at startup and printed by {@code /weft status}.
 */
public final class WeftModules {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * @param id            stable module id (config override lists + registry key)
     * @param note          one-line human description for the table
     * @param enabledByConfig the module's own kill switch (R1)
     * @param hooksApplied  R2 applied-check; constant true for modules whose
     *                      hooks are core (fail-loud) or that need none
     * @param applyActive   receives the resolved active state (R6: an inactive
     *                      module must go fully inert through this flag)
     * @param extraDetail   live counters etc., shown when active
     */
    private record Def(String id, String note,
                       BooleanSupplier enabledByConfig,
                       BooleanSupplier hooksApplied,
                       Consumer<Boolean> applyActive,
                       Supplier<String> extraDetail) {}

    private static final List<Def> MODULES = List.of(
            new Def("profiler", "P0 tick profiler",
                    () -> WeftConfig.PROFILING_ENABLED,
                    () -> true, // core mixins are fail-loud; reaching here means they applied
                    active -> WeftConfig.PROFILING_ENABLED = active,
                    () -> ""),
            new Def("spawn_density", "P1 spawn-density service",
                    () -> WeftConfig.SPAWN_DENSITY_MODE != SpawnDensityMode.OFF,
                    // R2: AUTHORITATIVE needs the tickChunks mixin; SHADOW is
                    // event-fed and needs none.
                    () -> WeftConfig.SPAWN_DENSITY_MODE != SpawnDensityMode.AUTHORITATIVE
                            || SpawnDensityHooks.hooksApplied(),
                    SpawnDensityHooks::setActive,
                    () -> "mode " + WeftConfig.SPAWN_DENSITY_MODE),
            new Def("activation", "WS-1 entity activation scheduling",
                    () -> WeftConfig.ACTIVATION_SCHEDULING,
                    ActivationHooks::hooksApplied,
                    ActivationHooks::setActive,
                    ActivationHooks::statusDetail),
            new Def("pathfinding", "WS-2 async pathfinding service",
                    () -> WeftConfig.ASYNC_PATHFINDING,
                    dev.weft.neoforge.path.PathfindingHooks::hooksApplied,
                    dev.weft.neoforge.path.PathfindingHooks::setActive,
                    dev.weft.neoforge.path.PathfindingHooks::statusDetail),
            new Def("entity_sharding", "WS-10 intra-region entity sharding",
                    () -> WeftConfig.ENTITY_SHARDING,
                    // Engine-internal today (regions carry no real entities
                    // until later P2 increments hand tickables to the REGION
                    // phase); once those land, this becomes the tick-ownership
                    // mixins' R2 applied-check and a failure floors the shard
                    // count to 1 (RFC-0004 §3).
                    () -> true,
                    dev.weft.neoforge.WeftMod::applyEntitySharding,
                    dev.weft.neoforge.WeftMod::entityShardingDetail),
            new Def("regionized_ticking", "P2 regionized vanilla ticking",
                    () -> WeftConfig.REGIONIZED_TICKING,
                    // The ownership mixins are fail-loud (weft.mixins.json,
                    // R2's reserved case), so this is belt-and-braces.
                    RegionizedTicking::hooksApplied,
                    RegionizedTicking::setActive,
                    RegionizedTicking::statusDetail));

    private static volatile NeighborRegistry registry;
    private static volatile boolean resolvedOnce;

    private WeftModules() {}

    /**
     * Resolve every module, apply the outcomes, and return the posture table
     * lines. Called at server start (logged), from {@code /weft status}
     * (printed), and after a config reload (re-applied). Cheap and idempotent.
     */
    public static synchronized List<String> resolve() {
        Set<String> present = ModList.get().getMods().stream()
                .map(mod -> mod.getModId()).collect(Collectors.toUnmodifiableSet());
        List<String> lines = new ArrayList<>();
        lines.add("Weft module posture (RFC-0003):");
        for (Def def : MODULES) {
            Map<String, Posture> postures = registry().posturesFor(def.id(), present);
            CoexistencePolicy.Resolution resolution = CoexistencePolicy.resolve(
                    def.enabledByConfig().getAsBoolean(),
                    WeftConfig.FORCE_ENABLE_MODULES.contains(def.id()),
                    WeftConfig.FORCE_DISABLE_MODULES.contains(def.id()),
                    def.hooksApplied().getAsBoolean(),
                    postures);
            def.applyActive().accept(resolution.active());
            if (resolution.state() == CoexistencePolicy.State.REFUSED) {
                // Ladder rung 4: never a mystery crash - one loud, clear report.
                LOGGER.error("Weft module '{}' conflicts with another installed mod over the same "
                        + "territory ({}). Choose one: remove the other mod, or disable this module "
                        + "via forceDisableModules in weft-common.toml.", def.id(), resolution.detail());
            }
            lines.add(String.format("  %-16s %-13s %s", def.id(),
                    label(resolution.state()),
                    detailFor(def, resolution, postures)).stripTrailing());
        }
        resolvedOnce = true;
        return lines;
    }

    /** Startup entry point (R5: the one-glance report, logged once). */
    public static void resolveAndLog() {
        LOGGER.info(String.join("\n", resolve()));
    }

    /** Config reload: re-run the ladder only once mods are discoverable. */
    public static void onConfigReload() {
        ActivationHooks.rebuildFromConfig();
        if (resolvedOnce) {
            resolveAndLog();
        }
    }

    private static String label(CoexistencePolicy.State state) {
        return switch (state) {
            case ACTIVE -> "ACTIVE";
            case ACTIVE_FORCED -> "ACTIVE";
            case DISABLED_CONFIG, DISABLED_FORCED -> "DISABLED";
            case YIELDED -> "YIELDED";
            case SELF_DISABLED -> "SELF-DISABLED";
            case REFUSED -> "REFUSED";
        };
    }

    private static String detailFor(Def def, CoexistencePolicy.Resolution resolution,
                                    Map<String, Posture> postures) {
        List<String> parts = new ArrayList<>();
        if (!resolution.detail().isEmpty()) {
            parts.add(resolution.detail());
        }
        if (resolution.active()) {
            String cooperating = postures.entrySet().stream()
                    .filter(e -> e.getValue() == Posture.COOPERATE)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(", "));
            if (!cooperating.isEmpty()) {
                parts.add("cooperating with " + cooperating);
            }
            String extra = def.extraDetail().get();
            if (!extra.isEmpty()) {
                parts.add(extra);
            }
        }
        return parts.isEmpty() ? "" : "(" + String.join("; ", parts) + ")";
    }

    private static NeighborRegistry registry() {
        NeighborRegistry loaded = registry;
        if (loaded == null) {
            registry = loaded = loadRegistry();
        }
        return loaded;
    }

    private static NeighborRegistry loadRegistry() {
        // Shipped inside weft-sandbox and validated by its unit tests; if it
        // is somehow unreadable, every neighbor defaults to cooperate and the
        // Tier-3 overlap scan remains the backstop.
        try (InputStream stream = NeighborRegistry.class.getResourceAsStream("/weft-neighbors.toml")) {
            if (stream == null) {
                LOGGER.warn("weft-neighbors.toml not found; known-neighbor postures unavailable.");
                return NeighborRegistry.empty();
            }
            return NeighborRegistry.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read weft-neighbors.toml; known-neighbor postures unavailable.", e);
            return NeighborRegistry.empty();
        }
    }
}
