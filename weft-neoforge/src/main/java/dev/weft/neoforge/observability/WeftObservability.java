package dev.weft.neoforge.observability;

import com.mojang.logging.LogUtils;
import dev.weft.api.telemetry.WeftTelemetry;
import dev.weft.engine.guard.WeftGuards;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.TickPhase;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.engine.telemetry.export.MetricsHttpServer;
import dev.weft.engine.telemetry.export.NdjsonEventSink;
import dev.weft.engine.telemetry.export.TickOutlierDetector;
import dev.weft.engine.telemetry.export.WeftEvent;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.regiontick.OwnerMail;
import dev.weft.neoforge.regiontick.RegionTopology;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.WeftServices;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The WS-7 observability module (RFC-0009). Owns the scrape endpoint, the event
 * sink, and the once-a-second {@link TelemetrySnapshot} that keeps the scrape off
 * server-thread state.
 *
 * <p><b>RFC-0003 compliance.</b> R1: registered in {@code WeftModules} as
 * {@code observability} with its own switch; nothing depends on it and it depends
 * on no sibling — a metric whose source module is off is <em>absent</em>, not
 * zero (§4). R2: <b>no mixins at all</b>, so the only runtime failures are a
 * taken port and an unwritable sink; either logs one line and self-disables
 * (rung 3) while the server runs on. R6: inactive means no thread, no socket, no
 * file handle, no listener, and no allocation on the tick path.
 *
 * <p><b>A telemetry failure must never affect the tick.</b> Every entry point
 * from the tick path is wrapped so that an exception here cannot propagate into
 * vanilla's tick; it latches the module off instead and says why.
 */
public final class WeftObservability {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * How often the server thread rebuilds the snapshot. 20 ticks = 1 s, well
     * inside the 10 s scrape interval it feeds.
     */
    private static final int SNAPSHOT_INTERVAL_TICKS = 20;

    /** Cost sources attached to a {@code tick_outlier} event. */
    private static final int OUTLIER_TOP_SOURCES = 5;

    private WeftObservability() {}

    // --- pushed instruments (looked up once; safe across WeftTelemetry.reset) ---

    /**
     * Head-to-head interval between vanilla ticks — the tick <b>period</b>, not
     * the tick's work.
     *
     * <p>Named carefully, because the obvious name is a lie. {@code tickServer}
     * is called once per {@code runServer} loop iteration and the loop sleeps to
     * hold 20 TPS, so this reads ~50 ms on any server that is keeping up no
     * matter how much work each tick does. An operator seeing that under the name
     * "tick duration" would conclude their tick was saturated. The period is the
     * right input for TPS and for outlier detection (a slow tick stretches it);
     * MSPT is {@code weft_mspt_seconds} below.
     */
    private static final WeftTelemetry.Histograms TICK_PERIOD = WeftTelemetry.histogram(
            "weft_tick_period_seconds",
            "Wall time between consecutive server ticks. This is the tick PERIOD, which "
                    + "includes the loop's sleep - about 50ms on a server keeping up. For "
                    + "time spent working, see weft_mspt_seconds.",
            WeftTelemetry.TICK_SECONDS);

    /**
     * <b>Corrected 2026-08-18, after it falsified a live reading.</b> This was
     * {@code min(20, 1e9 / median)}, and on a server with 45% of its ticks over
     * 50 ms it published a flat <b>20</b> — the operator's own distribution said
     * 14. Two mistakes compounded: a <em>median</em> hides a large minority of
     * overruns, and the <em>cap</em> then pinned the result at exactly the value
     * that means "fine". A gauge that reads healthy while a third of the tick
     * budget is missing is worse than no gauge.
     *
     * <p>Now the mean, uncapped. Values above 20 are real and mean the server is
     * ticking faster than realtime — a gametest server with no inter-tick sleep
     * does exactly that — rather than something to be clamped away.
     */
    private static final WeftTelemetry.Gauges TPS = WeftTelemetry.gauge(
            "weft_tps",
            "Ticks per second from the MEAN tick period over a 30s window, uncapped. "
                    + "Includes overrunning ticks, which is the point: a median-based "
                    + "figure reads 20 on a server missing 45% of its budget. Above 20 "
                    + "means faster than realtime (no inter-tick sleep), not an error.");

