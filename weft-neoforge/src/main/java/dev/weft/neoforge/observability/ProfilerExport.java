package dev.weft.neoforge.observability;

import dev.weft.api.telemetry.Collector;
import dev.weft.api.telemetry.MetricSink;
import dev.weft.engine.telemetry.RegionizabilityAnalyzer;
import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.engine.telemetry.TickSample;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.profiler.WeftProfiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cost attribution from the P0 profiler (RFC-0009 §3.2, Appendix A).
 *
 * <p><b>Gauges over the profiler window, not counters.</b> The brief specified
 * {@code weft_unit_cost_seconds_total} as a counter, and it cannot be one
 * honestly. {@link TickProfiler} keeps a <em>rolling</em> window (100 ticks by
 * default), so summing it produces a number that walks up and down while wearing
 * a {@code _total} suffix — {@code rate()} over that returns nonsense.
 *
 * <p>The alternative, accumulating a since-boot total, means walking every
 * sample of every tick on the server thread: O(entities + block entities) of map
 * merges per tick, which is a measurement job with a different overhead budget.
 * This workstream is serialization.
 *
 * <p>So these are gauges named for what they are: cost attributed <em>within the
 * current window</em>, read at scrape time from {@code snapshotWindow()}, which
 * is documented safe from any thread. Zero tick-path cost, and the quantity an
 * operator actually wants — "what share of my tick is mod X" — is a ratio within
 * one window anyway. Ticks between the window's end and the next scrape are not
 * observed; for attribution shares that is immaterial, and it is why these are
 * not counters.
 *
 * <p>Absent, not zero: with the {@code profiler} module off there is no window,
 * and nothing is emitted (§4).
 */
public final class ProfilerExport implements Collector {

    private ProfilerExport() {}

    public static final ProfilerExport INSTANCE = new ProfilerExport();

    @Override
    public void collect(MetricSink sink) {
        if (!WeftConfig.PROFILING_ENABLED) {
            return;
        }
        List<TickProfiler.TickRecord> window = WeftProfiler.get().snapshotWindow();
        if (window.isEmpty()) {
            return;
        }
        // Aggregate by (source, type). One pass over the window, on the scrape
        // thread, once per scrape.
        Map<String, long[]> byUnit = new HashMap<>();
        Map<String, long[]> aiByType = new HashMap<>();
        for (TickProfiler.TickRecord record : window) {
            for (TickSample sample : record.samples()) {
                long[] slot = byUnit.computeIfAbsent(
                        sample.source().name() + SEP + sample.typeId(), k -> new long[2]);
                slot[0] += sample.nanos();
                slot[1]++;
                if (sample.aiNanos() > 0) {
                    aiByType.computeIfAbsent(sample.typeId(), k -> new long[1])[0]
                            += sample.aiNanos();
                }
            }
        }

        int ticks = window.size();
        byUnit.forEach((key, value) -> {
            int split = key.indexOf(SEP);
            String[] labelValues = {key.substring(0, split), key.substring(split + 1)};
            sink.gauge("weft_unit_cost_seconds", "Simulation cost attributed to a unit type "
                            + "over the profiler window.",
                    UNIT_LABELS, labelValues, value[0] / 1e9);
            sink.gauge("weft_unit_ticks", "Units of this type ticked over the profiler window.",
                    UNIT_LABELS, labelValues, value[1]);
        });
        aiByType.forEach((type, value) -> sink.gauge("weft_unit_ai_cost_seconds",
                "Mob AI-step cost over the profiler window (the WS-1 sizing slice).",
                "type", type, value[0] / 1e9));
        sink.gauge("weft_profiler_window_ticks",
                "Completed ticks the attribution gauges cover.", ticks);
    }

    private static final String[] UNIT_LABELS = {"source", "type"};

    /**
     * Composite-key separator: ASCII US. Cannot occur in a source name or a
     * ResourceLocation, so ("ENTITY","a:b") cannot collide with another pair.
     */
    private static final char SEP = (char) 0x1f;

    /**
     * The top cost sources in the current window, as {@code tick_outlier} payload
     * rows. Exactly the information a human wants about a spike and never has.
     *
     * <p>Reads the window rather than the spiking tick itself: the tick that just
     * ended is not yet finalised into the window when the outlier fires, and the
     * window it dominates is the honest attribution available at that moment.
     */
    static List<Object> topSources(int limit) {
        if (!WeftConfig.PROFILING_ENABLED) {
            return List.of();
        }
        List<TickProfiler.TickRecord> window = WeftProfiler.get().snapshotWindow();
        if (window.isEmpty()) {
            return List.of();
        }
        List<TickSample> all = new ArrayList<>();
        for (TickProfiler.TickRecord record : window) {
            all.addAll(record.samples());
        }
        RegionizabilityAnalyzer.Report report = new RegionizabilityAnalyzer(
                WeftConfig.MERGE_DISTANCE, WeftConfig.SPEEDUP_WORKER_COUNTS, limit)
                .analyze(all);

        // The analyzer's topTypes lose the source, so recover it by lookup.
        Map<String, TickSample.Source> sourceOf = new HashMap<>();
        for (TickSample sample : all) {
            sourceOf.putIfAbsent(sample.typeId(), sample.source());
        }
        List<Object> rows = new ArrayList<>();
        for (RegionizabilityAnalyzer.TypeCost type : report.topTypes()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", sourceOf.getOrDefault(type.typeId(), TickSample.Source.GLOBAL)
                    .name());
            row.put("type", type.typeId());
            row.put("seconds", type.nanos() / 1e9);
            rows.add(row);
        }
        return rows;
    }
}
