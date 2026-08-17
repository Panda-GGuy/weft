package dev.weft.engine.telemetry;

import dev.weft.engine.region.ChunkKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportFormatterTest {

    private static RegionizabilityAnalyzer.Report sampleReport() {
        List<TickSample> samples = List.of(
                new TickSample(TickSample.Source.ENTITY, "mod:machine",
                        ChunkKey.pack(0, 0), 3_000_000),
                new TickSample(TickSample.Source.GLOBAL, "global:time",
                        TickSample.NO_CHUNK, 1_000_000));
        return new RegionizabilityAnalyzer(2, new int[]{2, 4}, 5).analyze(samples);
    }

    /**
     * Consumers (chat command, console logger) split the report on '\n'.
     * A %n in the formatter would emit \r\n on Windows and leak \r into
     * Minecraft chat lines.
     */
    @Test
    void linesSeparatedByBareNewlineOnly() {
        String text = ReportFormatter.format(sampleReport(), 10);
        assertFalse(text.contains("\r"), "report must not contain carriage returns");
        assertTrue(text.endsWith("\n"));
    }

    /**
     * The section sign (U+00A7) is a Minecraft chat formatting prefix (it
     * eats the following character and recolors the line), and non-ASCII
     * mojibakes on cp1252 consoles - the report must be plain ASCII.
     */
    @Test
    void outputIsAsciiSafe() {
        String text = ReportFormatter.format(sampleReport(), 10);
        assertEquals(-1, text.indexOf('§'), "no Minecraft formatting codes");
        text.chars().forEach(c ->
                assertTrue(c == '\n' || (c >= 0x20 && c < 0x7f),
                        "non-ASCII or control char in report: U+%04X".formatted(c)));
    }

    @Test
    void reportContainsHeadlineNumbers() {
        String text = ReportFormatter.format(sampleReport(), 10);
        assertTrue(text.contains("=== Weft P0 Regionizability Report ==="));
        assertTrue(text.contains("Parallelizable (region-attributable): 75.0%"));
        assertTrue(text.contains("Serial (global, no spatial home):     25.0%"));
        assertTrue(text.contains("Ticks analyzed: 10"));
    }

    @Test
    void activationLineAppearsOnlyWithThrottleableCost() {
        String without = ReportFormatter.format(sampleReport(), 10);
        assertFalse(without.contains("Projected WS-1"),
                "no throttleable samples -> no projection line");

        List<TickSample> samples = List.of(
                new TickSample(TickSample.Source.ENTITY, "minecraft:cow",
                        ChunkKey.pack(0, 0), 3_000_000, 4),
                new TickSample(TickSample.Source.GLOBAL, "global:time",
                        TickSample.NO_CHUNK, 1_000_000));
        String with = ReportFormatter.format(
                new RegionizabilityAnalyzer(2, new int[]{2}, 5).analyze(samples), 10);
        // saved = 3ms * 3/4 = 2.25ms of 4ms total = 56.3%; throttleable = 75%
        assertTrue(with.contains("Projected WS-1 activation savings: up to 56.3%"), with);
        assertTrue(with.contains("75.0% of cost is mob AI"), with);
    }

    @Test
    void entitySplitAndWidenedProjectionAppearOnlyWithAiSliceData() {
        String without = ReportFormatter.format(sampleReport(), 10);
        assertFalse(without.contains("Entity cost split"),
                "no AI-slice data -> absence, not a fake 0.0% measurement");
        assertFalse(without.contains("widening WS-1"));

        List<TickSample> samples = List.of(
                new TickSample(TickSample.Source.ENTITY, "minecraft:cow",
                        ChunkKey.pack(0, 0), 4_000_000, 4, 2_000_000),
                new TickSample(TickSample.Source.GLOBAL, "global:time",
                        TickSample.NO_CHUNK, 4_000_000));
        String with = ReportFormatter.format(
                new RegionizabilityAnalyzer(2, new int[]{2}, 5).analyze(samples), 10);
        // entity 4ms with 2ms AI -> 50/50 split
        assertTrue(with.contains(
                "Entity cost split: 50.0% AI step vs 50.0% movement/physics/other"), with);
        // widened saving = 2ms * 3/4 = 1.5ms of 8ms attributed = 18.8%
        assertTrue(with.contains("worth 18.8%"), with);
    }
}
