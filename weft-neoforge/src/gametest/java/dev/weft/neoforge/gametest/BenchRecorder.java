package dev.weft.neoforge.gametest;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * WS-8 (RFC-0002): collects world-benchmark measurements and writes them as
 * github-action-benchmark {@code customSmallerIsBetter} JSON next to the
 * server files ({@code weft-bench.json}). The nightly bench workflow feeds
 * that file to the same regression gate as the JMH suites, which is what
 * turns the WS-1..6 acceptance criteria into tracked benchmarks.
 *
 * <p>The file is rewritten after every entry, so a later test crashing never
 * loses an earlier test's numbers.
 */
public final class BenchRecorder {

    private BenchRecorder() {}

    private record Entry(String name, String unit, double value, String extra) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static synchronized void record(MinecraftServer server, String name,
                                           String unit, double value, String extra) {
        ENTRIES.add(new Entry(name, unit, value, extra));
        Path out = server.getServerDirectory().resolve("weft-bench.json");
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < ENTRIES.size(); i++) {
            Entry e = ENTRIES.get(i);
            json.append(String.format(Locale.ROOT,
                            "  {\"name\": \"%s\", \"unit\": \"%s\", \"value\": %.4f, \"extra\": \"%s\"}",
                            escape(e.name()), escape(e.unit()), e.value(), escape(e.extra())))
                    .append(i < ENTRIES.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");
        try {
            Files.writeString(out, json.toString());
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not write " + out, ex);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
