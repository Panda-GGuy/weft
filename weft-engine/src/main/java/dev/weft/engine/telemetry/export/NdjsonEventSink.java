package dev.weft.engine.telemetry.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * The newline-delimited JSON event sink (RFC-0009 §5): one JSON object per line,
 * written to a rotating file that an operator can point Vector, Promtail or
 * Fluent Bit at without Weft shipping a client for any of them.
 *
 * <p><b>Never backpressure the tick.</b> {@link #emit} hands the event to a
 * bounded queue and returns. A single daemon writer thread stamps the envelope,
 * renders the JSON and writes it, so neither formatting nor disk IO ever happens
 * on the server thread. When the queue is full the event is <b>dropped and
 * counted</b> ({@link #dropped()}), because a telemetry sink that blocks a tick
 * has become a performance bug in a performance mod.
 *
 * <p><b>Rotation</b> is size-triggered at {@code maxBytes}: the live file is
 * moved to {@code <name>.1} (replacing any previous one) and a fresh file
 * opened. One predecessor is kept — enough for a log shipper that is briefly
 * behind, and bounded so an unattended server cannot fill a disk.
 *
 * <p><b>Fail-soft</b> (RFC-0003 R2). If the file cannot be opened the
 * constructor throws and the caller self-disables the module with one log line.
 * If a write fails later, the sink latches itself off, records why, and the
 * server keeps running: an unwritable log must not be able to stop a tick.
 */
public final class NdjsonEventSink implements AutoCloseable {

    /**
     * RFC 3339, UTC, fixed millisecond precision. Fixed width on purpose —
     * {@code Instant.toString()} omits the fraction when it happens to be zero,
     * and a field whose format varies with the clock is a field consumers write
     * two parsers for.
     */
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Queue depth. Deep enough to absorb a burst of guard trips (the shape that
     * actually produces a flood) without becoming an unbounded memory sink.
     */
    private static final int QUEUE_CAPACITY = 4096;

    private final Path path;
    private final long maxBytes;
    private final String serverId;
    private final String weftVersion;
    private final String mcVersion;
    private final Supplier<Instant> clock;

    private final ArrayBlockingQueue<WeftEvent> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final LongAdder emitted = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder written = new LongAdder();
    private final Thread writer;

    private volatile boolean running = true;
    private volatile String latchedOffBecause;

    private BufferedWriter out;
    private long bytesInFile;

    public NdjsonEventSink(Path path, long maxBytes, String serverId, String weftVersion,
                           String mcVersion) throws IOException {
        this(path, maxBytes, serverId, weftVersion, mcVersion, Instant::now);
    }

    /** Clock-injecting constructor; tests need a fixed {@code ts}. */
    public NdjsonEventSink(Path path, long maxBytes, String serverId, String weftVersion,
                           String mcVersion, Supplier<Instant> clock) throws IOException {
        this.path = path;
        this.maxBytes = Math.max(1L, maxBytes);
        this.serverId = serverId;
        this.weftVersion = weftVersion;
        this.mcVersion = mcVersion;
        this.clock = clock;
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        open();
        this.writer = new Thread(this::drainLoop, "weft-event-sink");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    /**
     * Queue one event. Safe from any thread, never blocks, never throws.
     * Returns false if the event was dropped (queue full, or the sink latched
     * off after a write failure).
     */
    public boolean emit(WeftEvent event) {
        if (!running || latchedOffBecause != null) {
            dropped.increment();
            return false;
        }
        if (queue.offer(event)) {
            emitted.increment();
            return true;
        }
        dropped.increment();
        return false;
    }

    /** Render one event's full line, envelope included. Package-visible for tests. */
    String render(WeftEvent event) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("v", WeftEvent.SCHEMA_VERSION);
        line.put("ts", TIMESTAMP.format(clock.get()));
        line.put("kind", event.kind().wireName());
        line.put("server_id", serverId);
        line.put("weft_version", weftVersion);
        line.put("mc_version", mcVersion);
        line.put("data", event.data());
        return Json.write(line);
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            try {
                WeftEvent event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                writeLine(render(event));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException | UncheckedIOException e) {
                // Latch off rather than spin on a broken disk. One reason is
                // kept for /weft status; the tick never learns about this.
                latchedOffBecause = e.getClass().getSimpleName() + ": " + e.getMessage();
                closeQuietly();
                return;
            }
        }
        closeQuietly();
    }

    private void writeLine(String line) throws IOException {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytesInFile > 0 && bytesInFile + bytes.length + 1 > maxBytes) {
            rotate();
        }
        out.write(line);
        out.write('\n');
        out.flush();
        bytesInFile += bytes.length + 1L;
        written.increment();
    }

    private void open() throws IOException {
        out = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        bytesInFile = Files.exists(path) ? Files.size(path) : 0L;
    }

    private void rotate() throws IOException {
        out.close();
        Path predecessor = path.resolveSibling(path.getFileName() + ".1");
        Files.move(path, predecessor, StandardCopyOption.REPLACE_EXISTING);
        open();
    }

    private void closeQuietly() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
            // Closing a sink we are already abandoning.
        }
    }

    /** Events accepted onto the queue since this sink opened. */
    public long emitted() {
        return emitted.sum();
    }

    /** Events dropped: queue full, or arriving after a write failure latched the sink off. */
    public long dropped() {
        return dropped.sum();
    }

    /** Lines actually written to disk. */
    public long written() {
        return written.sum();
    }

    /** Why the sink stopped writing, or null while healthy (R5 status detail). */
    public String latchedOffBecause() {
        return latchedOffBecause;
    }

    public boolean healthy() {
        return running && latchedOffBecause == null;
    }

    public Path path() {
        return path;
    }

    /**
     * Stop accepting events, drain what is queued, and close the file. Bounded
     * wait: server shutdown must not hang on a telemetry sink.
     */
    @Override
    public void close() {
        running = false;
        try {
            writer.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