    /**
     * The median-based figure, kept as its own series rather than deleted. Paired
     * with {@code weft_tps} it distinguishes two very different servers: a mean
     * far below the median means a minority of very slow ticks, while the two
     * converging means uniform slowness.
     */
    private static final WeftTelemetry.Gauges TPS_MEDIAN = WeftTelemetry.gauge(
            "weft_tps_median",
            "Ticks per second from the MEDIAN tick period, uncapped. Insensitive to a "
                    + "slow minority by design - compare against weft_tps to see whether "
                    + "slowness is uniform or spiky.");

    /**
     * Vanilla's own average tick time — literally the number {@code /tps} and
     * spark show an admin, which is what makes it the right cross-check.
     *
     * <p><b>It understates the load, and the amount is not small.</b> It measures
     * work <em>inside</em> {@code tickServer}, while the main thread also runs
     * queued tasks in {@code waitUntilNextTick}'s drain — where a pre-generator's
     * chunk work lives. Observed on a live server: 25 ms here against a 71 ms mean
     * tick period. So read it with {@code weft_tick_period_seconds}, never alone;
     * the difference between them is main-thread work this number cannot see.
     *
     * <p>An average over vanilla's 100-tick ring, not a per-tick sample, so it is a
     * gauge rather than a histogram: a per-tick work duration would need a second
     * injection into {@code tickServer}, and RFC-0009 8 promises this module
     * needs no mixins at all.
     */
    private static final WeftTelemetry.Gauges MSPT = WeftTelemetry.gauge(
            "weft_mspt_seconds",
            "Vanilla's own average tick time (its 100-tick mean) - the figure /tps and "
                    + "spark report.");

    private static final WeftTelemetry.Histograms PHASE_DURATION = WeftTelemetry.histogram(
            "weft_tick_phase_duration_seconds",
            "Weft engine pipeline phase duration. NOT vanilla's tick sections - "
                    + "see weft_section_duration_seconds.",
            WeftTelemetry.SUBTICK_SECONDS, "phase");

    private static final WeftTelemetry.Histograms LEGACY_LANE_DURATION = WeftTelemetry.histogram(
            "weft_legacy_lane_duration_seconds", "One legacy-lane pass (RFC-0001 7.2).",
            WeftTelemetry.TICK_SECONDS);

    private static final WeftTelemetry.Histograms REGION_CHUNKS = WeftTelemetry.histogram(
            "weft_region_chunks", "Chunks per region, sampled once a second.",
            WeftTelemetry.COUNT_BUCKETS, "level");

    // The §9.2 probe's series. Absent unless regionTimingEnabled — off means no
    // series at all, not a series of zeros.

    private static final WeftTelemetry.Histograms SECTION_DURATION = WeftTelemetry.histogram(
            "weft_section_duration_seconds",
            "Wall time a vanilla tick section spent executing its region buckets. "
                    + "This is the section ruler; weft_tick_phase_duration_seconds measures "
                    + "the engine pipeline, which is a different thing (RFC-0009 3.1).",
            WeftTelemetry.SUBTICK_SECONDS, "section", "level");

    private static final WeftTelemetry.Histograms REGION_TICK_DURATION = WeftTelemetry.histogram(
            "weft_region_tick_duration_seconds", "Wall time one region bucket took.",
            WeftTelemetry.SUBTICK_SECONDS, "level");

    private static final WeftTelemetry.Histograms BARRIER_WAIT = WeftTelemetry.histogram(
            "weft_barrier_wait_seconds",
            "Time the server thread waited for a fanned-out section's slowest bucket.",
            WeftTelemetry.SUBTICK_SECONDS, "section");

    private static final WeftTelemetry.Gauges WORKER_UTILIZATION = WeftTelemetry.gauge(
            "weft_worker_utilization_ratio",
            "Bucket busy time over barrier wall time x buckets, from the last fanned-out "
                    + "section. A real work-conservation figure, not a scrape-time sample of "
                    + "a pool that is idle between sections (RFC-0009 3.3).",
            "pool");

