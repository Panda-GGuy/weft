package dev.weft.engine.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Renders an analyzer report as human-readable text (chat/console/file). */
public final class ReportFormatter {

    private ReportFormatter() {}

    public static String format(RegionizabilityAnalyzer.Report r, int ticksAnalyzed) {
        StringBuilder sb = new StringBuilder(1024);
        double totalMs = r.totalNanos() / 1e6;
        double spatialPct = pct(r.spatialNanos(), r.totalNanos());
        double globalPct = pct(r.globalNanos(), r.totalNanos());

        sb.append("=== Weft P0 Regionizability Report ===\n");
        sb.append(String.format("Ticks analyzed: %d | attributed cost: %.2f ms/tick avg%n",
                ticksAnalyzed, totalMs / Math.max(1, ticksAnalyzed)));
        sb.append(String.format("Parallelizable (region-attributable): %.1f%%%n", spatialPct));
        sb.append(String.format("Serial (global, no spatial home):     %.1f%%%n", globalPct));

        sb.append(String.format("Hypothetical regions: %d", r.regions().size()));
        if (!r.regions().isEmpty()) {
            RegionizabilityAnalyzer.RegionCost hottest = r.regions().get(0);
            sb.append(String.format(" (hottest: %.1f%% of tick, %d chunks)",
                    pct(hottest.nanos(), r.totalNanos()), hottest.chunkCount()));
        }
        sb.append('\n');

        sb.append("Estimated speedup if Weft owned this tick:\n");
        for (Map.Entry<Integer, Double> e : new TreeMap<>(r.speedupByWorkers()).entrySet()) {
            sb.append(String.format("  %2d workers -> %.2fx%n", e.getKey(), e.getValue()));
        }

        sb.append("Top cost sources:\n");
        List<RegionizabilityAnalyzer.TypeCost> types = new ArrayList<>(r.topTypes());
        for (RegionizabilityAnalyzer.TypeCost t : types) {
            sb.append(String.format("  %5.1f%%  %-40s x%d%n",
                    pct(t.nanos(), r.totalNanos()), t.typeId(), t.count()));
        }
        sb.append("(speedup = attributed-cost model, LPT schedule over regions; ")
          .append("serial fraction is the Amdahl floor — see RFC-0001 §9)\n");
        return sb.toString();
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }
}
