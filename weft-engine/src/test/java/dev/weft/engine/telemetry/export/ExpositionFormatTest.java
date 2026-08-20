package dev.weft.engine.telemetry.export;

import dev.weft.api.telemetry.MetricSink;
import dev.weft.api.telemetry.WeftTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-7's first gate (RFC-0009 §10.1): our exposition output must round-trip
 * through a strict reader. Malformed exposition is the classic exporter bug and
 * it fails silently — a scraper records nothing and nobody notices until
 * someone needs the data.
 *
 * <p>This is the offline half of the gate. The authoritative half is
 * {@code promtool check metrics} in CI, which is the canonical Go parser plus a
 * naming/unit linter; see {@link ExpositionParser} for why the official Java
 * client cannot serve here.
 */
class ExpositionFormatTest {

    @BeforeEach
    void enable() {
        WeftTelemetry.reset();
        WeftTelemetry.setEnabled(true);
    }

    @AfterEach
    void disable() {
        WeftTelemetry.setEnabled(false);
        WeftTelemetry.reset();
    }

    private static MetricSnapshot snapshot() {
        return new MetricSnapshot(50);
    }

    private static ExpositionParser.Parsed roundTrip(MetricSnapshot snapshot,
                                                     ExpositionFormat.Dialect dialect) {
        ExpositionFormat.Rendered rendered = ExpositionFormat.render(snapshot, dialect);
        return ExpositionParser.parse(rendered.body(), dialect);
    }

    @Test
    void countersGaugesAndHistogramsRoundTrip() {
        MetricSnapshot snapshot = snapshot();
        snapshot.counter("weft_guard_trips_total", "Ownership guard trips.",
                new String[]{"kind", "severity"},
                new String[]{"region_mutation", "degraded_to_mail"}, 3);
        snapshot.gauge("weft_regions", "Regions per level.", "level",
                "minecraft:overworld", 4);
        snapshot.histogram("weft_tick_duration_seconds", "Full server tick.",
                MetricSink.NO_LABELS, MetricSink.NO_LABELS,
                new double[]{0.025, 0.05, 0.1}, new long[]{2, 5, 6}, 0.31, 7);

        ExpositionParser.Parsed parsed = roundTrip(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);

        assertEquals(3, parsed.value("weft_guard_trips_total",
                "kind", "region_mutation", "severity", "degraded_to_mail"));
        assertEquals(4, parsed.value("weft_regions", "level", "minecraft:overworld"));
        assertEquals(2, parsed.value("weft_tick_duration_seconds_bucket", "le", "0.025"));
        assertEquals(6, parsed.value("weft_tick_duration_seconds_bucket", "le", "0.1"));
        // Observations above the last bound live in +Inf, which must equal _count.
        assertEquals(7, parsed.value("weft_tick_duration_seconds_bucket", "le", "+Inf"));
        assertEquals(7, parsed.value("weft_tick_duration_seconds_count"));
        assertEquals(0.31, parsed.value("weft_tick_duration_seconds_sum"), 1e-9);
        assertEquals("histogram", parsed.type().get("weft_tick_duration_seconds"));
        assertFalse(parsed.sawEof(), "Prometheus text format must not emit # EOF");
    }

    @Test
    void openMetricsDropsTotalFromMetadataAndTerminatesWithEof() {
        MetricSnapshot snapshot = snapshot();
        snapshot.counter("weft_legacy_mod_cost_seconds_total",
                "Legacy-lane cost attributed to a mod.", "modid", "create", 1.5);

        ExpositionFormat.Rendered rendered = ExpositionFormat.render(snapshot,
                ExpositionFormat.Dialect.OPENMETRICS);
        ExpositionParser.Parsed parsed = ExpositionParser.parse(rendered.body(),
                ExpositionFormat.Dialect.OPENMETRICS);

        // The metadata name drops _total; the sample keeps it. Getting this
        // backwards produces a body one of the two parsers rejects.
        assertEquals("counter", parsed.type().get("weft_legacy_mod_cost_seconds"));
        assertEquals("seconds", parsed.unit().get("weft_legacy_mod_cost_seconds"));
        assertEquals(1.5, parsed.value("weft_legacy_mod_cost_seconds_total",
                "modid", "create"), 1e-9);
        assertTrue(parsed.sawEof());
        assertEquals(ExpositionFormat.OPENMETRICS_CONTENT_TYPE, rendered.contentType());
    }

    @Test
    void prometheusDialectKeepsTotalInMetadata() {
        MetricSnapshot snapshot = snapshot();
        snapshot.counter("weft_scrapes_total", "Scrapes served.", 12);

        ExpositionParser.Parsed parsed = roundTrip(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);

        assertEquals("counter", parsed.type().get("weft_scrapes_total"));
        assertEquals(12, parsed.value("weft_scrapes_total"));
    }

    @Test
    void labelValuesAndHelpTextAreEscaped() {
        MetricSnapshot snapshot = snapshot();
        // A mod id cannot contain these, but a guard trip's forensic detail can,
        // and one unescaped quote corrupts every following line of the scrape.
        snapshot.counter("weft_guard_trips_total", "Trips \\ with a newline\nin help.",
                "kind", "say \"hi\" \\ here\nand there", 1);

        ExpositionParser.Parsed parsed = roundTrip(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);

        assertEquals(1, parsed.value("weft_guard_trips_total",
                "kind", "say \"hi\" \\ here\nand there"));
        assertEquals("Trips \\\\ with a newline\\nin help.",
                parsed.help().get("weft_guard_trips_total"));
    }