    private static final WeftTelemetry.Counters MODULE_SELFDISABLE = WeftTelemetry.counter(
            "weft_module_selfdisable_total", "Times a module turned itself off mid-flight.",
            "module", "reason");

    private static final WeftTelemetry.Counters EVENTS = WeftTelemetry.counter(
            "weft_event_stream_events_total", "Events accepted onto the event stream.", "kind");

    private static final WeftTelemetry.Counters EVENTS_DROPPED = WeftTelemetry.counter(
            "weft_event_stream_dropped_total",
            "Events dropped: queue full, or sink latched off after a write failure.");

    // --- module state ---

    private static volatile boolean active;
    private static volatile TelemetrySnapshot snapshot = TelemetrySnapshot.EMPTY;
    private static volatile String selfDisabledBecause;
    private static volatile MetricsHttpServer metrics;
    private static volatile NdjsonEventSink events;
    private static volatile TickOutlierDetector outliers;
    private static volatile String serverId = "unknown";
    private static MinecraftServer server;
    private static long lastSnapshotTick = Long.MIN_VALUE;

    /**
     * RFC-0003 R2's runtime check. There are no mixins to fail to apply, so this
     * reports the equivalent for this module: whether its two runtime
     * prerequisites — a bindable port and a writable sink — actually held.
     *
     * <p>Returning false after a self-disable is what makes the coexistence
     * ladder report SELF-DISABLED instead of ACTIVE. Without it the R5 table
     * would print "ACTIVE (self-disabled: ...)", which is the exact
     * needs-a-debugger confusion R5 exists to prevent.
     */
    public static boolean hooksApplied() {
        return selfDisabledBecause == null;
    }

    /**
     * Captured at server start, before {@code WeftModules.resolve} runs, so the
     * module knows where to write and which server it is.
     */
    public static void onServerAboutToStart(MinecraftServer starting) {
        server = starting;
        serverId = resolveServerId(starting.getServerDirectory());
        lastSnapshotTick = Long.MIN_VALUE;
        selfDisabledBecause = null;
    }

    /**
     * A config reload clears the self-disable latch so the next resolution
     * retries.
     *
     * <p>Without this, an operator who hits a port collision, changes
     * {@code metricsPort} and reloads would stay disabled until a full restart —
     * because {@link #hooksApplied()} would still be reporting the old failure. A
     * reload is an explicit operator action, which is exactly when a retry is
     * warranted; a failure that recurs simply latches again and logs again.
     */
    public static void onConfigReload() {
        selfDisabledBecause = null;
    }

    /**
     * Wired as the module's {@code applyActive} (RFC-0003 R6). Idempotent: the
     * coexistence ladder re-resolves on every config reload.
     */
    public static void setActive(boolean value) {
        if (value == active) {
            return;
        }
        if (value) {
            start();
        } else {
            stop("module deactivated");
        }
    }

    public static boolean isActive() {
        return active;
    }

