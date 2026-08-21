package dev.weft.neoforge.coexist;

import com.mojang.logging.LogUtils;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.legacy.LegacyRouting;
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
                    // applyActive also resolves the partitionedTicking sub-mode
                    // (increment 4) from config; it is not a separate module.
                    RegionizedTicking::applyActive,
                    RegionizedTicking::statusDetail),
            new Def("legacy_lane", "P2 legacy lane (Tier-2 tick extraction)",
                    () -> WeftConfig.LEGACY_LANE,
                    // Extraction seams are fail-loud too (weft.mixins.json).
                    LegacyRouting::hooksApplied,
                    LegacyRouting::setActive,
                    LegacyRouting::statusDetail),
            new Def("observability", "WS-7 telemetry egress (metrics + event stream)",
                    // R1: one switch, satisfied by either surface being asked for.
                    () -> WeftConfig.METRICS_ENABLED || WeftConfig.EVENT_STREAM_ENABLED,
                    // R2: no mixins at all, so nothing can fail to apply. The
                    // runtime failures are a taken port and an unwritable sink,
                    // and those self-disable from inside setActive (rung 3).
                    dev.weft.neoforge.observability.WeftObservability::hooksApplied,
                    dev.weft.neoforge.observability.WeftObservability::setActive,
                    dev.weft.neoforge.observability.WeftObservability::statusDetail));

    private static volatile NeighborRegistry registry;
    private static volatile boolean resolvedOnce;

    /**
     * The last resolved state per module, keyed by module id, valued by the same
     * {@link #label} the R5 table prints (WS-7 / RFC-0009 §3.9).
     *
     * <p>Deriving {@code weft_module_state} and the {@code module_state_change}
     * event from this — rather than from a parallel mapping — is what makes it
     * impossible for the metric, the event and {@code /weft status} to disagree
     * about what a module is doing. R5's promise is that no user needs a debugger
     * to find out; three sources of truth would break it quietly.
     */
    private static volatile Map<String, String> lastResolutions = Map.of();

    /** Module id to R5 label, from the most recent {@link #resolve()}. */
    public static Map<String, String> lastResolutions() {
        return lastResolutions;
    }

    /**
     * Notified when a module's resolved state changes (WS-7). Null unless the
     * observability module is active (R6).
     */
    private static volatile StateChangeObserver stateChangeObserver;

    /** Receives module state transitions for the RFC-0009 §5 event. */
    public interface StateChangeObserver {
        void onStateChange(String module, String from, String to, String detail);
    }

    public static void setStateChangeObserver(StateChangeObserver observer) {
        stateChangeObserver = observer;
    }

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
        Map<String, String> previous = lastResolutions;
        Map<String, String> resolved = new java.util.LinkedHashMap<>();
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
            // Rung 3 can happen *during* activation, not only before it: WS-7's
            // endpoint discovers a taken port and its sink discovers an unwritable
            // path only when they try. Re-read the applied-check so the table, the
            // event and weft_module_state report what actually happened rather
            // than what we asked for — a line reading "ACTIVE (self-disabled: ...)"
            // is precisely the debugger-needed confusion R5 exists to prevent.
            if (resolution.active() && !def.hooksApplied().getAsBoolean()) {
                String why = def.extraDetail().get();
                resolution = new CoexistencePolicy.Resolution(
                        CoexistencePolicy.State.SELF_DISABLED,
                        why.isEmpty() ? "self-disabled during activation" : why);
            }
            if (resolution.state() == CoexistencePolicy.State.REFUSED) {
                // Ladder rung 4: never a mystery crash - one loud, clear report.
                LOGGER.error("Weft module '{}' conflicts with another installed mod over the same "
                        + "territory ({}). Choose one: remove the other mod, or disable this module "
                        + "via forceDisableModules in weft-common.toml.", def.id(), resolution.detail());
            }
            String state = label(resolution.state());
            resolved.put(def.id(), state);
            String before = previous.get(def.id());
            StateChangeObserver observer = stateChangeObserver;
            if (observer != null && before != null && !before.equals(state)) {
                observer.onStateChange(def.id(), before, state, resolution.detail());
            }
            lines.add(String.format("  %-16s %-13s %s", def.id(),
                    state,
                    detailFor(def, resolution, postures)).stripTrailing());
        }
        // Insertion-ordered, not Map.copyOf: the startup_posture event and the
        // metric should list modules in the same order the table prints them.
        lastResolutions = java.util.Collections.unmodifiableMap(resolved);
        resolvedOnce = true;
        lines.addAll(disarmedSubFlagLines(resolved));
        return lines;
    }

    /**
     * Issue #16. A yielded or disabled module's sub-flags are silently inert:
     * {@code parallelRegions}/{@code partitionedTicking} only ever resolve as
     * {@code regionized_ticking && flag}, so an operator who set them in
     * {@code weft-common.toml} and then installed a neighbor Weft yields to
     * would read their own config as proof of something that cannot happen.
     *
     * <p>R5's promise is that nobody needs a debugger to find out what a module
     * is doing, and "your parallel flag is set but architecturally cannot
     * engage" is exactly that class of confusion. One extra line, only when the
     * contradiction is real — and it is what the R7 {@code moonrise} cell greps
     * to prove the crash path is disarmed rather than merely re-labelled.
     */
    private static List<String> disarmedSubFlagLines(Map<String, String> resolved) {
        String state = resolved.get("regionized_ticking");
        if ("ACTIVE".equals(state) || state == null) {
            return List.of();
        }
        List<String> set = new ArrayList<>();
        if (WeftConfig.PARTITIONED_TICKING) {
            set.add("partitionedTicking");
        }
        if (WeftConfig.PARALLEL_REGIONS) {
            set.add("parallelRegions");
        }
        if (WeftConfig.BLOCK_ENTITY_SHARDING) {
            set.add("blockEntitySharding");
        }
        if (set.isEmpty()) {
            return List.of();
        }
        return List.of("  regionized_ticking is " + state + ", so its sub-flags are DISARMED: "
                + String.join(", ", set)
                + " set in config but no Weft worker fan-out can engage.");
    }

    /** Startup entry point (R5: the one-glance report, logged once). */
    public static void resolveAndLog() {
        LOGGER.info(String.join("\n", resolve()));
    }

    /** Config reload: re-run the ladder only once mods are discoverable. */
    public static void onConfigReload() {
        ActivationHooks.rebuildFromConfig();
        // Clear WS-7's self-disable latch so a reload can retry a fixed port or
        // a fixed sink path (RFC-0009 §8).
        dev.weft.neoforge.observability.WeftObservability.onConfigReload();
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
