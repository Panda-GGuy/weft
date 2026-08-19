package dev.weft.neoforge.observability;

import dev.weft.api.telemetry.Collector;
import dev.weft.api.telemetry.MetricSink;
import dev.weft.api.telemetry.WeftTelemetry;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.sandbox.LegacyLane;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.Map;

/**
 * The pulled side of WS-7 (RFC-0009 §11): collectors that read what Weft already
 * measures and hand it to the exporter at scrape time.
 *
 * <p>Every collector here obeys the {@link Collector} thread contract. Anything
 * that would need to walk server-thread-owned state reads
 * {@link WeftObservability#snapshot()} instead, which the server thread publishes
 * once a second (see {@link TelemetrySnapshot} for why that is not optional).
 *
 * <p><b>Absent, not zero</b> (§4). Each collector returns early when its source
 * module is inactive. A zero would be a measurement claim — "the legacy lane cost
 * nothing" — where absence is the truth: Weft is not attributing legacy cost
 * right now.
 */
public final class ObservabilityCollectors {

    private ObservabilityCollectors() {}

    /** Register every pulled source. Called when the module activates. */
    public static void registerAll() {
        WeftTelemetry.register(ProfilerExport.INSTANCE);
        WeftTelemetry.register(ObservabilityCollectors::collectTopology);
        WeftTelemetry.register(ObservabilityCollectors::collectLegacyLane);
        WeftTelemetry.register(ObservabilityCollectors::collectMailAndSections);
        WeftTelemetry.register(ObservabilityCollectors::collectWorkers);
        WeftTelemetry.register(ObservabilityCollectors::collectServices);
        WeftTelemetry.register(ObservabilityCollectors::collectModules);
        WeftTelemetry.register(ObservabilityCollectors::collectExporterHealth);
        if (WeftConfig.JVM_METRICS_ENABLED) {
            WeftTelemetry.register(ObservabilityCollectors::collectJvm);
        }
    }

    // --- regions ---

    private static void collectTopology(MetricSink sink) {
        TelemetrySnapshot snapshot = WeftObservability.snapshot();
        for (TelemetrySnapshot.LevelTopology level : snapshot.levels()) {
            sink.gauge("weft_regions", "Live regions in this level.",
                    "level", level.levelId(), level.regions());
            sink.gauge("weft_region_chunks_loaded",
                    "Loaded chunks mapped to a region in this level.",
                    "level", level.levelId(), level.chunks());
            // The hottest-region-share numerator. RFC-0002 WS-7 names this panel
            // as an acceptance criterion; divide by weft_region_chunks_loaded.
            sink.gauge("weft_region_largest_chunks",
                    "Chunks in this level's largest region.",
                    "level", level.levelId(), level.largestRegion());
            // One series per category and no roll-up series: an "all" alongside
            // the parts is a trap, because summing the family in a dashboard then
            // double-counts. Grafana can sum the parts.
            level.entitiesByCategory().forEach((category, count) ->
                    sink.gauge("weft_entities", "Entities tracked by the census, by mob "
                                    + "category. Category rather than entity type: that is "
                                    + "what the mobcap is expressed in, and a per-type gauge "
                                    + "would mean a full entity walk per scrape.",
                            ENTITY_LABELS, new String[]{level.levelId(), category}, count));
        }
        sink.counter("weft_region_merges_total",
                "Regions absorbed into another by a bridging chunk load.",
                snapshot.regionMerges());
        sink.counter("weft_region_splits_total",
                "Regions shed because a chunk unload disconnected a component.",
                snapshot.regionSplits());
        sink.gauge("weft_region_buckets", "Per-region buckets in the last tick section.",
                "section", "ENTITY", snapshot.entityBuckets());
        sink.gauge("weft_region_buckets", "Per-region buckets in the last tick section.",
                "section", "BLOCK_ENTITY", snapshot.blockEntityBuckets());
        sink.gauge("weft_block_entities_ticking",
                "Block entities the most recent block-entity section ticked.",
                snapshot.blockEntitiesTicking());
    }

    private static final String[] ENTITY_LABELS = {"level", "category"};

    // --- legacy lane: the flagship series ---

    private static void collectLegacyLane(MetricSink sink) {
        if (!LegacyRouting.isActive()) {
            return;
        }
        LegacyLane lane = LegacyRouting.lane();
        Map<String, Long> costByMod = lane.costByModNanos();
        if (costByMod.isEmpty()) {
            return;
        }
        // RFC-0001 §9.1's "your tick is 61% mod X" number, on the wire. The single
        // most valuable thing Weft can tell an operator, and nothing else has it.
        costByMod.forEach((modid, nanos) -> sink.counter("weft_legacy_mod_cost_seconds_total",
                "Legacy-lane execution time attributed to a mod since boot.",
                "modid", modid, nanos / 1e9));
        lane.unitsByMod().forEach((modid, units) -> sink.counter(
                "weft_legacy_extractions_total",
                "Tick units extracted to the legacy lane, by owning mod.",
                "modid", modid, units));
        TelemetrySnapshot snapshot = WeftObservability.snapshot();
        sink.counter("weft_legacy_extracted_units_total",
                "Tick units extracted to the legacy lane, by kind.",
                "kind", "entity", snapshot.legacyExtractedEntities());
        sink.counter("weft_legacy_extracted_units_total",
                "Tick units extracted to the legacy lane, by kind.",
                "kind", "be", snapshot.legacyExtractedBlockEntities());
    }

