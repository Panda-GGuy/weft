package dev.weft.engine.telemetry.export;

import dev.weft.api.telemetry.MetricSink;
import dev.weft.api.telemetry.WeftTelemetry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-7's cardinality gate (RFC-0009 §10.4). {@code modid} and {@code type} are
 * unbounded label values in principle — a 400-mod pack has hundreds of the
 * first and thousands of the second. An exporter that ships all of them OOMs
 * the operator's Prometheus, and the bug report gets filed against us.
 */
class MetricSnapshotCapTest {

    private static final int CAP = 50;

    @Test
    void aFamilyBeyondTheCapFoldsItsTailIntoOneOtherSeries() {
        MetricSnapshot snapshot = new MetricSnapshot(CAP);
        // 400 mods, cost descending with index: mod0 is the most expensive.
        for (int i = 0; i < 400; i++) {
            snapshot.counter("weft_legacy_mod_cost_seconds_total", "Legacy cost.",
                    "modid", "mod" + i, 400 - i);
        }

        List<MetricPoint> family = snapshot.families().get(0);

        assertEquals(CAP + 1, family.size(), "cap survivors plus exactly one __other__");
        assertEquals(350, snapshot.foldedSeries());

        MetricPoint other = family.get(family.size() - 1);
        assertEquals(WeftTelemetry.OTHER, other.labelValues()[0]);
        // The tail's cost is preserved in aggregate: 400-50 = ranks 51..400,
        // i.e. values 350 down to 1.
        assertEquals(350 * 351 / 2, other.rankValue());
    }

    @Test
    void theSurvivorsAreTheExpensiveOnesNotTheFirstToArrive() {
        MetricSnapshot snapshot = new MetricSnapshot(CAP);
        // Arrival order is deliberately the reverse of cost order: the mod that
        // loaded last is the one that matters. Capping by arrival would hide it.
        for (int i = 0; i < 200; i++) {
            snapshot.counter("weft_legacy_mod_cost_seconds_total", "Legacy cost.",
                    "modid", "mod" + i, i);
        }

        List<MetricPoint> family = snapshot.families().get(0);
        List<String> kept = family.stream().map(p -> p.labelValues()[0]).toList();

        assertTrue(kept.contains("mod199"), "the most expensive mod must survive the cap");
        assertTrue(kept.contains("mod150"));
        assertTrue(!kept.contains("mod0"), "the cheapest mod must be folded away");
        assertEquals(CAP + 1, family.size());
    }

    @Test
    void histogramsFoldByObservationCountAndPreserveTheirBuckets() {
        MetricSnapshot snapshot = new MetricSnapshot(2);
        double[] bounds = {0.01, 0.1};
        for (int i = 0; i < 5; i++) {
            snapshot.histogram("weft_service_latency_seconds", "Service latency.",
                    new String[]{"service"}, new String[]{"svc" + i},
                    bounds, new long[]{i, i * 2L}, 0.5 * i, i * 3L);
        }

        List<MetricPoint> family = snapshot.families().get(0);
        assertEquals(3, family.size());

        MetricPoint.Histogram other = (MetricPoint.Histogram) family.get(family.size() - 1);
        // Folded ranks 3..5 are svc2, svc1, svc0 -> counts 2+1+0 and 4+2+0.
        assertEquals(3, other.cumulativeCounts()[0]);
        assertEquals(6, other.cumulativeCounts()[1]);
        assertEquals(9, other.count());
        assertEquals(1.5, other.sum(), 1e-9);
    }

    @Test
    void aCappedFamilyStillRoundTripsThroughTheParser() {
        MetricSnapshot snapshot = new MetricSnapshot(CAP);
        for (int i = 0; i < 500; i++) {
            snapshot.counter("weft_unit_cost_seconds_total", "Unit cost.",
                    new String[]{"source", "type"},
                    new String[]{"ENTITY", "modid:type" + i}, i);
        }

        ExpositionFormat.Rendered rendered = ExpositionFormat.render(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS);
        ExpositionParser.Parsed parsed = ExpositionParser.parse(rendered.body(),
                ExpositionFormat.Dialect.PROMETHEUS);

        // Bounded on the wire, and the tail is reachable rather than lost.
        assertEquals(CAP + 1, parsed.family("weft_unit_cost_seconds_total").size());
        // Every label of a folded series becomes __other__, source included:
        // folding only the high-cardinality label would need the exporter to
        // guess which one that is. Values 0..499, so the surviving top 50 are
        // 499..450 and the fold carries 449..0.
        assertEquals(449 * 450 / 2, parsed.value("weft_unit_cost_seconds_total",
                "source", WeftTelemetry.OTHER, "type", WeftTelemetry.OTHER));
    }

    @Test
    void aFamilyUnderTheCapIsUntouched() {
        MetricSnapshot snapshot = new MetricSnapshot(CAP);
        snapshot.gauge("weft_regions", "Regions.", "level", "minecraft:overworld", 4);
        snapshot.gauge("weft_regions", "Regions.", "level", "minecraft:the_nether", 1);

        assertEquals(2, snapshot.families().get(0).size());
        assertEquals(0, snapshot.foldedSeries());
    }

    @Test
    void theRegistryRunawayGuardBoundsRetentionIndependently() {
        WeftTelemetry.reset();
        WeftTelemetry.setEnabled(true);
        try {
            WeftTelemetry.Counters extractions = WeftTelemetry.counter(
                    "weft_legacy_extractions_total", "Extractions.", "modid");
            // Far past the 4096 retention ceiling: memory must stay bounded even
            // before the wire-level cap gets a chance to run.
            for (int i = 0; i < 10_000; i++) {
                extractions.inc("mod" + i);
            }
            MetricSnapshot snapshot = new MetricSnapshot(CAP);
            WeftTelemetry.collectInto(snapshot, (c, e) -> {
                throw e;
            });
            assertTrue(snapshot.pointCount() <= 4096 + 1,
                    "retention ceiling breached: " + snapshot.pointCount());
            assertEquals(CAP + 1, snapshot.families().get(0).size());
        } finally {
            WeftTelemetry.setEnabled(false);
            WeftTelemetry.reset();
        }
    }

    /** Guards the {@link MetricSink} contract the sink implementations rely on. */
    @Test
    void noLabelsIsTheSharedEmptyArray() {
        assertEquals(0, MetricSink.NO_LABELS.length);
    }
}