    private static synchronized void start() {
        boolean wantMetrics = WeftConfig.METRICS_ENABLED;
        boolean wantEvents = WeftConfig.EVENT_STREAM_ENABLED;
        if (!wantMetrics && !wantEvents) {
            // Nothing asked for. Not a failure — just nothing to do.
            return;
        }
        WeftTelemetry.setEnabled(true);
        outliers = new TickOutlierDetector(WeftConfig.TICK_OUTLIER_FACTOR);

        if (wantMetrics) {
            MetricsHttpServer server = new MetricsHttpServer(WeftConfig.MAX_LABEL_CARDINALITY,
                    (collector, error) -> LOGGER.warn(
                            "Weft observability: collector {} failed; skipped for this scrape.",
                            collector.getClass().getSimpleName(), error));
            try {
                server.start(WeftConfig.METRICS_BIND_ADDRESS, WeftConfig.METRICS_PORT);
                metrics = server;
                LOGGER.info("Weft metrics endpoint on http://{}{} ({}).", server.boundAddress(),
                        MetricsHttpServer.METRICS_PATH,
                        "127.0.0.1".equals(WeftConfig.METRICS_BIND_ADDRESS)
                                ? "loopback only" : "REACHABLE OFF-HOST - unauthenticated");
            } catch (BindException e) {
                // The one real conflict with a neighbouring exporter, detected by
                // binding rather than by modid (RFC-0009 8.1). We cannot name the
                // holder without process inspection, which RFC-0003 4 forbids -
                // so report the port, not the culprit.
                selfDisable("metrics port " + WeftConfig.METRICS_BIND_ADDRESS + ":"
                        + WeftConfig.METRICS_PORT + " is already in use by another process on "
                        + "this host (another exporter, a plugin, anything). Yielding the port; "
                        + "set observability.metricsPort to something else to use it.",
                        "port_in_use");
                return;
            } catch (IOException e) {
                selfDisable("could not bind the metrics endpoint: " + e, "bind_failed");
                return;
            }
        }

        if (wantEvents) {
            Path path = eventStreamPath();
            try {
                events = new NdjsonEventSink(path,
                        WeftConfig.EVENT_STREAM_MAX_MB * 1024L * 1024L,
                        serverId, weftVersion(), mcVersion());
                LOGGER.info("Weft event stream writing {} (rotating at {} MB).",
                        path, WeftConfig.EVENT_STREAM_MAX_MB);
            } catch (IOException e) {
                selfDisable("could not open the event stream at " + path + ": " + e,
                        "sink_unwritable");
                return;
            }
        }

        ObservabilityCollectors.registerAll();
        installListeners();
        active = true;
        emitStartupPosture();
    }

    private static synchronized void stop(String why) {
        active = false;
        removeListeners();
        MetricsHttpServer runningMetrics = metrics;
        metrics = null;
        if (runningMetrics != null) {
            runningMetrics.close();
        }
        NdjsonEventSink runningEvents = events;
        events = null;
        if (runningEvents != null) {
            runningEvents.close();
        }
        outliers = null;
        snapshot = TelemetrySnapshot.EMPTY;
        previousTickStartNanos = 0L;
        // R6: nothing left to publish into, and no residue on any tick path.
        WeftTelemetry.setEnabled(false);
        WeftTelemetry.reset();
        LOGGER.debug("Weft observability stopped: {}", why);
    }

    /** Server stop: drop every handle and every counter from this world. */
    public static void onServerStopping() {
        if (active) {
            stop("server stopping");
        }
        server = null;
    }

    /**
     * Rung 3: turn ourselves off, log exactly once, keep the server running.
     * A telemetry failure must never be a server failure.
     */
    private static void selfDisable(String because, String reason) {
        selfDisabledBecause = because;
        LOGGER.warn("Weft observability self-disabled: {}", because);
        MODULE_SELFDISABLE.inc("observability", reason);
        stop(because);
    }

    /** R5 detail for the posture table and {@code /weft status}. */
    public static String statusDetail() {
        String failure = selfDisabledBecause;
        if (failure != null) {
            return "self-disabled: " + failure;
        }
        if (!active) {
            return WeftConfig.METRICS_ENABLED || WeftConfig.EVENT_STREAM_ENABLED
                    ? "" : "both surfaces off in config";
        }
        List<String> parts = new ArrayList<>(3);
        MetricsHttpServer runningMetrics = metrics;
        if (runningMetrics != null) {
            parts.add("metrics on " + runningMetrics.boundAddress()
                    + MetricsHttpServer.METRICS_PATH
                    + " (" + runningMetrics.scrapes() + " scrapes)");
        }
        NdjsonEventSink sink = events;
        if (sink != null) {
            parts.add(sink.healthy()
                    ? "events -> " + sink.path().getFileName() + " (" + sink.written()
                            + " written, " + sink.dropped() + " dropped)"
                    : "event sink FAILED: " + sink.latchedOffBecause());
        }
        return String.join("; ", parts);
    }

    // --- tick-path entry points (all guarded; see the class note) ---

    /** Wall clock of the previous tick head; 0 until the module has seen one. */
    private static long previousTickStartNanos;