    // --- mail and owned sections ---

    private static void collectMailAndSections(MetricSink sink) {
        TelemetrySnapshot snapshot = WeftObservability.snapshot();
        String help = "Owner-mail deliveries by outcome (RFC-0007 §3).";
        sink.counter("weft_mail_messages_total", help, MAIL_LABELS,
                new String[]{"task", "routed"}, snapshot.mailRoutedToRegion());
        sink.counter("weft_mail_messages_total", help, MAIL_LABELS,
                new String[]{"task", "inline_fallback"}, snapshot.mailInlineFallback());
        sink.counter("weft_mail_messages_total", help, MAIL_LABELS,
                new String[]{"task", "drained"}, snapshot.mailDrained());
        sink.counter("weft_mail_messages_total", help, MAIL_LABELS,
                new String[]{"task", "flushed_on_deactivate"}, snapshot.mailFlushed());
        sink.counter("weft_owned_sections_total",
                "Vanilla tick sections run under engine ownership.",
                "mode", "serial", snapshot.ownedSerialSections());
        sink.counter("weft_owned_sections_total",
                "Vanilla tick sections run under engine ownership.",
                "mode", "parallel", snapshot.ownedParallelSections());
    }

    private static final String[] MAIL_LABELS = {"type", "outcome"};

    // --- workers ---

    private static void collectWorkers(MetricSink sink) {
        WeftScheduler scheduler = WeftMod.schedulerOrNull();
        if (scheduler == null) {
            return;
        }
        sink.gauge("weft_worker_parallelism", "Engine pool parallelism.",
                "pool", "region", scheduler.poolParallelism());
        sink.gauge("weft_worker_queue_depth",
                "Tasks queued on the engine pool. Non-zero means real backlog; this is "
                        + "deliberately not a utilisation signal (RFC-0009 §3.3).",
                "pool", "region", scheduler.poolQueuedTasks());
        sink.counter("weft_worker_steals_total",
                "Work-stealing steals on the engine pool - monotonic, so a rate is honest.",
                "pool", "region", scheduler.poolStealCount());
    }

    // --- services ---

    private static void collectServices(MetricSink sink) {
        for (TelemetrySnapshot.LevelTopology level : WeftObservability.snapshot().levels()) {
            TelemetrySnapshot.SpawnCounters spawn = level.spawn();
            if (spawn == null) {
                continue; // module inactive: absent, not zero
            }
            String levelId = level.levelId();
            sink.counter("weft_service_requests_total",
                    "Service requests by outcome.", SERVICE_LABELS,
                    new String[]{"spawn_density", levelId, "served"}, spawn.authoritativeTicks());
            sink.counter("weft_service_requests_total",
                    "Service requests by outcome.", SERVICE_LABELS,
                    new String[]{"spawn_density", levelId, "fell_back"}, spawn.fallbackTicks());
            sink.counter("weft_service_requests_total",
                    "Service requests by outcome.", SERVICE_LABELS,
                    new String[]{"spawn_density", levelId, "failed"}, spawn.serviceFailures());
            sink.counter("weft_spawn_density_fallbacks_total",
                    "Ticks that fell back to vanilla's synchronous scan.",
                    "level", levelId, spawn.fallbackTicks());
            // Not a "verify_diff" gauge: the mismatch magnitude is not retained,
            // only the tick counts (RFC-0009 §3.6). 1 - mismatch/ticks is the
            // parity rate, alertable and true.
            sink.counter("weft_spawn_density_parity_ticks_total",
                    "Verify ticks that compared the async result against vanilla's scan.",
                    "level", levelId, spawn.parityTicks());
            sink.counter("weft_spawn_density_parity_mismatch_ticks_total",
                    "Verify ticks where the async result disagreed with vanilla.",
                    "level", levelId, spawn.parityMismatchTicks());
            sink.gauge("weft_spawn_density_latched_off",
                    "1 when the spawn-density service latched itself off for this level.",
                    "level", levelId, spawn.latchedOff() ? 1 : 0);
            // Last measured stage durations, as gauges rather than a histogram:
            // the service retains last values, and a once-a-second sample of a
            // per-tick quantity is not a distribution (RFC-0009 sec. 3).
            sink.gauge("weft_service_last_latency_seconds",
                    "Most recent duration of a service stage.", LATENCY_LABELS,
                    new String[]{"spawn_density", levelId, "capture"},
                    spawn.captureNanos() / 1e9);
            sink.gauge("weft_service_last_latency_seconds",
                    "Most recent duration of a service stage.", LATENCY_LABELS,
                    new String[]{"spawn_density", levelId, "build"}, spawn.buildNanos() / 1e9);
            if (spawn.computeNanos() > 0) {
                sink.gauge("weft_service_last_latency_seconds",
                        "Most recent duration of a service stage.", LATENCY_LABELS,
                        new String[]{"spawn_density", levelId, "compute"},
                        spawn.computeNanos() / 1e9);
            }
            sink.gauge("weft_census_tracked", "Entities in the incremental census.",
                    "level", levelId, spawn.censusTracked());
            sink.counter("weft_census_drift_total",
                    "Census drift measured against full scans.",
                    "level", levelId, spawn.censusDrift());
            sink.counter("weft_census_reconciles_total",
                    "Census reconciliations against a full scan.",
                    "level", levelId, spawn.censusReconciles());
        }
        if (WeftConfig.ASYNC_PATHFINDING) {
            // submitted/filled/no_path are what WS-2 counts. There is deliberately
            // no "coalesced" outcome: nothing increments one (RFC-0009 §3.7).
            sink.counter("weft_path_requests_total",
                    "Async pathfinding requests submitted.",
                    "outcome", "submitted", PathfindingHooks.submittedCount());
        }
    }

