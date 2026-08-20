package dev.weft.engine.telemetry.export;

import dev.weft.api.telemetry.WeftTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scrape endpoint's gate (RFC-0009 §8.1, §10.5). Two things matter beyond
 * serving bytes: a taken port must be detectable so the module can yield it, and
 * a closed server must leave no socket behind (R6).
 */
class MetricsHttpServerTest {

    private static final String LOOPBACK = "127.0.0.1";

    private MetricsHttpServer server;

    @BeforeEach
    void enable() {
        WeftTelemetry.reset();
        WeftTelemetry.setEnabled(true);
    }

    @AfterEach
    void cleanUp() {
        if (server != null) {
            server.close();
        }
        WeftTelemetry.setEnabled(false);
        WeftTelemetry.reset();
    }

    /** Port 0 lets the OS pick, so a busy CI machine cannot flake this suite. */
    private MetricsHttpServer startOnEphemeralPort() throws IOException {
        server = new MetricsHttpServer(50, (collector, error) -> {
            throw error;
        });
        server.start(LOOPBACK, 0);
        return server;
    }

    private static String get(String url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL()
                .openConnection();
        if (accept != null) {
            connection.setRequestProperty("Accept", accept);
        }
        try (var in = connection.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private static int port(MetricsHttpServer server) {
        String bound = server.boundAddress();
        return Integer.parseInt(bound.substring(bound.lastIndexOf(':') + 1));
    }

    @Test
    void aScrapeServesParseablePrometheusText() throws IOException {
        WeftTelemetry.gauge("weft_regions", "Regions per level.", "level")
                .set(4, "minecraft:overworld");
        MetricsHttpServer running = startOnEphemeralPort();

        String body = get("http://" + LOOPBACK + ":" + port(running) + "/metrics", null);

        ExpositionParser.Parsed parsed = ExpositionParser.parse(body,
                ExpositionFormat.Dialect.PROMETHEUS);
        assertEquals(4, parsed.value("weft_regions", "level", "minecraft:overworld"));
        assertEquals(1, running.scrapes());
        assertTrue(running.scrapeNanos() > 0);
    }

    @Test
    void theDialectIsNegotiatedFromTheAcceptHeader() throws IOException {
        WeftTelemetry.counter("weft_scrapes_total", "Scrapes.").add(7);
        MetricsHttpServer running = startOnEphemeralPort();
        String url = "http://" + LOOPBACK + ":" + port(running) + "/metrics";

        String openMetrics = get(url, "application/openmetrics-text; version=1.0.0");
        assertTrue(openMetrics.endsWith("# EOF\n"), openMetrics);
        ExpositionParser.parse(openMetrics, ExpositionFormat.Dialect.OPENMETRICS);

        String prometheus = get(url, "text/plain");
        assertTrue(!prometheus.contains("# EOF"), prometheus);
        ExpositionParser.parse(prometheus, ExpositionFormat.Dialect.PROMETHEUS);
    }

    @Test
    void aTakenPortSurfacesAsBindExceptionSoTheModuleCanYieldIt() throws IOException {
        // Stand in for a neighbouring exporter, a Bukkit plugin under a proxy, or
        // any unrelated process. Binding is what detects all of them; a modid
        // registry would have detected none (RFC-0009 §8.1).
        try (ServerSocket squatter = new ServerSocket(0, 1,
                java.net.InetAddress.getByName(LOOPBACK))) {
            MetricsHttpServer contender = new MetricsHttpServer(50, (c, e) -> { });
            assertThrows(BindException.class,
                    () -> contender.start(LOOPBACK, squatter.getLocalPort()));
            // Nothing half-started: no listener to leak.
            assertTrue(!contender.running());
        }
    }

    @Test
    void closingReleasesThePortSoAYieldedModuleHoldsNoSocket() throws IOException {
        MetricsHttpServer running = startOnEphemeralPort();
        int boundPort = port(running);
        running.close();
        server = null;

        // R6: yield must be total. If the socket survived, this would throw.
        try (ServerSocket reuse = new ServerSocket(boundPort, 1,
                java.net.InetAddress.getByName(LOOPBACK))) {
            assertEquals(boundPort, reuse.getLocalPort());
        }
        assertTrue(!running.running());
        assertEquals("", running.boundAddress());
    }

    @Test
    void anythingOtherThanTheMetricsPathPointsAtIt() throws IOException {
        MetricsHttpServer running = startOnEphemeralPort();
        HttpURLConnection connection = (HttpURLConnection) URI.create(
                "http://" + LOOPBACK + ":" + port(running) + "/").toURL().openConnection();
        try {
            assertEquals(404, connection.getResponseCode());
            try (var in = connection.getErrorStream()) {
                assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8)
                        .contains("/metrics"));
            }
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void theBoundAddressIsReportedForTheStatusTable() throws IOException {
        MetricsHttpServer running = startOnEphemeralPort();
        assertTrue(running.boundAddress().startsWith(LOOPBACK + ":"));
        assertNotEquals(LOOPBACK + ":0", running.boundAddress(),
                "the resolved port, not the requested one, belongs in /weft status");
    }
}