    /**
     * Called at the head of every vanilla tick from
     * {@code WeftMod.onVanillaTick}.
     *
     * <p><b>One volatile read when inactive</b>, and nothing else — not even a
     * {@code System.nanoTime()}. The clock read lives inside the active branch on
     * purpose: R6 says a disabled module must leave the tick path unable to tell
     * it exists, and 20 clock reads a second is exactly the kind of residue that
     * gets waved through and then quoted back at us.
     *
     * <p>The first tick after activation has no predecessor to measure against, so
     * it is skipped rather than charged a bogus duration.
     */
    public static void onVanillaTickStart(long tickNumber) {
        if (!active) {
            return;
        }
        long now = System.nanoTime();
        long elapsed = previousTickStartNanos == 0L ? 0L : now - previousTickStartNanos;
        previousTickStartNanos = now;
        onTickBoundary(tickNumber, elapsed);
    }

    private static void onTickBoundary(long tickNumber, long previousTickNanos) {
        if (!active) {
            return;
        }
        try {
            if (previousTickNanos > 0) {
                TICK_PERIOD.observeNanos(previousTickNanos);
                TickOutlierDetector detector = outliers;
                if (detector != null) {
                    if (detector.observe(previousTickNanos)) {
                        emitTickOutlier(tickNumber, previousTickNanos, detector);
                    }
                    long median = detector.medianNanos();
                    if (median > 0) {
                        TPS_MEDIAN.set(1e9 / median);
                    }
                    long mean = detector.meanNanos();
                    if (mean > 0) {
                        // Uncapped, and from the mean: see the TPS field note.
                        TPS.set(1e9 / mean);
                    }
                }
            }
            MinecraftServer running = server;
            if (running != null) {
                MSPT.set(running.getAverageTickTimeNanos() / 1e9);
            }
            recordPhaseTimings();
            // The explicit never-built check is load-bearing: with
            // lastSnapshotTick at Long.MIN_VALUE, `tickNumber - lastSnapshotTick`
            // OVERFLOWS to a negative number, the interval test is never true,
            // and the snapshot stays EMPTY forever — which silently costs the
            // exporter every per-level and per-module series while the pushed
            // instruments keep working, so the endpoint looks healthy. The
            // p2observability gametest caught exactly this.
            if (lastSnapshotTick == Long.MIN_VALUE
                    || tickNumber - lastSnapshotTick >= SNAPSHOT_INTERVAL_TICKS) {
                lastSnapshotTick = tickNumber;
                snapshot = buildSnapshot(tickNumber);
            }
        } catch (RuntimeException e) {
            selfDisable("telemetry collection threw on the tick path: " + e, "tick_path_error");
        }
    }

    /** Called at the end of each legacy-lane pass. */
    public static void onLegacyLanePass(long passNanos) {
        if (active && passNanos > 0) {
            LEGACY_LANE_DURATION.observeNanos(passNanos);
        }
    }

    /**
     * The §9.2 probe's gate. Both conditions, because the operator can decline the
     * probe while keeping the rest of the exporter.
     */
    public static boolean regionTimingActive() {
        return active && WeftConfig.REGION_TIMING_ENABLED;
    }

    /**
     * One vanilla tick section's bucket timings (RFC-0009 §9.2).
     *
     * @param bucketNanos     wall time per region bucket
     * @param sectionNanos    wall time of the whole bucket run, i.e. what the
     *                        vanilla section paid
     * @param fannedOut       whether buckets ran concurrently; a serial run has no
     *                        barrier, so no barrier wait is reported for it
     */
    public static void onSectionBuckets(String levelId, String sectionKind,
                                        long[] bucketNanos, long sectionNanos,
                                        boolean fannedOut) {
        if (!active) {
            return;
        }
        try {
            SECTION_DURATION.observeNanos(sectionNanos, sectionKind, levelId);
            long busy = 0L;
            for (long nanos : bucketNanos) {
                REGION_TICK_DURATION.observeNanos(nanos, levelId);
                busy += nanos;
            }
            if (!fannedOut || bucketNanos.length == 0 || sectionNanos <= 0) {
                return;
            }
            // Barrier wait: what the server thread paid waiting for the slowest
            // bucket. The coordinator's own wall time IS that number, and it is
            // the one the tick actually pays.
            BARRIER_WAIT.observeNanos(sectionNanos, sectionKind);
            // A real work-conservation ratio over the interval that matters,
            // rather than a scrape-time sample of a pool that is idle between
            // sections (§3.3). 1.0 means every bucket ran the whole barrier.
            double ratio = busy / ((double) sectionNanos * bucketNanos.length);
            WORKER_UTILIZATION.set(Math.min(1.0, ratio), "region");
        } catch (RuntimeException e) {
            selfDisable("region timing threw on the tick path: " + e, "probe_error");
        }
    }

