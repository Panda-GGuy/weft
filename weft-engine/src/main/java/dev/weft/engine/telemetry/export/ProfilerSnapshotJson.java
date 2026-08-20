package dev.weft.engine.telemetry.export;

import dev.weft.engine.telemetry.RegionizabilityAnalyzer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The profiler window as JSON (RFC-0009 §6): the same content
 * {@code /weft report} renders, in a shape a program can read.
 *
 * <p>Reachable two ways — {@code /weft report --json}, which writes
 * {@code weft-report.json} beside the existing text file, and as a
 * {@code profiler_snapshot} event. <b>The text report is unchanged and stays the
 * human surface</b>, including {@code ReportFormatter}'s LF-only, ASCII-safe
 * output contract, which exists because {@code §} is a Minecraft chat formatting
 * prefix and non-ASCII mojibakes in cp1252 consoles.
 *
 * <p>Field names are the snake_case spelling of
 * {@link RegionizabilityAnalyzer.Report}'s accessors — a mechanical mapping, so
 * a field added to the report cannot quietly fail to appear here, and the
 * envelope's snake_case convention holds throughout one document rather than
 * changing halfway down.
 */
public final class ProfilerSnapshotJson {

    private ProfilerSnapshotJson() {}

    /**
     * Build the snapshot document.
     *
     * @param report        the analysed window, or null when nothing has been
     *                      recorded — a window that produced no ticks yields
     *                      {@code ticks_analyzed: 0} and no derived fields,
     *                      rather than a document full of zeros that reads like
     *                      a measurement of an idle server
     * @param ticksAnalyzed completed ticks in the window
     * @param windowSize    the configured window, for context on the above
     * @param profilingOn   whether the profiler is currently recording; false
     *                      means the numbers are stale, which a consumer needs
     *                      to know as much as a human does
     */
    public static Map<String, Object> of(RegionizabilityAnalyzer.Report report, int ticksAnalyzed,
                                         int windowSize, boolean profilingOn) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("window_size", windowSize);
        out.put("ticks_analyzed", ticksAnalyzed);
        out.put("profiling_enabled", profilingOn);
        if (report == null || ticksAnalyzed == 0) {
            return out;
        }
        out.put("total_nanos", report.totalNanos());
        out.put("spatial_nanos", report.spatialNanos());
        out.put("global_nanos", report.globalNanos());
        out.put("entity_nanos", report.entityNanos());
        out.put("entity_ai_nanos", report.entityAiNanos());
        out.put("throttleable_nanos", report.throttleableNanos());
        out.put("activation_saved_nanos", report.activationSavedNanos());
        out.put("throttleable_ai_nanos", report.throttleableAiNanos());
        out.put("activation_ai_saved_nanos", report.activationAiSavedNanos());

        List<Object> regions = new ArrayList<>(report.regions().size());
        for (RegionizabilityAnalyzer.RegionCost region : report.regions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("region_id", region.regionId());
            entry.put("chunk_count", region.chunkCount());
            entry.put("nanos", region.nanos());
            regions.add(entry);
        }
        out.put("regions", regions);

        List<Object> types = new ArrayList<>(report.topTypes().size());
        for (RegionizabilityAnalyzer.TypeCost type : report.topTypes()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type_id", type.typeId());
            entry.put("nanos", type.nanos());
            entry.put("count", type.count());
            types.add(entry);
        }
        out.put("top_types", types);

        // JSON object keys are strings; sorted so the document is stable.
        Map<String, Object> speedups = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> e : new TreeMap<>(report.speedupByWorkers()).entrySet()) {
            speedups.put(String.valueOf(e.getKey()), e.getValue());
        }
        out.put("speedup_by_workers", speedups);
        return out;
    }

    /** The document as one JSON string, for {@code weft-report.json}. */
    public static String render(RegionizabilityAnalyzer.Report report, int ticksAnalyzed,
                               int windowSize, boolean profilingOn) {
        return Json.write(of(report, ticksAnalyzed, windowSize, profilingOn));
    }
}
