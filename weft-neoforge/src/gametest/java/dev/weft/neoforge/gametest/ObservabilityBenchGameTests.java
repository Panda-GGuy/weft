package dev.weft.neoforge.gametest;

import dev.weft.engine.telemetry.SectionSamples;
import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.observability.WeftObservability;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.profiler.WeftProfiler;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WS-7's overhead gate (RFC-0009 §9.3). RFC-0002's acceptance criterion is
 * "overhead unmeasurable at 10 s scrape interval"; this makes it a measured claim
 * rather than an assertion, and records it on {@code bench-data} so a future
 * regression fails CI like every other Weft benchmark.
 *
 * <p><b>Interleaved A/B/A/B, in one run.</b> Cross-run deltas on shared runners
 * are mostly variance — the WS-2 flat-world reading showed that, and P2's
 * retracted 1.59x is the standing reminder. But same-run A/B alone was <em>also
 * not enough</em>: two phases leave warmup <em>order</em> bias, where whichever
 * phase runs second benefits from a warmer JIT. Six alternating phases with the
 * two of each condition pooled cancel that, and
 * {@link SectionSamples#stats(int)}'s {@code skipLeading} drops the ticks around
 * each flag flip, which belong to neither phase.
 *
 * <p><b>Median and p95, not mean.</b> Tick durations are right-skewed; one GC
 * pause drags a mean while barely moving a median, and the honest claim about a
 * telemetry module is about the tail as much as the centre.
 *
 * <p>The exporter is scraped throughout the ON phases at the same 10 s cadence
 * the criterion names, so the measurement includes collection, formatting and
 * response — not just the collectors sitting idle.
 *
 * <p><b>And a negative control.</b> A third condition scrapes every tick, 200x
 * the shipping cadence. RFC-0002 asks for overhead that is *unmeasurable*, and a
 * null result is only evidence if the harness can demonstrate what it does
 * resolve — otherwise "we saw nothing" and "the instrument is broken" produce
 * identical output. Two early readings of the 10 s figure came back +0.08%% and
 * +2.56%%, a spread wider than the effect, which is exactly why the control is
 * here rather than a confident number in the README.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class ObservabilityBenchGameTests {

    private static final int WARMUP_TICKS = 100;
    /** Measured ticks per phase; six phases, so 6x this plus warmup. */
    private static final int PHASE_TICKS = 200;
    /**
     * Ticks discarded at the head of each phase. The tick where the flag flips
     * belongs to neither phase, and the next few run with the other path's branch
     * predictions still warm — charging them to a phase is exactly the
     * attribution error that produced the retracted 1.59x.
     */
    private static final int SKIP_LEADING = 5;
    /** Scrape every 200 ticks = 10 s, the interval the criterion names. */
    private static final int SCRAPE_INTERVAL_TICKS = 200;
    /**
     * The negative control's cadence: a scrape every tick, 200x the shipping
     * interval.
     *
     * <p>Without a control, "we could not measure the overhead" is
     * indistinguishable from "our instrument is broken" — and RFC-0002's
     * criterion is literally *unmeasurable at 10 s*, so a null result is only
     * evidence if the harness can show what it CAN resolve. Same reasoning as
     * RFC-0005's vanilla-vs-vanilla control and WS-1's collapse detector.
     */
    private static final int HOT_SCRAPE_INTERVAL_TICKS = 1;

    private static final int POPULATION = 1200;

    @GameTest(template = "empty", batch = "p2observabilitybench", timeoutTicks = 3000,
            required = false)
    public void observabilityOverhead(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = WeftBenchGameTests.groundOrigin(helper);
        BenchmarkWorld.configure(level);
        WeftBenchGameTests.forceChunks(level, origin, true);
        List<Mob> population =
                BenchmarkWorld.spawnCountablePassive(level, origin, POPULATION);
        LoadBot bot = LoadBot.join(level, origin, "WeftWs7Bot");

        // Isolate the exporter: the profiler stays on (it is one of the sources
        // the exporter reads, so measuring without it would measure the wrong
        // thing), everything else that could move MSPT stays off.
        WeftConfig.PROFILING_ENABLED = true;
        WeftConfig.PROFILE_WINDOW_TICKS = PHASE_TICKS + 64;

        int port = freePort();
        SectionSamples off = new SectionSamples(4 * PHASE_TICKS);
        SectionSamples on = new SectionSamples(4 * PHASE_TICKS);
        SectionSamples hot = new SectionSamples(4 * PHASE_TICKS);
        long[] phaseStart = new long[1];
        // Scraped from its OWN thread, because that is where a scrape happens in
        // production: Prometheus is another process and the exporter answers on
        // its own daemon thread, so the server thread never waits for a response.
        // An earlier version of this harness called the endpoint from inside
        // onEachTick and measured the server thread blocking on a localhost HTTP
        // round trip - ~56 ms per scrape, none of which a real tick ever pays.
        // That would have shipped an alarming number describing the test rig.
        TickPacedScraper scraper = new TickPacedScraper("http://127.0.0.1:" + port + "/metrics");

        configureExporter(port, level);
        helper.onEachTick(() -> bot.tickCircle(12.0));

        // Six interleaved phases: OFF, ON@10s, ON@every-tick, twice over.
        // Pooling the two of each cancels warmup-order bias, which same-run A/B
        // with only two phases does not.
        helper.runAfterDelay(WARMUP_TICKS, () -> phaseStart[0] = tick());

        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            collect(off, phaseStart[0]);
            startExporter(port, level);
            scraper.cadence(SCRAPE_INTERVAL_TICKS);
            phaseStart[0] = tick();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            collect(on, phaseStart[0]);
            scraper.cadence(HOT_SCRAPE_INTERVAL_TICKS);
            phaseStart[0] = tick();
        });

        helper.runAfterDelay(WARMUP_TICKS + 3 * PHASE_TICKS, () -> {
            collect(hot, phaseStart[0]);
            scraper.cadence(0);
            stopExporter();
            phaseStart[0] = tick();
        });

        helper.runAfterDelay(WARMUP_TICKS + 4 * PHASE_TICKS, () -> {
            collect(off, phaseStart[0]);
            startExporter(port, level);
            scraper.cadence(SCRAPE_INTERVAL_TICKS);
            phaseStart[0] = tick();
        });

        helper.runAfterDelay(WARMUP_TICKS + 5 * PHASE_TICKS, () -> {
            collect(on, phaseStart[0]);
            scraper.cadence(HOT_SCRAPE_INTERVAL_TICKS);
            phaseStart[0] = tick();
        });

        helper.runAfterDelay(WARMUP_TICKS + 6 * PHASE_TICKS, () -> {
            collect(hot, phaseStart[0]);
            scraper.cadence(0);
            stopExporter();
            scraper.close();
            restoreModules();

            WeftConfig.PROFILE_WINDOW_TICKS = 100;
            population.forEach(net.minecraft.world.entity.Entity::discard);
            bot.leave();
            WeftBenchGameTests.forceChunks(level, origin, false);

            SectionSamples.Stats offStats = off.stats(0);
            SectionSamples.Stats onStats = on.stats(0);
            SectionSamples.Stats hotStats = hot.stats(0);
            if (offStats == null || onStats == null || hotStats == null) {
                helper.fail("A phase produced no samples (off=" + off.count() + ", on="
                        + on.count() + ", hot=" + hot.count() + ") - the A/B never ran");
                return;
            }
            // Vacuous-run guards. Zero overhead for the wrong reason is the
            // failure mode here: an exporter nobody scraped costs nothing, and so
            // does a world that never ticked.
            long shippingScrapes = scraper.served(SCRAPE_INTERVAL_TICKS);
            long controlScrapes = scraper.served(HOT_SCRAPE_INTERVAL_TICKS);
            if (shippingScrapes < 2) {
                helper.fail("Only " + shippingScrapes + " scrapes served across two ON phases "
                        + "- the measurement did not include a scrape, so 'unmeasurable "
                        + "overhead' would be meaningless");
            }
            if (controlScrapes < PHASE_TICKS / 4) {
                helper.fail("The control served only " + controlScrapes + " scrapes across "
                        + (2 * PHASE_TICKS) + " control ticks - it is not the load it claims "
                        + "to be, so it cannot calibrate anything");
            }
            if (off.dropped() > 0 || on.dropped() > 0 || hot.dropped() > 0) {
                helper.fail("Sample windows overflowed (off " + off.dropped() + ", on "
                        + on.dropped() + ", hot " + hot.dropped() + ") - the phases are not "
                        + "comparable");
            }

            double offMedian = offStats.medianMillis();
            double onMedian = onStats.medianMillis();
            double hotMedian = hotStats.medianMillis();
            double deltaPct = 100.0 * (onMedian / offMedian - 1.0);
            double controlDeltaPct = 100.0 * (hotMedian / offMedian - 1.0);

            String shape = String.format(Locale.ROOT,
                    "6 interleaved phases (OFF / ON@%dt / ON@%dt, twice), %d ticks each, "
                            + "%d skipped per phase, region timing on, %d passive mobs + 1 bot",
                    SCRAPE_INTERVAL_TICKS, HOT_SCRAPE_INTERVAL_TICKS, PHASE_TICKS,
                    SKIP_LEADING, POPULATION);
            String detail = String.format(Locale.ROOT,
                    "OFF median %.3f ms (p95 %.3f, n=%d); ON@10s median %.3f ms (p95 %.3f, "
                            + "n=%d, %d scrapes) = %+.2f%%; CONTROL ON@every-tick median "
                            + "%.3f ms (p95 %.3f, n=%d, %d scrapes) = %+.2f%%. %s",
                    offMedian, offStats.p95Millis(), off.count(),
                    onMedian, onStats.p95Millis(), on.count(), shippingScrapes, deltaPct,
                    hotMedian, hotStats.p95Millis(), hot.count(), controlScrapes,
                    controlDeltaPct, shape);

            BenchRecorder.record(level.getServer(),
                    "ws7_exporter_overhead_mspt_off", "ms/tick", offMedian, detail);
            BenchRecorder.record(level.getServer(),
                    "ws7_exporter_overhead_mspt_on", "ms/tick", onMedian, detail);
            BenchRecorder.record(level.getServer(),
                    "ws7_exporter_overhead_pct", "percent", deltaPct,
                    "RFC-0002 WS-7 acceptance: overhead unmeasurable at 10s scrape interval. "
                            + detail);
            // The control's delta is what makes the 10s number interpretable: a
            // null result only counts as evidence if the same harness resolves a
            // load it should resolve. Recorded so bench-data tracks the harness's
            // sensitivity alongside the figure it produces.
            BenchRecorder.record(level.getServer(),
                    "ws7_exporter_control_overhead_pct", "percent", controlDeltaPct,
                    "Negative control: same exporter scraped 200x more often. If this is "
                            + "resolvable and the 10s figure is not, 'unmeasurable at 10s' is "
                            + "a supported claim rather than an absence of evidence. " + detail);

            // Optional, like every other acceptance measurement here: the numbers
            // are tracked nightly on bench-data and the regression gate owns
            // drift. A hard assertion at this sample size would be a coin flip on
            // a shared runner, which is worse than no gate.
            helper.succeed();
        });
    }

    private static long tick() {
        return WeftProfiler.get().tickCounter();
    }

    /** Per-tick durations of the phase that just ended, skipping its lead-in. */
    private static void collect(SectionSamples into, long phaseStartExclusive) {
        long end = tick() - 1;
        List<Long> nanos = new ArrayList<>();
        for (TickProfiler.TickRecord record : WeftProfiler.get().snapshotWindow()) {
            if (record.tickNumber() > phaseStartExclusive && record.tickNumber() <= end) {
                nanos.add(record.tickNanos());
            }
        }
        for (int i = SKIP_LEADING; i < nanos.size(); i++) {
            into.record(nanos.get(i));
        }
    }

    /**
     * Configure the exporter once, before any phase runs.
     *
     * <p>Separate from {@link #startExporter} because {@code WeftModules.resolve}
     * re-resolves <em>every</em> module from config, which silently made an
     * earlier version of this harness invalid: the first OFF phase ran with the
     * P1 services forced off, then the first {@code startExporter} resolve turned
     * them back on for every phase after it. The OFF pool was therefore a mix of
     * two different worlds, and the sign of the result flipped between runs. So
     * the ladder runs once here, the P1 services are pinned off afterwards, and
     * the phases toggle only the exporter.
     */
    private static void configureExporter(int port, ServerLevel level) {
        WeftConfig.METRICS_ENABLED = true;
        WeftConfig.METRICS_BIND_ADDRESS = "127.0.0.1";
        WeftConfig.METRICS_PORT = port;
        WeftConfig.EVENT_STREAM_ENABLED = true;
        WeftConfig.EVENT_STREAM_PATH = "logs/weft-events-bench.ndjson";
        // The probe is the one new tick-path measurement WS-7 adds, so the
        // overhead number has to include it or it is not the shipping cost.
        WeftConfig.REGION_TIMING_ENABLED = true;
        WeftObservability.onServerAboutToStart(level.getServer());
        WeftModules.resolve();
        // Now pin everything that is not the exporter, and leave it pinned.
        SpawnDensityHooks.setActive(false);
        PathfindingHooks.setActive(false);
        WeftObservability.setActive(false);
    }

    /** Toggle only the module under test — deliberately not through the ladder. */
    private static void startExporter(int port, ServerLevel level) {
        WeftObservability.setActive(true);
    }

    private static void stopExporter() {
        WeftObservability.setActive(false);
    }

    /** Restore config-resolved postures for the batches that follow. */
    private static void restoreModules() {
        WeftConfig.METRICS_ENABLED = false;
        WeftConfig.EVENT_STREAM_ENABLED = false;
        WeftModules.resolve();
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL()
                .openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Scrapes the endpoint from its own thread, paced by the server's tick
     * counter.
     *
     * <p>Off-thread because that is where a scrape happens in production, and
     * tick-paced rather than wall-clock-paced because the gametest server runs
     * uncapped (~90 tps): a literal 10-second timer would fire zero times in a
     * 200-tick phase, while "every 200 ticks" is the same cadence a 20 tps server
     * sees at 10 seconds and is comparable across runs and machines.
     */
    private static final class TickPacedScraper implements AutoCloseable {

        private final String url;
        private final Thread thread;
        private final java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent
                .atomic.AtomicLong> servedByCadence = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile int everyTicks;
        private volatile boolean running = true;

        TickPacedScraper(String url) {
            this.url = url;
            this.thread = new Thread(this::loop, "weft-bench-scraper");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        /** 0 stops scraping; N scrapes once every N ticks. */
        void cadence(int everyTicks) {
            this.everyTicks = everyTicks;
        }

        long served(int cadence) {
            var counter = servedByCadence.get(cadence);
            return counter == null ? 0L : counter.get();
        }

        private void loop() {
            long nextTick = -1;
            int lastCadence = 0;
            while (running) {
                int every = everyTicks;
                if (every <= 0) {
                    nextTick = -1;
                    lastCadence = 0;
                    parkBriefly();
                    continue;
                }
                long now = WeftProfiler.get().tickCounter();
                if (every != lastCadence) {
                    lastCadence = every;
                    nextTick = now;              // scrape immediately on a change
                }
                if (now >= nextTick) {
                    nextTick = now + every;
                    try {
                        get(url);
                        servedByCadence.computeIfAbsent(every,
                                k -> new java.util.concurrent.atomic.AtomicLong())
                                .incrementAndGet();
                    } catch (IOException ignored) {
                        // Counted by absence in the guards; a failed scrape must
                        // not stop the pacer or the run.
                    }
                } else {
                    parkBriefly();
                }
            }
        }

        private void parkBriefly() {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }

        @Override
        public void close() {
            running = false;
        }
    }

    private static int freePort() {
        try (ServerSocket probe = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            return probe.getLocalPort();
        } catch (IOException e) {
            return 19941;
        }
    }
}