    private static void recordPhaseTimings() {
        WeftScheduler scheduler = WeftMod.schedulerOrNull();
        if (scheduler == null) {
            return;
        }
        for (Map.Entry<TickPhase, Long> entry : scheduler.lastPhaseTimings().entrySet()) {
            PHASE_DURATION.observeNanos(entry.getValue(), entry.getKey().name());
        }
    }

    // --- snapshot ---

    /** The most recent snapshot. Read by collectors on the scrape thread. */
    public static TelemetrySnapshot snapshot() {
        return snapshot;
    }

    /** The live endpoint, or null when metrics are off. Read by the health collector. */
    static MetricsHttpServer metricsServer() {
        return metrics;
    }

    /** The live event sink, or null when the stream is off. */
    static NdjsonEventSink eventSink() {
        return events;
    }

    private static TelemetrySnapshot buildSnapshot(long tick) {
        MinecraftServer running = server;
        List<TelemetrySnapshot.LevelTopology> levels = new ArrayList<>();
        long merges = 0;
        long splits = 0;
        if (running != null) {
            WeftServices services = WeftMod.servicesOrNull();
            for (ServerLevel level : running.getAllLevels()) {
                RegionManager manager = RegionTopology.managerFor(level);
                List<Integer> chunkCounts = new ArrayList<>();
                for (Region region : manager.all()) {
                    chunkCounts.add(region.chunks().size());
                }
                String levelId = level.dimension().location().toString();
                // Observed here rather than at scrape time so the distribution is
                // sampled at a steady cadence instead of at whatever moment a
                // scraper happens to ask.
                for (int count : chunkCounts) {
                    REGION_CHUNKS.observe(count, levelId);
                }
                merges += manager.merges();
                splits += manager.splits();
                levels.add(new TelemetrySnapshot.LevelTopology(levelId,
                        manager.all().size(), manager.chunkCount(),
                        chunkCounts.stream().max(Comparator.naturalOrder()).orElse(0),
                        List.copyOf(chunkCounts),
                        censusByCategory(services, level),
                        spawnCountersOf(services, level)));
            }
        }

        Map<String, Long> phases = new LinkedHashMap<>();
        WeftScheduler scheduler = WeftMod.schedulerOrNull();
        if (scheduler != null) {
            for (Map.Entry<TickPhase, Long> e : new EnumMap<>(scheduler.lastPhaseTimings())
                    .entrySet()) {
                phases.put(e.getKey().name(), e.getValue());
            }
        }

        long[] entityPartition = RegionizedTicking.lastEntityPartition();
        long[] bePartition = RegionizedTicking.lastBlockEntityPartition();
        // NOT a sum over bePartition: that array holds region IDS, and summing
        // them published "6" on a three-region world (1+2+3) as a block-entity
        // count while the level ticked thousands. The partitioner now reports the
        // real captured unit count.
        int tickingBlockEntities = RegionizedTicking.lastBlockEntityUnits();

        return new TelemetrySnapshot(tick, List.copyOf(levels), moduleStates(), phases,
                merges, splits,
                scheduler == null ? 0L : scheduler.ownedSerialSections(),
                scheduler == null ? 0L : scheduler.ownedParallelSections(),
                OwnerMail.routedToRegion(), OwnerMail.inlineFallback(),
                OwnerMail.drainedTasks(), OwnerMail.flushedTasks(),
                LegacyRouting.deferredEntities(), LegacyRouting.deferredBlockEntities(),
                entityPartition.length, bePartition.length, tickingBlockEntities);
    }

