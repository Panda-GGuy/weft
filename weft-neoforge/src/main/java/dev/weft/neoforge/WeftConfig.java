package dev.weft.neoforge;

import dev.weft.neoforge.service.SpawnDensityMode;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Weft tunables (RFC §4.2), backed by NeoForge's config system
 * ({@code config/weft-common.toml}).
 *
 * <p>Values are cached into plain static fields on {@link ModConfigEvent}
 * so hot paths (profiler hooks, per-tick reads) never touch the config
 * machinery, and reads before the config loads fall back to the defaults
 * instead of throwing.
 */
public final class WeftConfig {
    private WeftConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue MERGE_DISTANCE_SPEC = BUILDER
            .comment("Chebyshev chunk distance within which regions merge.",
                    "Also used by the P0 report's hypothetical-region partition. RFC-0001 sec. 4.2.")
            .defineInRange("mergeDistance", 8, 1, 64);

    private static final ModConfigSpec.IntValue RESERVED_THREADS_SPEC = BUILDER
            .comment("Threads reserved for IO / netty / GC breathing room.")
            .defineInRange("reservedThreads", 2, 0, 32);

    private static final ModConfigSpec.BooleanValue PROFILING_ENABLED_SPEC = BUILDER
            .comment("Whether the P0 tick profiler records samples.",
                    "Toggle at runtime with /weft profile on|off (not persisted).")
            .define("profilingEnabled", true);

    private static final ModConfigSpec.IntValue PROFILE_WINDOW_TICKS_SPEC = BUILDER
            .comment("Rolling window of completed ticks the report is computed over.")
            .defineInRange("profileWindowTicks", 100, 1, 20 * 600);

