package dev.weft.neoforge.gametest;

import dev.weft.engine.region.RegionManager;
import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.engine.telemetry.TickSample;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.observability.WeftObservability;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.profiler.WeftProfiler;
import dev.weft.neoforge.regiontick.RegionTopology;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WS-7's in-world gates (RFC-0009 §10.2 and §10.5).
 *
 * <p><b>Correctness against a known world.</b> The RFC-0005 parity arena — the
 * same furnaces, hopper clocks and penned mobs — is built, ticked, and scraped;
 * every scraped value must match what the profiler, the topology and the
 * services independently report. A scrape that agrees with nothing is not
 * evidence, so this carries a <b>vacuous-run guard</b>: if the arena produced no
 * ticks, no entities and no block-entity work, the test fails rather than
 * passing on empty agreement. That guard has already paid for itself once in
 * this repo — RFC-0005's first "green" run was comparing identical emptiness
 * because the arena had built at bedrock and its population had fallen into the
 * void.
 *
 * <p><b>Fail-soft.</b> A taken port and an unwritable event sink must each
 * produce a self-disabled module, one log line, and an unaffected tick
 * (RFC-0003 R2, rung 3). The unit suite covers the bind failure in isolation;
 * this covers it through the real coexistence ladder on a booted server, which
 * is where a wiring mistake would actually show up.
 *
 * <p>The scrape body is also written to {@code weft-metrics-scrape.txt} in the
 * run directory, where the {@code bench}/{@code build} workflow feeds it to
 * {@code promtool check metrics} — the canonical Prometheus parser and naming
 * linter, run against real server data rather than a synthetic fixture.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class ObservabilityGameTests {

    private static final int SETTLE_TICKS = 10;
    /** Long enough for furnaces to smelt and hopper clocks to cycle. */
    private static final int RUN_TICKS = 220;

    /** Where the scrape body lands for the promtool CI step. */
    public static final String SCRAPE_FILE = "weft-metrics-scrape.txt";

    @GameTest(template = "empty", batch = "p2observability", timeoutTicks = 1600)
    public void observabilityScrapeMatchesIndependentSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = arenaColumn(helper);
        WeftBenchGameTests.forceChunks(level, column, true);
        BlockPos base = new BlockPos(column.getX(),
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        column.getX(), column.getZ()),
                column.getZ());

        // Isolate the exporter as the only variable (RFC-0005 §3 discipline):
        // the profiler stays ON because it is one of the sources being
        // cross-checked, but nothing else may move the world underneath us.
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        WeftConfig.PROFILING_ENABLED = true;

        AtomicReference<String> scrape = new AtomicReference<>();
        int port = freePort();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            ParityScenario.reset(level, base);
            ParityScenario.build(level, base);
            startExporter(port, level);
        });

        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS, () -> {
            try {
                // Twice. The endpoint counts a scrape after rendering it, so the
                // first body reports zero scrapes by construction - asserting on
                // one body would either fail or accept anything. The second body
                // is the one that can prove the counter moves.
                get("http://127.0.0.1:" + port + "/metrics");
                scrape.set(get("http://127.0.0.1:" + port + "/metrics"));
            } catch (IOException e) {
                stopExporter();
                tearDown(level, base);
                helper.fail("Scrape failed against the module's own bound port: " + e);
                return;
            }
            Map<String, Double> series = parse(scrape.get());
            writeScrapeFile(level, scrape.get());

            // Independent sources, read the same tick the scrape covered.
            RegionManager topology = RegionTopology.managerFor(level);
            int actualRegions = topology.all().size();
            int actualChunks = topology.chunkCount();
            String levelId = level.dimension().location().toString();
            ProfilerTotals profiler = profilerTotals();

            stopExporter();
            tearDown(level, base);
            // --- vacuous-run guard, before any agreement is trusted ---
            if (profiler.ticks == 0) {
                helper.fail("Vacuous run: the profiler window holds no completed ticks, so "
                        + "every comparison below would be comparing emptiness.");
            }
            if (profiler.entityCost <= 0 || profiler.blockEntityCost <= 0) {
                helper.fail(String.format(Locale.ROOT,
                        "Vacuous run: arena produced no simulation work to attribute "
                                + "(entity cost %d ns, block-entity cost %d ns over %d ticks). "
                                + "The arena is empty or did not tick - agreement here would "
                                + "be meaningless.",
                        profiler.entityCost, profiler.blockEntityCost, profiler.ticks));
            }
            if (actualChunks == 0 || actualRegions == 0) {
                helper.fail("Vacuous run: topology tracked no chunks (" + actualChunks
                        + " chunks, " + actualRegions + " regions)");
            }

            // --- the endpoint served something a parser can read at all ---
            if (series.isEmpty()) {
                helper.fail("Scrape produced no series:\n" + scrape.get());
            }
            if (!scrape.get().contains("# TYPE weft_tick_period_seconds histogram")) {
                helper.fail("Scrape is missing the tick-period histogram metadata:\n"
                        + head(scrape.get()));
            }

            // --- topology: scraped vs RegionTopology ---
            assertSeries(helper, series, "weft_regions{level=\"" + levelId + "\"}",
                    actualRegions, 0, "region count");
            assertSeries(helper, series,
                    "weft_region_chunks_loaded{level=\"" + levelId + "\"}",
                    actualChunks, 0, "loaded chunk count");

            // --- tick histogram: scraped count vs profiler ticks ---
            Double tickCount = series.get("weft_tick_period_seconds_count{}");
            if (tickCount == null) {
                helper.fail("No weft_tick_period_seconds_count in the scrape");
                return;
            }
            if (tickCount < RUN_TICKS * 0.5) {
                helper.fail(String.format(Locale.ROOT,
                        "Tick histogram observed %.0f ticks across a %d-tick run - the tick "
                                + "hook is not firing every tick", tickCount, RUN_TICKS));
            }
            Double tickSum = series.get("weft_tick_period_seconds_sum{}");
            if (tickSum == null || tickSum <= 0) {
                helper.fail("Tick durations summed to " + tickSum + " - the clock is not "
                        + "being read");
            }

            // --- cost attribution: scraped vs the profiler window itself ---
            double scrapedEntity = sumWhere(series, "weft_unit_cost_seconds{source=\"ENTITY\"");
            double scrapedBlockEntity = sumWhere(series,
                    "weft_unit_cost_seconds{source=\"BLOCK_ENTITY\"");
            // Same window, so these must agree closely; the scrape reads
            // snapshotWindow() one tick later at most.
            assertClose(helper, scrapedEntity, profiler.entityCost / 1e9, 0.25,
                    "entity cost attribution");
            assertClose(helper, scrapedBlockEntity, profiler.blockEntityCost / 1e9, 0.25,
                    "block-entity cost attribution");
            Double windowTicks = series.get("weft_profiler_window_ticks{}");
            if (windowTicks == null || Math.abs(windowTicks - profiler.ticks) > 2) {
                helper.fail("Scraped profiler window " + windowTicks + " disagrees with the "
                        + "profiler's own " + profiler.ticks + " ticks");
            }

            // --- module posture: scraped vs the R5 table ---
            Map<String, String> postures = WeftModules.lastResolutions();
            String observabilityState = postures.get("observability");
            if (observabilityState == null) {
                helper.fail("The observability module is not in the R5 posture table");
                return;
            }
            // Exactly one state series per module must be 1 (the state-set
            // pattern); more than one would make the panel ambiguous.
            int hot = 0;
            for (Map.Entry<String, Double> entry : series.entrySet()) {
                if (entry.getKey().startsWith("weft_module_state{module=\"observability\"")
                        && entry.getValue() == 1.0) {
                    hot++;
                }
            }
            if (hot != 1) {
                helper.fail("weft_module_state has " + hot + " states set for 'observability'; "
                        + "exactly one must be 1 (R5 said " + observabilityState + ")");
            }

            // --- the exporter's own health ---
            Double scrapes = series.get("weft_scrapes_total{}");
            if (scrapes == null || scrapes < 1) {
                helper.fail("The endpoint served two scrapes but counted " + scrapes);
            }
            helper.succeed();
        });
    }

    /**
     * Fail-soft, both failure modes, through the real ladder (RFC-0009 §10.5).
     *
     * <p>The tick must be visibly unaffected: a telemetry failure that stops the
     * world is worse than no telemetry.
     */
    @GameTest(template = "empty", batch = "p2observability", timeoutTicks = 600)
    public void observabilityYieldsATakenPortAndAnUnwritableSink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Path serverDir = level.getServer().getServerDirectory();

        boolean metricsWas = WeftConfig.METRICS_ENABLED;
        boolean eventsWas = WeftConfig.EVENT_STREAM_ENABLED;
        String pathWas = WeftConfig.EVENT_STREAM_PATH;
        int portWas = WeftConfig.METRICS_PORT;

        int port = freePort();
        try (ServerSocket squatter = new ServerSocket(port, 1,
                InetAddress.getByName("127.0.0.1"))) {
            // Stand in for a neighbouring exporter, a Bukkit plugin behind a
            // proxy, or any unrelated process. Binding detects all of them;
            // a modid registry would have detected none (RFC-0009 §8.1).
            WeftConfig.METRICS_ENABLED = true;
            WeftConfig.EVENT_STREAM_ENABLED = false;
            WeftConfig.METRICS_PORT = squatter.getLocalPort();
            WeftModules.resolve();

            if (WeftObservability.isActive()) {
                helper.fail("Module stayed active with its port held by another process");
            }
            String detail = WeftObservability.statusDetail();
            if (!detail.contains("self-disabled") || !detail.contains("already in use")) {
                helper.fail("R5 detail does not explain the port yield: " + detail);
            }
        } catch (IOException e) {
            helper.fail("Could not occupy a port for the collision test: " + e);
        }

        // An unwritable sink: a directory where the file should be.
        Path blocked = serverDir.resolve("weft-events-blocked.ndjson");
        try {
            if (!Files.isDirectory(blocked)) {
                Files.createDirectories(blocked);
            }
            WeftConfig.METRICS_ENABLED = false;
            WeftConfig.EVENT_STREAM_ENABLED = true;
            WeftConfig.EVENT_STREAM_PATH = blocked.getFileName().toString();
            WeftModules.resolve();

            if (WeftObservability.isActive()) {
                helper.fail("Module stayed active with an unwritable event sink");
            }
            if (!WeftObservability.statusDetail().contains("self-disabled")) {
                helper.fail("R5 detail does not report the sink failure: "
                        + WeftObservability.statusDetail());
            }
        } catch (IOException e) {
            helper.fail("Could not stage an unwritable sink: " + e);
        } finally {
            WeftConfig.METRICS_ENABLED = metricsWas;
            WeftConfig.EVENT_STREAM_ENABLED = eventsWas;
            WeftConfig.EVENT_STREAM_PATH = pathWas;
            WeftConfig.METRICS_PORT = portWas;
            WeftModules.resolve();
        }

        // And the tick kept running through both failures.
        long gameTimeAtFailure = level.getGameTime();
        helper.runAfterDelay(20, () -> {
            if (level.getGameTime() <= gameTimeAtFailure) {
                helper.fail("The tick did not advance after two telemetry failures");
            }
            helper.succeed();
        });
    }

    // --- exporter lifecycle for the test ---

    private static void startExporter(int port, ServerLevel level) {
        WeftConfig.METRICS_ENABLED = true;
        WeftConfig.METRICS_BIND_ADDRESS = "127.0.0.1";
        WeftConfig.METRICS_PORT = port;
        WeftConfig.EVENT_STREAM_ENABLED = true;
        WeftConfig.EVENT_STREAM_PATH = "logs/weft-events-gametest.ndjson";
        WeftConfig.REGION_TIMING_ENABLED = true;
        WeftObservability.onServerAboutToStart(level.getServer());
        // Through the ladder, not around it: the point is that resolution is
        // what starts the endpoint.
        WeftModules.resolve();
    }

    private static void stopExporter() {
        WeftConfig.METRICS_ENABLED = false;
        WeftConfig.EVENT_STREAM_ENABLED = false;
        WeftModules.resolve();
    }

    // --- helpers ---

    /** Per-source totals over the profiler window, read independently of the scrape. */
    private record ProfilerTotals(int ticks, long entityCost, long blockEntityCost) {}

    private static ProfilerTotals profilerTotals() {
        List<TickProfiler.TickRecord> window = WeftProfiler.get().snapshotWindow();
        long entity = 0;
        long blockEntity = 0;
        for (TickProfiler.TickRecord record : window) {
            for (TickSample sample : record.samples()) {
                if (sample.source() == TickSample.Source.ENTITY) {
                    entity += sample.nanos();
                } else if (sample.source() == TickSample.Source.BLOCK_ENTITY) {
                    blockEntity += sample.nanos();
                }
            }
        }
        return new ProfilerTotals(window.size(), entity, blockEntity);
    }

    /**
     * Minimal series reader: {@code name{labels} value} to a map keyed by
     * {@code name{labels}}. Deliberately not a conformance check — that is the
     * unit suite's strict parser plus {@code promtool} in CI. This only needs to
     * find the values being cross-checked.
     */
    private static Map<String, Double> parse(String body) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String line : body.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int space = line.lastIndexOf(' ');
            if (space <= 0) {
                continue;
            }
            String key = line.substring(0, space);
            if (!key.contains("{")) {
                key = key + "{}";
            }
            try {
                out.put(key, Double.parseDouble(line.substring(space + 1)));
            } catch (NumberFormatException ignored) {
                // NaN/+Inf spellings; none of the cross-checks need them.
            }
        }
        return out;
    }

    private static double sumWhere(Map<String, Double> series, String keyPrefix) {
        double total = 0;
        for (Map.Entry<String, Double> entry : series.entrySet()) {
            if (entry.getKey().startsWith(keyPrefix)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static void assertSeries(GameTestHelper helper, Map<String, Double> series,
                                     String key, double expected, double tolerance,
                                     String what) {
        Double actual = series.get(key);
        if (actual == null) {
            helper.fail("Scrape is missing " + key + " (" + what + ")");
            return;
        }
        if (Math.abs(actual - expected) > tolerance) {
            helper.fail(String.format(Locale.ROOT,
                    "%s disagrees: scrape says %.0f, the source says %.0f (%s)",
                    what, actual, expected, key));
        }
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected,
                                    double relativeTolerance, String what) {
        double allowed = Math.max(1e-6, Math.abs(expected) * relativeTolerance);
        if (Math.abs(actual - expected) > allowed) {
            helper.fail(String.format(Locale.ROOT,
                    "%s disagrees: scrape says %.6fs, the profiler says %.6fs "
                            + "(tolerance %.0f%%)",
                    what, actual, expected, relativeTolerance * 100));
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL()
                .openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try (InputStream in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    /** Let the OS pick, so a busy CI runner cannot collide with a fixed port. */
    private static int freePort() {
        try (ServerSocket probe = new ServerSocket(0, 1,
                InetAddress.getByName("127.0.0.1"))) {
            return probe.getLocalPort();
        } catch (IOException e) {
            return 19940;
        }
    }

    /** The artifact the promtool CI step lints. */
    private static void writeScrapeFile(ServerLevel level, String body) {
        try {
            Files.writeString(level.getServer().getServerDirectory().resolve(SCRAPE_FILE),
                    body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Not worth failing the gate over: the assertions above are the gate,
            // and promtool has its own CI step that will notice a missing file.
            System.err.println("Weft: could not write " + SCRAPE_FILE + ": " + e);
        }
    }

    private static void tearDown(ServerLevel level, BlockPos base) {
        ParityScenario.reset(level, base);
        ParityScenario.restoreFloor(level, base);
        WeftBenchGameTests.forceChunks(level, base, false);
    }

    private static String head(String body) {
        return body.length() > 800 ? body.substring(0, 800) + "..." : body;
    }

    /** Well away from the gametest structure plot, like the parity arena's. */
    private static BlockPos arenaColumn(GameTestHelper helper) {
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        return new BlockPos(ground.getX() + 160, 0, ground.getZ() + 160);
    }
}