    /**
     * Census counts, or an empty map when nothing is feeding the census.
     *
     * <p>Absent, not zero (§4). The census is event-fed by the P1 services, so
     * with {@code spawn_density} inactive it is legitimately empty — and
     * exporting {@code weft_entities{category="monster"} 0} would claim the world
     * has no monsters, which is a measurement Weft has not made.
     */
    private static Map<String, Integer> censusByCategory(WeftServices services,
                                                         ServerLevel level) {
        if (services == null
                || !dev.weft.neoforge.service.SpawnDensityHooks.moduleActive()) {
            return Map.of();
        }
        return services.censusStats(level).byCategory();
    }

    private static TelemetrySnapshot.SpawnCounters spawnCountersOf(WeftServices services,
                                                                  ServerLevel level) {
        // Absent, not zero (RFC-0009 4): with the spawn-density module inactive
        // there is nothing to report, and reporting zeros would claim otherwise.
        if (services == null || !dev.weft.neoforge.service.SpawnDensityHooks.moduleActive()) {
            return null;
        }
        WeftServices.SpawnStats spawn = services.spawnStats(level);
        WeftServices.CensusStats census = services.censusStats(level);
        WeftServices.ServiceLatency latency = services.serviceLatency(level);
        return new TelemetrySnapshot.SpawnCounters(spawn.authoritativeTicks(),
                spawn.fallbackTicks(), spawn.parityTicks(), spawn.parityMismatchTicks(),
                spawn.serviceFailures(), spawn.latchedOff(),
                census.tracked(), census.drift(), census.reconciles(),
                latency.captureNanos(), latency.buildNanos(), latency.computeNanos());
    }

    /** Module postures, from the same collapse {@code /weft status} prints. */
    private static List<TelemetrySnapshot.ModuleState> moduleStates() {
        List<TelemetrySnapshot.ModuleState> out = new ArrayList<>();
        dev.weft.neoforge.coexist.WeftModules.lastResolutions().forEach((module, state) ->
                out.add(new TelemetrySnapshot.ModuleState(module, wireState(state))));
        return List.copyOf(out);
    }

    // --- events ---

    /** Queue one event. Never throws, never blocks (RFC-0009 §5). */
    public static void emit(WeftEvent event) {
        NdjsonEventSink sink = events;
        if (sink == null) {
            return;
        }
        if (sink.emit(event)) {
            EVENTS.inc(event.kind().wireName());
        } else {
            EVENTS_DROPPED.inc();
        }
    }

    private static void installListeners() {
        // Only now does a guard trip pay for its stack walk (R6).
        WeftGuards.setTripListener(WeftObservability::onGuardTrip);
        RegionTopology.setTopologyObserver(WeftObservability::onTopologyChange);
        dev.weft.neoforge.coexist.WeftModules.setStateChangeObserver(
                WeftObservability::onModuleStateChange);
    }

    private static void removeListeners() {
        WeftGuards.setTripListener(null);
        RegionTopology.setTopologyObserver(null);
        dev.weft.neoforge.coexist.WeftModules.setStateChangeObserver(null);
    }

    /**
     * A module changed posture mid-flight - a yield, a self-disable, a user
     * override taking effect on a config reload. Exactly the kind of thing a 10s
     * gauge sample can miss entirely.
     */
    private static void onModuleStateChange(String module, String from, String to,
                                            String detail) {
        emit(WeftEvent.of(WeftEvent.Kind.MODULE_STATE_CHANGE)
                .put("module", module)
                .put("from", wireState(from))
                .put("to", wireState(to))
                .put("reason", detail == null || detail.isEmpty() ? null : detail)
                .build());
    }

