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

    // --- P2: regionized vanilla ticking (RFC-0001 sec. 11; kill switch per RFC-0003 R1) ---

    private static final ModConfigSpec.BooleanValue REGIONIZED_TICKING_SPEC = BUILDER
            .comment("P2 (RFC-0001 sec. 11): route vanilla entity and block-entity ticking",
                    "through the Weft engine. Increment 1 is deliberately the degenerate",
                    "case - every level's loaded chunks as ONE region, ticked serially on",
                    "the server thread in vanilla's own order (bit-identical by",
                    "construction: it exercises the ownership seams and the vanilla-parity",
                    "suite, not parallelism yet). Ships off until the parity suite",
                    "(RFC-0005) is green for every increment that changes semantics.")
            .define("regionizedTicking", false);

    private static final ModConfigSpec.BooleanValue PARTITIONED_TICKING_SPEC = BUILDER
            .comment("P2 increment 4 (RFC-0001 sec. 4.2/11): with regionizedTicking on,",
                    "group each entity/block-entity section by the real chunk->region",
                    "topology and tick bucket-by-bucket in canonical (ascending region id)",
                    "order under each region's own id - STILL SERIAL, on the server thread.",
                    "Vanilla order is preserved within each region; only cross-region",
                    "interleaving changes, unobservable for regions >= mergeDistance apart.",
                    "The execution shape of parallel regions with the concurrency removed;",
                    "worker fan-out (class E1) is a later increment. No effect unless",
                    "regionizedTicking is active.")
            .define("partitionedTicking", false);

    private static final ModConfigSpec.BooleanValue PARALLEL_REGIONS_SPEC = BUILDER
            .comment("P2 increment 5 (RFC-0006, class E1): run partitioned buckets",
                    "CONCURRENTLY on engine workers, barriered inside each vanilla tick",
                    "section - the server thread waits; vanilla macro-order is unchanged.",
                    "Per-region execution is bit-identical to serial; cross-region",
                    "interleaving and a handful of cosmetic level.random draws are the",
                    "documented order-canonicalized set (RFC-0006 sec. 4). Single-bucket",
                    "sections (solo play: one region) take the serial path - zero new",
                    "overhead. Requires partitionedTicking. Ships off until the parity",
                    "suite, chaos, R7 and the Create/AE2 soak are green under the flag.")
            .define("parallelRegions", false);

    private static final ModConfigSpec.BooleanValue BLOCK_ENTITY_SHARDING_SPEC = BUILDER
            .comment("P2 increment 7 / WS-10 (RFC-0008, class E2): shard a region's",
                    "block-entity ticking ACROSS worker threads within one region - the",
                    "solo-play lever, where region-level parallelism is a no-op because the",
                    "world is one region. Safety is spatial: chunks are 2x2-colored and each",
                    "color runs as its own barriered pass, so concurrently-ticking chunks are",
                    "always >= 16 empty blocks apart - further than any short-reach block",
                    "entity can touch (a hopper reaches one block). Types whose reach can",
                    "exceed that (pistons, beacons, conduits, sculk, end gateways, trial",
                    "spawners, vaults, bells) tick serially afterwards, and access outside a",
                    "shard's own chunks trips a fail-loud counted guard.",
                    "Documented behavior change (RFC-0004 sec. 2.5): block entities in",
                    "ADJACENT chunks tick in different passes, so a hopper chain crossing a",
                    "chunk border can move an item on a different tick than vanilla. Totals",
                    "are conserved and the result is reproducible; vanilla's exact ordering",
                    "is not preserved. Requires partitionedTicking. Ships off until the",
                    "conservation (E2) gate and the throughput number are green.")
            .define("blockEntitySharding", false);

    private static final ModConfigSpec.IntValue BLOCK_ENTITY_SHARD_MIN_UNITS_SPEC = BUILDER
            .comment("Block entities a region must be ticking before sharding engages;",
                    "below this the region takes the serial path (fan-out would cost more",
                    "than it saves).")
            .defineInRange("blockEntityShardMinUnits", 64, 1, 100_000);

    private static final ModConfigSpec.BooleanValue OWNER_MAIL_ROUTING_SPEC = BUILDER
            .comment("P2 increment 6 (RFC-0007 sec. 3): deliver positionally-owned async",
                    "results (WS-2 path fills) to the owning region's OWN mailbox, drained",
                    "by that region's bucket at the head of its section run - replacing",
                    "the parked-main-thread global inbox as the delivery model. Delivery",
                    "still lands before the owner's simulation each tick; unmapped targets",
                    "and target-less work keep the global-inbox path. Requires",
                    "partitionedTicking (the bucket IS the drain point). Ships off until",
                    "the parity suite and the p2mail contract gate are green (RFC-0005).")
            .define("ownerMailRouting", false);

    private static final ModConfigSpec.BooleanValue SINGLE_JOIN_TICK_SPEC = BUILDER
            .comment("P2 increment 7 (RFC-0007 sec. 4, class E1): fuse each region's",
                    "[owner-mail drain -> entity bucket -> block-entity bucket] into ONE",
                    "uninterrupted worker task with a single join per level per tick,",
                    "instead of a cross-region barrier at every vanilla section. Within a",
                    "region, vanilla entity order then vanilla BE order is preserved",
                    "(per-region digests bit-identical); only cross-region SECTION",
                    "interleaving is added to the documented order-canonicalized set.",
                    "SCAFFOLDING: the engine fused-task runner and per-region pending",
                    "containers exist and are unit-tested, but the loader tick path does",
                    "not route through them yet - enabling this arms the seam and its",
                    "probes only, it changes no execution. Requires partitionedTicking",
                    "AND ownerMailRouting (a fused task drains its region's mail as its",
                    "first stage; without routing there is nothing correct to fuse).",
                    "Ships off until the p2fuse gate (two-island free-of-each-other",
                    "probe) and the parity suite are green (RFC-0005).")
            .define("singleJoinTick", false);

    private static final ModConfigSpec.BooleanValue LEGACY_LANE_SPEC = BUILDER
            .comment("P2 (RFC-0001 sec. 7.2): extract Tier-2 (unverified) mods' entity and",
                    "block-entity tick work from the vanilla sections and run it in the",
                    "engine's serialized LEGACY phase - single-threaded, on the server",
                    "thread, between vanilla ticks, with per-mod cost attribution. Vanilla",
                    "and verified content is untouched. Ships off until the parity suite",
                    "and the p2legacy contract gate are green (RFC-0005).")
            .define("legacyLane", false);

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

    // --- WS-7: observability exporter (RFC-0009; kill switch per RFC-0003 R1) ---
    //
    // Every default is chosen so an unaware operator is unaffected and, more
    // importantly, unexposed. The module is off until someone turns it on, and
    // even then it listens on loopback only.

    static {
        BUILDER.comment("WS-7 (RFC-0009): structured telemetry egress - a Prometheus scrape",
                        "endpoint and a newline-delimited JSON event stream. Weft-specific",
                        "series nobody else has: region topology, per-mod legacy-lane cost,",
                        "guard trips, module posture, worker/mail internals.",
                        "Both surfaces are OFF by default and independent (RFC-0003 R1).")
               .push("observability");
    }

    private static final ModConfigSpec.BooleanValue METRICS_ENABLED_SPEC = BUILDER
            .comment("Serve the Prometheus scrape endpoint. Opt-in.")
            .define("metricsEnabled", false);

    private static final ModConfigSpec.ConfigValue<String> METRICS_BIND_ADDRESS_SPEC = BUILDER
            .comment("Address the metrics endpoint binds. LOOPBACK ONLY BY DEFAULT, and",
                    "read this before changing it: the endpoint is deliberately",
                    "unauthenticated (Prometheus convention - a half-built auth scheme is",
                    "worse than none), and it exposes your mod list, player counts and",
                    "world topology. Exposing it to the internet leaks all of that to",
                    "anyone who scans your port. Remote scraping is your explicit",
                    "decision: set this to your private interface, ideally behind your own",
                    "reverse proxy with TLS and auth. \"0.0.0.0\" means every interface.")
            .define("metricsBindAddress", "127.0.0.1");

    private static final ModConfigSpec.IntValue METRICS_PORT_SPEC = BUILDER
            .comment("Port for the metrics endpoint. If it is already taken - another",
                    "exporter, a plugin, anything - Weft logs one line, yields the port and",
                    "self-disables this module (RFC-0003 rung 3). The tick is unaffected.")
            .defineInRange("metricsPort", 9940, 1, 65535);

    private static final ModConfigSpec.BooleanValue JVM_METRICS_ENABLED_SPEC = BUILDER
            .comment("Export weft_jvm_* (heap gauges, cumulative GC counters). Turn this OFF",
                    "if another exporter already covers your JVM, to avoid duplicate scrape",
                    "cost. On by default because no exporter mod ships on NeoForge 1.21.1,",
                    "so Weft is usually the only source (RFC-0009 sec. 8.2). GC *pause",
                    "histograms* are deliberately not here - that needs a notification",
                    "listener and belongs to WS-6.2.")
            .define("jvmMetricsEnabled", true);

    private static final ModConfigSpec.BooleanValue EVENT_STREAM_ENABLED_SPEC = BUILDER
            .comment("Write the newline-delimited JSON event stream: guard trips, module",
                    "state changes, service fallbacks, region merges/splits, config changes,",
                    "tick outliers - the discrete things a 10s gauge sample loses. Point",
                    "Vector/Promtail/Fluent Bit at the file. Opt-in.")
            .define("eventStreamEnabled", false);

    private static final ModConfigSpec.ConfigValue<String> EVENT_STREAM_PATH_SPEC = BUILDER
            .comment("Event stream file, relative to the server directory.")
            .define("eventStreamPath", "logs/weft-events.ndjson");

    private static final ModConfigSpec.IntValue EVENT_STREAM_MAX_MB_SPEC = BUILDER
            .comment("Rotate the event stream at this size. One predecessor (.1) is kept, so",
                    "the stream costs at most twice this on disk and cannot fill it.")
            .defineInRange("eventStreamMaxMB", 128, 1, 16384);

    private static final ModConfigSpec.IntValue MAX_LABEL_CARDINALITY_SPEC = BUILDER
            .comment("Maximum series per metric family before the tail is folded into an",
                    "__other__ bucket, ranked by cost so the expensive ones survive.",
                    "modid and type labels are unbounded in principle; on a 400-mod pack an",
                    "uncapped exporter can OOM a Prometheus instance.")
            .defineInRange("maxLabelCardinality", 50, 1, 10_000);

    private static final ModConfigSpec.DoubleValue TICK_OUTLIER_FACTOR_SPEC = BUILDER
            .comment("Emit a tick_outlier event for any tick exceeding this multiple of the",
                    "rolling median tick duration, with the top cost sources attached.",
                    "Median, not mean: tick durations are right-skewed and one GC pause",
                    "drags a mean while barely moving a median.")
            .defineInRange("tickOutlierFactor", 4.0, 1.01, 1000.0);

    private static final ModConfigSpec.BooleanValue REGION_TIMING_ENABLED_SPEC = BUILDER
            .comment("Time each region bucket and each fan-out barrier (RFC-0009 sec. 9.2).",
                    "This is the one new measurement WS-7 adds to the tick path: two",
                    "System.nanoTime() calls per BUCKET per section - O(buckets), not",
                    "O(units); the P0 profiler pays two per entity. It is what makes",
                    "per-region tick duration, hottest-region share and an honest worker",
                    "utilisation ratio possible at all. No effect while the observability",
                    "module is inactive; off means those series are absent, not zero.")
            .define("regionTimingEnabled", true);

    static {
        BUILDER.pop();
    }

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
    public static volatile boolean REGIONIZED_TICKING = false;
    public static volatile boolean PARTITIONED_TICKING = false;
    public static volatile boolean PARALLEL_REGIONS = false;
    public static volatile boolean BLOCK_ENTITY_SHARDING = false;
    public static volatile int BLOCK_ENTITY_SHARD_MIN_UNITS = 64;
    public static volatile boolean OWNER_MAIL_ROUTING = false;
    public static volatile boolean SINGLE_JOIN_TICK = false;
    public static volatile boolean LEGACY_LANE = false;
    // WS-7 observability (RFC-0009 sec. 7)
    public static volatile boolean METRICS_ENABLED = false;
    public static volatile String METRICS_BIND_ADDRESS = "127.0.0.1";
    public static volatile int METRICS_PORT = 9940;
    public static volatile boolean JVM_METRICS_ENABLED = true;
    public static volatile boolean EVENT_STREAM_ENABLED = false;
    public static volatile String EVENT_STREAM_PATH = "logs/weft-events.ndjson";
    public static volatile int EVENT_STREAM_MAX_MB = 128;
    public static volatile int MAX_LABEL_CARDINALITY = 50;
    public static volatile double TICK_OUTLIER_FACTOR = 4.0;
    public static volatile boolean REGION_TIMING_ENABLED = true;
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
        REGIONIZED_TICKING = REGIONIZED_TICKING_SPEC.get();
        PARTITIONED_TICKING = PARTITIONED_TICKING_SPEC.get();
        PARALLEL_REGIONS = PARALLEL_REGIONS_SPEC.get();
        BLOCK_ENTITY_SHARDING = BLOCK_ENTITY_SHARDING_SPEC.get();
        BLOCK_ENTITY_SHARD_MIN_UNITS = BLOCK_ENTITY_SHARD_MIN_UNITS_SPEC.get();
        OWNER_MAIL_ROUTING = OWNER_MAIL_ROUTING_SPEC.get();
        SINGLE_JOIN_TICK = SINGLE_JOIN_TICK_SPEC.get();
        LEGACY_LANE = LEGACY_LANE_SPEC.get();
        METRICS_ENABLED = METRICS_ENABLED_SPEC.get();
        METRICS_BIND_ADDRESS = METRICS_BIND_ADDRESS_SPEC.get();
        METRICS_PORT = METRICS_PORT_SPEC.get();
        JVM_METRICS_ENABLED = JVM_METRICS_ENABLED_SPEC.get();
        EVENT_STREAM_ENABLED = EVENT_STREAM_ENABLED_SPEC.get();
        EVENT_STREAM_PATH = EVENT_STREAM_PATH_SPEC.get();
        EVENT_STREAM_MAX_MB = EVENT_STREAM_MAX_MB_SPEC.get();
        MAX_LABEL_CARDINALITY = MAX_LABEL_CARDINALITY_SPEC.get();
        TICK_OUTLIER_FACTOR = TICK_OUTLIER_FACTOR_SPEC.get();
        REGION_TIMING_ENABLED = REGION_TIMING_ENABLED_SPEC.get();
        FORCE_ENABLE_MODULES = Set.copyOf(FORCE_ENABLE_MODULES_SPEC.get());
        FORCE_DISABLE_MODULES = Set.copyOf(FORCE_DISABLE_MODULES_SPEC.get());
    }
}