    private static final ModConfigSpec.IntValue REPORT_LOG_INTERVAL_TICKS_SPEC = BUILDER
            .comment("Log a report summary to console every N ticks (1200 = 60s). 0 = off.")
            .defineInRange("reportLogIntervalTicks", 1200, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue REPORT_TOP_TYPES_SPEC = BUILDER
            .comment("How many cost sources the report lists.")
            .defineInRange("reportTopTypes", 12, 1, 100);

    private static final ModConfigSpec.EnumValue<SpawnDensityMode> SPAWN_DENSITY_MODE_SPEC = BUILDER
            .comment("P1 spawn-density service (RFC-0001 sec. 11). OFF: vanilla only.",
                    "SHADOW: recompute the scan off-thread and diff against vanilla, which",
                    "stays authoritative (parity data via /weft services). AUTHORITATIVE:",
                    "vanilla's per-tick createState scan is replaced by the off-thread",
                    "result (one tick stale by design); any tick the result isn't fresh",
                    "falls back to vanilla's synchronous scan. Default AUTHORITATIVE on the",
                    "strength of the shadow-mode parity evidence (see README Status).")
            .defineEnum("spawnDensityMode", SpawnDensityMode.AUTHORITATIVE);

    private static final ModConfigSpec.IntValue SPAWN_DENSITY_VERIFY_INTERVAL_TICKS_SPEC = BUILDER
            .comment("In AUTHORITATIVE mode, run vanilla's synchronous scan anyway every N",
                    "ticks, use it for that tick, and diff our async result against it so",
                    "parity evidence keeps flowing after graduation. 0 = never verify.")
            .defineInRange("spawnDensityVerifyIntervalTicks", 200, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.IntValue CENSUS_RECONCILE_INTERVAL_TICKS_SPEC = BUILDER
            .comment("How often (ticks) the incremental entity census is reconciled against a",
                    "full scan. Drift numbers appear in /weft services. 0 = census disabled.")
            .defineInRange("censusReconcileIntervalTicks", 200, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SPEEDUP_WORKER_COUNTS_SPEC = BUILDER
            .comment("Worker counts to estimate hypothetical speedup for.")
            .defineListAllowEmpty("speedupWorkerCounts", List.of(2, 4, 8, 16),
                    () -> 4, o -> o instanceof Integer i && i >= 1 && i <= 1024);

    // --- WS-1: entity activation scheduling (RFC-0002; kill switch per RFC-0003 R1) ---

    private static final ModConfigSpec.BooleanValue ACTIVATION_SCHEDULING_SPEC = BUILDER
            .comment("WS-1 (RFC-0002): tick distant mobs' expensive AI (sensing, goal and",
                    "target selectors) at reduced frequency, and stretch their periodic",
                    "path-recompute window by the same factor. Movement, physics, the",
                    "per-tick navigation step and brain ticking stay per-tick; within",
                    "activationFullRateDistance of a player nothing changes at all.",
                    "Independent kill switch (RFC-0003 R1); ships off until the WS-8",
                    "benchmarks prove the acceptance criteria.")
            .define("activationScheduling", false);

    private static final ModConfigSpec.IntValue ACTIVATION_FULL_RATE_DISTANCE_SPEC = BUILDER
            .comment("Blocks from the nearest player within which AI always runs every tick.")
            .defineInRange("activationFullRateDistance", 32, 0, 1024);

    private static final ModConfigSpec.IntValue ACTIVATION_REDUCED_DISTANCE_SPEC = BUILDER
            .comment("Blocks within which AI runs every activationReducedInterval ticks;",
                    "beyond it, every activationFarInterval ticks.")
            .defineInRange("activationReducedDistance", 64, 0, 1024);

    private static final ModConfigSpec.IntValue ACTIVATION_REDUCED_INTERVAL_SPEC = BUILDER
            .defineInRange("activationReducedInterval", 4, 1, 200);

    private static final ModConfigSpec.IntValue ACTIVATION_FAR_INTERVAL_SPEC = BUILDER
            .defineInRange("activationFarInterval", 20, 1, 200);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> ACTIVATION_EXEMPT_TYPES_SPEC = BUILDER
            .comment("Entity types never throttled, in addition to the built-in exemptions",
                    "(raiders and anything with a live attack target).")
            .defineListAllowEmpty("activationExemptTypes",
                    List.of("minecraft:ender_dragon", "minecraft:wither",
                            "minecraft:warden", "minecraft:elder_guardian"),
                    () -> "modid:entity_type", o -> o instanceof String s && !s.isBlank());

    private static final ModConfigSpec.ConfigValue<List<? extends String>> ACTIVATION_TYPE_OVERRIDES_SPEC = BUILDER
            .comment("Per-type interval overrides, entries like \"modid:entity_type=8\".",
                    "1 opts the type out of throttling; larger values replace the tier",
                    "interval whenever the type is outside the full-rate ring.")
            .defineListAllowEmpty("activationTypeOverrides", List.of(),
                    () -> "modid:entity_type=8",
                    o -> o instanceof String s && parseOverride(s) != null);

    // --- WS-2: async pathfinding (RFC-0002; kill switch per RFC-0003 R1) ---

    private static final ModConfigSpec.BooleanValue ASYNC_PATHFINDING_SPEC = BUILDER
            .comment("WS-2 (RFC-0002): run mob pathfinding (the A* inside createPath) on",
                    "Weft worker threads; results apply at the next tick boundary while",
                    "the mob keeps following its previous path. Node evaluation stays",
                    "vanilla's own (modded NodeEvaluators respected). Independent kill",
                    "switch (RFC-0003 R1). Default ON since the in-world acceptance run:",
                    "59% entity-phase reduction on the WS-2 300-zombie stress world and",
                    "~0% (no harm) on path-light worlds; see README Status.")
            .define("asyncPathfinding", true);

    private static final ModConfigSpec.IntValue PATHFINDING_THREADS_SPEC = BUILDER
            .comment("Worker threads for the pathfinding service. Takes effect when the",
                    "module (re)activates.")
            .defineInRange("pathfindingThreads", 2, 1, 8);

    // --- WS-10: intra-region entity sharding (RFC-0004; kill switch per RFC-0003 R1) ---

    private static final ModConfigSpec.BooleanValue ENTITY_SHARDING_SPEC = BUILDER
            .comment("WS-10 (RFC-0004): fan a big region's tickables out across worker",
                    "threads (the solo-play / one-region lever). Opt-in and off by default",
                    "until the parity suite proves it (RFC-0004 sec. 2.5): within-tick",
                    "entity interleaving is no longer vanilla's exact list order, though",
                    "outcomes are deterministic and reproducible.")
            .define("entitySharding", false);

    private static final ModConfigSpec.IntValue ENTITY_SHARD_MIN_BATCH_SPEC = BUILDER
            .comment("Minimum tickables per shard; regions below this stay serial.")
            .defineInRange("entityShardMinBatch", 64, 1, 100_000);

    // --- RFC-0003 R4: user overrides of the coexistence ladder, both directions ---

    private static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_ENABLE_MODULES_SPEC = BUILDER
            .comment("Weft module ids to force-enable over a yield (RFC-0003 R4), e.g.",
                    "\"activation\". Logged as user-chosen. Module ids: /weft status.")
            .defineListAllowEmpty("forceEnableModules", List.of(),
                    () -> "module_id", o -> o instanceof String s && !s.isBlank());

    private static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_DISABLE_MODULES_SPEC = BUILDER
            .comment("Weft module ids to force-disable (RFC-0003 R4).")
            .defineListAllowEmpty("forceDisableModules", List.of(),
                    () -> "module_id", o -> o instanceof String s && !s.isBlank());

    public static final ModConfigSpec SPEC = BUILDER.build();

    // --- cached values (defaults mirror the spec; refreshed on config events) ---

    public static volatile int MERGE_DISTANCE = 8;
    public static volatile int RESERVED_THREADS = 2;
    public static volatile boolean PROFILING_ENABLED = true;
    public static volatile int PROFILE_WINDOW_TICKS = 100;
    public static volatile int REPORT_LOG_INTERVAL_TICKS = 1200;
    public static volatile int REPORT_TOP_TYPES = 12;
    public static volatile SpawnDensityMode SPAWN_DENSITY_MODE = SpawnDensityMode.AUTHORITATIVE;
    public static volatile int SPAWN_DENSITY_VERIFY_INTERVAL_TICKS = 200;
    public static volatile int CENSUS_RECONCILE_INTERVAL_TICKS = 200;
    public static volatile int[] SPEEDUP_WORKER_COUNTS = {2, 4, 8, 16};
    public static volatile boolean ACTIVATION_SCHEDULING = false;
    public static volatile int ACTIVATION_FULL_RATE_DISTANCE = 32;
    public static volatile int ACTIVATION_REDUCED_DISTANCE = 64;
    public static volatile int ACTIVATION_REDUCED_INTERVAL = 4;
    public static volatile int ACTIVATION_FAR_INTERVAL = 20;
    public static volatile Set<String> ACTIVATION_EXEMPT_TYPES = Set.of(
            "minecraft:ender_dragon", "minecraft:wither",
            "minecraft:warden", "minecraft:elder_guardian");
    public static volatile Map<String, Integer> ACTIVATION_TYPE_OVERRIDES = Map.of();
    public static volatile boolean ASYNC_PATHFINDING = true;
    public static volatile int PATHFINDING_THREADS = 2;
    public static volatile boolean ENTITY_SHARDING = false;
    public static volatile int ENTITY_SHARD_MIN_BATCH = 64;
    public static volatile Set<String> FORCE_ENABLE_MODULES = Set.of();
    public static volatile Set<String> FORCE_DISABLE_MODULES = Set.of();

    /** {@code "type=interval"} entry parser; null when malformed (also the spec validator). */
    static Map.Entry<String, Integer> parseOverride(String entry) {
        int eq = entry.indexOf('=');
        if (eq <= 0 || eq == entry.length() - 1) {
            return null;
        }
        try {
            int interval = Integer.parseInt(entry.substring(eq + 1).trim());
            return interval >= 1 ? Map.entry(entry.substring(0, eq).trim(), interval) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Wired to the mod event bus in {@link WeftMod}. */
    static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        MERGE_DISTANCE = MERGE_DISTANCE_SPEC.get();
        RESERVED_THREADS = RESERVED_THREADS_SPEC.get();
        PROFILING_ENABLED = PROFILING_ENABLED_SPEC.get();
        PROFILE_WINDOW_TICKS = PROFILE_WINDOW_TICKS_SPEC.get();
        REPORT_LOG_INTERVAL_TICKS = REPORT_LOG_INTERVAL_TICKS_SPEC.get();
        REPORT_TOP_TYPES = REPORT_TOP_TYPES_SPEC.get();
        SPAWN_DENSITY_MODE = SPAWN_DENSITY_MODE_SPEC.get();
        SPAWN_DENSITY_VERIFY_INTERVAL_TICKS = SPAWN_DENSITY_VERIFY_INTERVAL_TICKS_SPEC.get();
        CENSUS_RECONCILE_INTERVAL_TICKS = CENSUS_RECONCILE_INTERVAL_TICKS_SPEC.get();
        SPEEDUP_WORKER_COUNTS = SPEEDUP_WORKER_COUNTS_SPEC.get().stream()
                .mapToInt(Integer::intValue).toArray();
        ACTIVATION_SCHEDULING = ACTIVATION_SCHEDULING_SPEC.get();
        ACTIVATION_FULL_RATE_DISTANCE = ACTIVATION_FULL_RATE_DISTANCE_SPEC.get();
        ACTIVATION_REDUCED_DISTANCE = ACTIVATION_REDUCED_DISTANCE_SPEC.get();
        ACTIVATION_REDUCED_INTERVAL = ACTIVATION_REDUCED_INTERVAL_SPEC.get();
        ACTIVATION_FAR_INTERVAL = ACTIVATION_FAR_INTERVAL_SPEC.get();
        ACTIVATION_EXEMPT_TYPES = Set.copyOf(ACTIVATION_EXEMPT_TYPES_SPEC.get());
        Map<String, Integer> overrides = new HashMap<>();
        for (String entry : ACTIVATION_TYPE_OVERRIDES_SPEC.get()) {
            Map.Entry<String, Integer> parsed = parseOverride(entry);
            if (parsed != null) { // validator already rejected malformed entries
                overrides.put(parsed.getKey(), parsed.getValue());
            }
        }
        ACTIVATION_TYPE_OVERRIDES = Map.copyOf(overrides);
        ASYNC_PATHFINDING = ASYNC_PATHFINDING_SPEC.get();
        PATHFINDING_THREADS = PATHFINDING_THREADS_SPEC.get();
        ENTITY_SHARDING = ENTITY_SHARDING_SPEC.get();
        ENTITY_SHARD_MIN_BATCH = ENTITY_SHARD_MIN_BATCH_SPEC.get();
        FORCE_ENABLE_MODULES = Set.copyOf(FORCE_ENABLE_MODULES_SPEC.get());
        FORCE_DISABLE_MODULES = Set.copyOf(FORCE_DISABLE_MODULES_SPEC.get());
    }
}