    @Test
    void carriageReturnInALabelValueCannotBreakTheScrape() {
        MetricSnapshot snapshot = snapshot();
        snapshot.counter("weft_guard_trips_total", "Trips.", "kind", "a\r\nb", 1);

        // Parses at all only because the CR was dropped rather than emitted raw.
        ExpositionParser.Parsed parsed = roundTrip(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);
        assertEquals(1, parsed.value("weft_guard_trips_total", "kind", "a\nb"));
    }

    @Test
    void nonFiniteValuesUseTheSpecSpellings() {
        MetricSnapshot snapshot = snapshot();
        snapshot.gauge("weft_worker_utilization_ratio", "Utilisation.", "pool", "region",
                Double.NaN);

        ExpositionParser.Parsed parsed = roundTrip(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);

        assertTrue(Double.isNaN(parsed.value("weft_worker_utilization_ratio",
                "pool", "region")));
    }

    @Test
    void duplicateSeriesAreDroppedAndCountedRatherThanEmitted() {
        MetricSnapshot snapshot = snapshot();
        snapshot.gauge("weft_regions", "Regions.", "level", "minecraft:overworld", 4);
        snapshot.gauge("weft_regions", "Regions.", "level", "minecraft:overworld", 9);

        ExpositionFormat.Rendered rendered = ExpositionFormat.render(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);

        // Two collectors racing to publish the same series makes a scrape
        // invalid. Dropping the second keeps the body parseable; counting it is
        // how the bug becomes visible instead of silent.
        assertEquals(1, rendered.seriesWritten());
        assertEquals(1, rendered.duplicatesDropped());
        ExpositionParser.Parsed parsed = ExpositionParser.parse(rendered.body(),
                ExpositionFormat.Dialect.PROMETHEUS);
        assertEquals(4, parsed.value("weft_regions", "level", "minecraft:overworld"));
    }

    @Test
    void pushedInstrumentsCumulateHistogramBucketsOnTheWayOut() {
        WeftTelemetry.Histograms lane = WeftTelemetry.histogram(
                "weft_legacy_lane_duration_seconds", "Legacy lane pass.",
                new double[]{0.001, 0.01, 0.1});
        lane.observe(0.0005);
        lane.observe(0.005);
        lane.observe(0.005);
        lane.observe(5.0);          // above the last bound: only +Inf and _count

        MetricSnapshot snapshot = snapshot();
        WeftTelemetry.collectInto(snapshot, (c, e) -> {
            throw e;
        });
        ExpositionParser.Parsed parsed = roundTrip(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);

        assertEquals(1, parsed.value("weft_legacy_lane_duration_seconds_bucket",
                "le", "0.001"));
        assertEquals(3, parsed.value("weft_legacy_lane_duration_seconds_bucket",
                "le", "0.01"));
        assertEquals(3, parsed.value("weft_legacy_lane_duration_seconds_bucket",
                "le", "0.1"));
        assertEquals(4, parsed.value("weft_legacy_lane_duration_seconds_bucket",
                "le", "+Inf"));
        assertEquals(4, parsed.value("weft_legacy_lane_duration_seconds_count"));
    }

    @Test
    void disabledTelemetryPublishesNothingAtAll() {
        WeftTelemetry.Counters trips = WeftTelemetry.counter(
                "weft_guard_trips_total", "Trips.", "kind");
        WeftTelemetry.setEnabled(false);

        trips.inc("region_mutation");

        MetricSnapshot snapshot = snapshot();
        WeftTelemetry.collectInto(snapshot, (c, e) -> {
            throw e;
        });
        // R6: yield must be total. Not "zero" — nothing.
        assertEquals(0, snapshot.pointCount());
    }

    @Test
    void theParserRejectsWhatTheExporterMustNeverEmit() {
        // Each of these has been a real exporter bug somewhere.
        assertThrows(ExpositionParser.MalformedExposition.class, () ->
                ExpositionParser.parse("weft_thing 1\n",
                        ExpositionFormat.Dialect.PROMETHEUS),
                "a sample with no # TYPE must be rejected");
        assertThrows(ExpositionParser.MalformedExposition.class, () ->
                ExpositionParser.parse("# HELP weft_a A.\n# TYPE weft_a counter\nweft_a 1\n",
                        ExpositionFormat.Dialect.PROMETHEUS),
                "a counter without the _total suffix must be rejected");
        assertThrows(ExpositionParser.MalformedExposition.class, () ->
                ExpositionParser.parse("""
                        # HELP weft_h H.
                        # TYPE weft_h histogram
                        weft_h_bucket{le="0.1"} 2
                        weft_h_sum 1
                        weft_h_count 2
                        """, ExpositionFormat.Dialect.PROMETHEUS),
                "a histogram with no +Inf bucket must be rejected");
        assertThrows(ExpositionParser.MalformedExposition.class, () ->
                ExpositionParser.parse("""
                        # HELP weft_h H.
                        # TYPE weft_h histogram
                        weft_h_bucket{le="0.1"} 5
                        weft_h_bucket{le="+Inf"} 2
                        weft_h_sum 1
                        weft_h_count 2
                        """, ExpositionFormat.Dialect.PROMETHEUS),
                "non-cumulative histogram counts must be rejected");
        assertThrows(ExpositionParser.MalformedExposition.class, () ->
                ExpositionParser.parse("# HELP weft_a_total A.\n# TYPE weft_a_total counter\n"
                                + "weft_a_total 1\n# EOF\n",
                        ExpositionFormat.Dialect.OPENMETRICS),
                "OpenMetrics counter metadata keeping _total must be rejected");
    }
}