    /** The R5 label as the schema spells it: lower-case, underscores. */
    private static String wireState(String label) {
        return label.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static void onGuardTrip(WeftGuards.GuardTrip trip) {
        emit(WeftEvent.of(WeftEvent.Kind.GUARD_TRIP)
                .put("kind", WeftGuards.wire(trip.kind()))
                .put("severity", WeftGuards.wire(trip.severity()))
                .put("thread", trip.thread())
                .put("context_kind", trip.contextKind().name())
                .put("context_owner", trip.contextOwner())
                .put("target_kind", trip.targetKind())
                .put("target_id", trip.targetId())
                .put("degradation", WeftGuards.wire(trip.degradation()))
                .put("stack", trip.stack())
                .build());
    }

    /** Region merge/split, with the level label {@code RegionManager} cannot know. */
    private static void onTopologyChange(String levelId, boolean merge, long sourceId,
                                        long[] otherIds, int chunksAfter) {
        emit(WeftEvent.of(merge ? WeftEvent.Kind.REGION_MERGE : WeftEvent.Kind.REGION_SPLIT)
                .put("level", levelId)
                .put("region_ids", merge ? boxed(otherIds) : List.of(sourceId))
                .put("result_ids", merge ? List.of(sourceId) : boxed(otherIds))
                .put("chunks_after", chunksAfter)
                .build());
    }

    private static List<Long> boxed(long[] values) {
        List<Long> out = new ArrayList<>(values.length);
        for (long value : values) {
            out.add(value);
        }
        return out;
    }

    /** RFC-0003 R5's table, once at boot, as data. */
    private static void emitStartupPosture() {
        List<Object> modules = new ArrayList<>();
        for (TelemetrySnapshot.ModuleState state : moduleStates()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("module", state.module());
            row.put("state", state.state());
            modules.add(row);
        }
        if (modules.isEmpty()) {
            return;
        }
        emit(WeftEvent.of(WeftEvent.Kind.STARTUP_POSTURE).put("modules", modules).build());
    }

    private static void emitTickOutlier(long tick, long tickNanos,
                                        TickOutlierDetector detector) {
        WeftEvent.Builder builder = WeftEvent.of(WeftEvent.Kind.TICK_OUTLIER)
                .put("tick", tick)
                .put("duration_seconds", tickNanos / 1e9)
                .put("median_seconds", detector.medianNanos() / 1e9)
                .put("factor", (double) tickNanos / Math.max(1L, detector.medianNanos()));
        List<Object> topSources = ProfilerExport.topSources(OUTLIER_TOP_SOURCES);
        if (!topSources.isEmpty()) {
            // The information a human always wants about a spike and never has.
            builder.put("top_sources", topSources);
        }
        Map<String, Object> phases = new LinkedHashMap<>();
        snapshot.phaseNanos().forEach((phase, nanos) -> phases.put(phase, nanos / 1e9));
        if (!phases.isEmpty()) {
            builder.put("phase_breakdown", phases);
        }
        emit(builder.build());
    }

    // --- identity ---

    /**
     * A random UUID, generated once and persisted beside the world.
     *
     * <p>Deliberately not derived from IP, hostname, MAC or player data: an event
     * stream an operator ships to a log aggregator must not smuggle identifying
     * data, and a stable random id serves every legitimate purpose (correlating a
     * regression against a deployment) that a derived one would.
     */
    private static String resolveServerId(Path serverDirectory) {
        Path file = serverDirectory.resolve("weft-server-id.txt");
        try {
            if (Files.exists(file)) {
                String existing = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    return existing;
                }
            }
            String fresh = UUID.randomUUID().toString();
            Files.writeString(file, fresh, StandardCharsets.UTF_8);
            return fresh;
        } catch (IOException e) {
            // A missing id is not worth failing telemetry over; the stream is
            // still useful, it just cannot be correlated across restarts.
            LOGGER.warn("Weft observability could not persist a server id in {}: {}",
                    file, e.toString());
            return UUID.randomUUID().toString();
        }
    }

    private static Path eventStreamPath() {
        MinecraftServer running = server;
        Path base = running != null ? running.getServerDirectory() : Path.of(".");
        return base.resolve(WeftConfig.EVENT_STREAM_PATH).normalize();
    }

    static String weftVersion() {
        return ModList.get().getModContainerById("weft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    static String mcVersion() {
        return SharedConstants.getCurrentVersion().getName();
    }
}
