package dev.weft.engine.telemetry.export;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event sink's own gate (RFC-0009 §5, §10.5). Two properties matter more
 * than the contents of any line: the sink can never apply backpressure to the
 * tick, and it can never grow without bound on an unattended server.
 */
class NdjsonEventSinkTest {

    private static final Instant FIXED = Instant.parse("2026-08-17T14:03:11.482Z");

    private static NdjsonEventSink open(Path file, long maxBytes) throws IOException {
        return new NdjsonEventSink(file, maxBytes, "server-1", "0.1.0-alpha", "1.21.1",
                () -> FIXED);
    }

    private static WeftEvent event(String key) {
        return WeftEvent.of(WeftEvent.Kind.CONFIG_CHANGE)
                .put("key", key).put("to", 1L).put("source", "file").build();
    }

    /** Poll rather than sleep-and-hope: the writer thread is asynchronous by design. */
    private static void await(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    @Test
    void theEnvelopeIsStampedByTheSinkNotTheProducer() throws IOException {
        Path dir = Files.createTempDirectory("weft-sink");
        Path file = dir.resolve("weft-events.ndjson");
        try (NdjsonEventSink sink = open(file, 1 << 20)) {
            String line = sink.render(event("profilingEnabled"));
            // Field order is stable, which is what makes the stream diffable.
            assertTrue(line.startsWith("{\"v\":1,\"ts\":\"2026-08-17T14:03:11.482Z\","
                    + "\"kind\":\"config_change\",\"server_id\":\"server-1\","
                    + "\"weft_version\":\"0.1.0-alpha\",\"mc_version\":\"1.21.1\","
                    + "\"data\":{"), line);
        }
    }

    @Test
    void eventsReachTheFileOneJsonObjectPerLine() throws IOException {
        Path dir = Files.createTempDirectory("weft-sink");
        Path file = dir.resolve("weft-events.ndjson");
        try (NdjsonEventSink sink = open(file, 1 << 20)) {
            for (int i = 0; i < 20; i++) {
                assertTrue(sink.emit(event("key" + i)));
            }
            await(() -> sink.written() == 20, "20 lines written");
        }
        List<String> lines = Files.readAllLines(file);
        assertEquals(20, lines.size());
        assertTrue(lines.stream().allMatch(l -> l.startsWith("{") && l.endsWith("}")));
    }

    @Test
    void rotationKeepsExactlyOnePredecessorSoADiskCannotFill() throws IOException {
        Path dir = Files.createTempDirectory("weft-sink");
        Path file = dir.resolve("weft-events.ndjson");
        Path predecessor = dir.resolve("weft-events.ndjson.1");
        // Small cap so a handful of lines forces several rotations.
        try (NdjsonEventSink sink = open(file, 400)) {
            for (int i = 0; i < 40; i++) {
                sink.emit(event("key" + i));
            }
            await(() -> sink.written() == 40, "40 lines written");
        }
        assertTrue(Files.exists(predecessor), "a rotation must have happened");
        assertFalse(Files.exists(dir.resolve("weft-events.ndjson.2")),
                "only one predecessor is kept");
        assertTrue(Files.size(file) <= 400 + 512, "live file stayed near its cap");
        // Nothing else in the directory: no .2, no .gz, no temp files left behind.
        try (var entries = Files.list(dir)) {
            assertEquals(2, entries.count());
        }
    }

    @Test
    void anUnwritablePathFailsAtConstructionSoTheModuleCanSelfDisable() throws IOException {
        Path dir = Files.createTempDirectory("weft-sink");
        // A directory where the file should be: opening for write cannot succeed.
        Path occupied = Files.createDirectory(dir.resolve("weft-events.ndjson"));
        assertTrue(Files.isDirectory(occupied));
        // RFC-0003 R2: the caller logs one line and self-disables (rung 3).
        // Failing loudly here is what makes that possible.
        assertThrows(IOException.class, () -> open(occupied, 1 << 20));
    }

    @Test
    void emitAfterCloseIsDroppedAndCountedRatherThanThrowing() throws IOException {
        Path dir = Files.createTempDirectory("weft-sink");
        NdjsonEventSink sink = open(dir.resolve("weft-events.ndjson"), 1 << 20);
        sink.emit(event("before"));
        sink.close();

        // A shutdown race must not surface as an exception on a tick path.
        assertFalse(sink.emit(event("after")));
        assertTrue(sink.dropped() >= 1);
        assertFalse(sink.healthy());
    }

    @Test
    void aBurstBeyondTheQueueIsDroppedAndCountedNotBlocked() throws IOException {
        Path dir = Files.createTempDirectory("weft-sink");
        try (NdjsonEventSink sink = open(dir.resolve("weft-events.ndjson"), 1 << 20)) {
            // Far past the 4096-deep queue. The point is not how many land — the
            // writer drains concurrently — but that emit() never blocks and every
            // rejection is counted, so a guard-trip flood cannot stall a tick.
            long start = System.nanoTime();
            int accepted = 0;
            for (int i = 0; i < 200_000; i++) {
                if (sink.emit(event("k" + i))) {
                    accepted++;
                }
            }
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            assertEquals(200_000, accepted + sink.dropped(),
                    "every event is either accepted or counted as dropped");
            assertTrue(sink.dropped() > 0, "the queue bound must actually bite");
            assertTrue(elapsedMillis < 10_000,
                    "emit() must never block on the writer: took " + elapsedMillis + "ms");
        }
    }

    @Test
    void jsonRenderingRefusesTypesWithNoJsonSpelling() {
        // Better a loud failure in a test than a silently truncated event line.
        assertThrows(IllegalArgumentException.class,
                () -> Json.write(java.util.Map.of("k", new Object())));
    }

    @Test
    void nonFiniteNumbersBecomeNullBecauseJsonHasNoSpellingForThem() {
        assertEquals("{\"ratio\":null}", Json.write(java.util.Map.of("ratio", Double.NaN)));
        assertEquals("{\"ratio\":null}",
                Json.write(java.util.Map.of("ratio", Double.POSITIVE_INFINITY)));
    }

    @Test
    void controlCharactersInAStringAreEscaped() {
        // A guard trip's forensic detail is the realistic source of these, and
        // one raw control byte makes the line unparseable for every consumer.
        assertEquals("{\"k\":\"a\\u0001b\\nc\\\"d\\\\e\"}",
                Json.write(java.util.Map.of("k", "a" + (char) 1 + "b\nc\"d\\e")));
    }
}