    private static final String[] SERVICE_LABELS = {"service", "level", "outcome"};
    private static final String[] LATENCY_LABELS = {"service", "level", "stage"};

    // --- modules ---

    private static void collectModules(MetricSink sink) {
        List<TelemetrySnapshot.ModuleState> modules = WeftObservability.snapshot().modules();
        for (TelemetrySnapshot.ModuleState module : modules) {
            // The state-set pattern: one series per (module, state), 1 for the
            // state it is in. Five states, derived from the same collapse
            // /weft status prints (RFC-0009 §3.9).
            for (String state : MODULE_STATES) {
                sink.gauge("weft_module_state",
                        "1 for the state a module is in, 0 otherwise.", MODULE_LABELS,
                        new String[]{module.module(), state},
                        state.equals(module.state()) ? 1 : 0);
            }
        }
    }

    private static final String[] MODULE_LABELS = {"module", "state"};
    private static final List<String> MODULE_STATES =
            List.of("active", "yielded", "self_disabled", "disabled", "refused");

    // --- the exporter's own health ---

    private static void collectExporterHealth(MetricSink sink) {
        sink.gauge("weft_exporter_build_info", "1, labelled with the running versions.",
                BUILD_LABELS,
                new String[]{WeftObservability.weftVersion(), WeftObservability.mcVersion()}, 1);

        var metrics = WeftObservability.metricsServer();
        if (metrics != null) {
            // One behind by construction: the scrape being served is counted after
            // this renders. That is normal for a self-reporting exporter and worth
            // knowing rather than papering over.
            sink.counter("weft_scrapes_total", "Scrapes served by this endpoint.",
                    metrics.scrapes());
            sink.counter("weft_scrape_duration_seconds_total",
                    "Cumulative time spent collecting and formatting scrapes.",
                    metrics.scrapeNanos() / 1e9);
            // Non-zero means two collectors publish the same series - a bug in
            // Weft, surfaced instead of swallowed.
            sink.counter("weft_scrape_duplicate_series_total",
                    "Series suppressed because an identical name+labels pair was already "
                            + "written this scrape.",
                    metrics.duplicatesDropped());
        }

        var events = WeftObservability.eventSink();
        if (events != null) {
            sink.counter("weft_event_stream_written_total",
                    "Event lines written to disk.", events.written());
            sink.gauge("weft_event_stream_healthy",
                    "1 while the event sink is writable; 0 once a write failure latched it off.",
                    events.healthy() ? 1 : 0);
        }
    }

    private static final String[] BUILD_LABELS = {"weft_version", "mc_version"};

    // --- jvm ---

    /**
     * Cumulative GC counters and heap gauges — no pause histogram. A histogram
     * needs a {@code GarbageCollectionNotificationInfo} listener, and GC-pause
     * attribution is WS-6.2's territory (RFC-0009 §8.2). Meanwhile
     * {@code rate(gc_seconds_total)/rate(gc_collections_total)} is mean pause,
     * from two counters that cost a scrape-time MX bean read and no threads.
     */
    private static void collectJvm(MetricSink sink) {
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count < 0) {
                continue; // the bean declines to answer; absent beats a -1
            }
            sink.counter("weft_jvm_gc_collections_total", "Collections by this collector.",
                    "collector", gc.getName(), count);
            sink.counter("weft_jvm_gc_seconds_total", "Time spent collecting.",
                    "collector", gc.getName(), gc.getCollectionTime() / 1000.0);
        }
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        sink.gauge("weft_jvm_heap_bytes", "Heap size by area.",
                "area", "used", memory.getHeapMemoryUsage().getUsed());
        sink.gauge("weft_jvm_heap_bytes", "Heap size by area.",
                "area", "committed", memory.getHeapMemoryUsage().getCommitted());
        long max = memory.getHeapMemoryUsage().getMax();
        if (max >= 0) {
            sink.gauge("weft_jvm_heap_bytes", "Heap size by area.", "area", "max", max);
        }
    }
}
