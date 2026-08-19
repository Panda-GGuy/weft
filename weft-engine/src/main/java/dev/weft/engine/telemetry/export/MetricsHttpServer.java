package dev.weft.engine.telemetry.export;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.weft.api.telemetry.Collector;
import dev.weft.api.telemetry.WeftTelemetry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

/**
 * The Prometheus scrape endpoint (RFC-0009 §7, §8.1).
 *
 * <p>Built on the JDK's own {@code com.sun.net.httpserver}, so the exporter's
 * pure parts stay dependency-free. One daemon thread serves scrapes;
 * <b>collection and formatting happen entirely on that thread</b>, never on the
 * server thread (§9.1).
 *
 * <p><b>Loopback by default, and no authentication.</b> An unauthenticated
 * metrics port on a public interface leaks mod list, player counts and world
 * topology, so the default bind address is {@code 127.0.0.1} and remote
 * scraping is the operator's explicit decision — made by changing the address,
 * ideally behind their own reverse proxy. There is deliberately no auth and no
 * TLS here: that is the deployment's job by Prometheus convention, and a
 * half-built auth scheme is worse than none.
 *
 * <p><b>Port collision is detected by binding</b> (§8.1). {@link #start} throws
 * {@link BindException} and the caller self-disables the module with one log
 * line (RFC-0003 R2, rung 3). This catches every neighbour — exporter mods,
 * Bukkit plugins under a proxy, unrelated processes — which modid detection
 * could not. What it deliberately cannot do is name the holder: mapping a
 * listening socket to a mod means process inspection, and RFC-0003 §4 forbids
 * reaching into neighbours. The log reports the port, not the culprit.
 */
public final class MetricsHttpServer implements AutoCloseable {

    /** Where a scraper expects to find metrics. */
    public static final String METRICS_PATH = "/metrics";

    private final int maxCardinality;
    private final BiConsumer<Collector, RuntimeException> onCollectorError;

    private final LongAdder scrapes = new LongAdder();
    private final LongAdder scrapeNanos = new LongAdder();
    private final LongAdder duplicatesDropped = new LongAdder();

    private HttpServer server;
    private String boundAddress = "";

    public MetricsHttpServer(int maxCardinality,
                             BiConsumer<Collector, RuntimeException> onCollectorError) {
        this.maxCardinality = maxCardinality;
        this.onCollectorError = onCollectorError;
    }

    /**
     * Bind and begin serving.
     *
     * @throws BindException if the address is already in use — the signal the
     *         caller turns into a yield
     * @throws IOException   on any other bind failure
     */
    public void start(String bindAddress, int port) throws IOException {
        InetSocketAddress address = new InetSocketAddress(bindAddress, port);
        // Backlog 1: Prometheus scrapes one at a time, and a deep backlog would
        // only queue work for a server that is already behind.
        server = HttpServer.create(address, 1);
        server.createContext(METRICS_PATH, this::handleMetrics);
        // A bare "/" is what an operator types into a browser to check the port
        // is alive; pointing them at /metrics beats a 404 they have to guess at.
        server.createContext("/", this::handleIndex);
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "weft-metrics");
            thread.setDaemon(true);
            return thread;
        };
        server.setExecutor(Executors.newSingleThreadExecutor(threads));
        server.start();
        boundAddress = bindAddress + ":" + server.getAddress().getPort();
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain; charset=utf-8", "method not allowed\n");
            return;
        }
        long started = System.nanoTime();
        ExpositionFormat.Rendered rendered;
        try {
            MetricSnapshot snapshot = new MetricSnapshot(maxCardinality);
            WeftTelemetry.collectInto(snapshot, onCollectorError);
            rendered = ExpositionFormat.render(snapshot,
                    ExpositionFormat.negotiate(exchange.getRequestHeaders()
                            .getFirst("Accept")));
        } catch (RuntimeException e) {
            // A telemetry failure must never affect the tick, and it should not
            // hang a scraper either: answer 500 and let the operator see it.
            respond(exchange, 500, "text/plain; charset=utf-8",
                    "weft: metric collection failed: " + e + "\n");
            return;
        }
        scrapes.increment();
        scrapeNanos.add(System.nanoTime() - started);
        duplicatesDropped.add(rendered.duplicatesDropped());
        respond(exchange, 200, rendered.contentType(), rendered.body());
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        respond(exchange, 404, "text/plain; charset=utf-8",
                "weft: metrics are at " + METRICS_PATH + "\n");
    }

    private static void respond(HttpExchange exchange, int status, String contentType,
                                String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** {@code host:port} actually bound, or empty before {@link #start}. */
    public String boundAddress() {
        return boundAddress;
    }

    public long scrapes() {
        return scrapes.sum();
    }

    public long scrapeNanos() {
        return scrapeNanos.sum();
    }

    /**
     * Series suppressed because an identical name+labels pair was already
     * written this scrape. Non-zero means two collectors are publishing the same
     * series — a bug in Weft, surfaced rather than swallowed.
     */
    public long duplicatesDropped() {
        return duplicatesDropped.sum();
    }

    public boolean running() {
        return server != null;
    }

    /**
     * R6: yield must be total. Stops the listener and drops the executor, so a
     * disabled module holds no thread and no socket.
     */
    @Override
    public void close() {
        HttpServer running = server;
        server = null;
        boundAddress = "";
        if (running != null) {
            running.stop(0);
        }
    }
}
