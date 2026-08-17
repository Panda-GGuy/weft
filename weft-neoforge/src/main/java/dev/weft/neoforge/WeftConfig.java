package dev.weft.neoforge;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

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

    private static final ModConfigSpec.BooleanValue SPAWN_SERVICE_SHADOW_SPEC = BUILDER
            .comment("P1 shadow mode: recompute the spawn-density scan off-thread each tick",
                    "and compare against vanilla (which stays authoritative). Costs a few",
                    "microseconds of capture per level tick; produces parity data via",
                    "/weft services.")
            .define("spawnServiceShadow", true);

    private static final ModConfigSpec.IntValue CENSUS_RECONCILE_INTERVAL_TICKS_SPEC = BUILDER
            .comment("How often (ticks) the incremental entity census is reconciled against a",
                    "full scan. Drift numbers appear in /weft services. 0 = census disabled.")
            .defineInRange("censusReconcileIntervalTicks", 200, 0, Integer.MAX_VALUE);

    private static final ModConfigSpec.ConfigValue<List<? extends Integer>> SPEEDUP_WORKER_COUNTS_SPEC = BUILDER
            .comment("Worker counts to estimate hypothetical speedup for.")
            .defineListAllowEmpty("speedupWorkerCounts", List.of(2, 4, 8, 16),
                    () -> 4, o -> o instanceof Integer i && i >= 1 && i <= 1024);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // --- cached values (defaults mirror the spec; refreshed on config events) ---

    public static volatile int MERGE_DISTANCE = 8;
    public static volatile int RESERVED_THREADS = 2;
    public static volatile boolean PROFILING_ENABLED = true;
    public static volatile int PROFILE_WINDOW_TICKS = 100;
    public static volatile int REPORT_LOG_INTERVAL_TICKS = 1200;
    public static volatile int REPORT_TOP_TYPES = 12;
    public static volatile boolean SPAWN_SERVICE_SHADOW = true;
    public static volatile int CENSUS_RECONCILE_INTERVAL_TICKS = 200;
    public static volatile int[] SPEEDUP_WORKER_COUNTS = {2, 4, 8, 16};

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
        SPAWN_SERVICE_SHADOW = SPAWN_SERVICE_SHADOW_SPEC.get();
        CENSUS_RECONCILE_INTERVAL_TICKS = CENSUS_RECONCILE_INTERVAL_TICKS_SPEC.get();
        SPEEDUP_WORKER_COUNTS = SPEEDUP_WORKER_COUNTS_SPEC.get().stream()
                .mapToInt(Integer::intValue).toArray();
    }
}
