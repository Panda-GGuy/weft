package dev.weft.engine.telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders an analyzer report as human-readable text (chat/console/file).
 *
 * <p>Output contract: lines are separated by {@code '\n'} only (consumers
 * split on it — {@code %n} would smuggle {@code \r} into Minecraft chat on
 * Windows), and the text is ASCII-safe: {@code §} is a Minecraft chat
 * formatting prefix and non-ASCII mojibakes in cp1252 consoles.
 */
public final class ReportFormatter {

    private ReportFormatter() {}

    public static String format(RegionizabilityAnalyzer.Report r, int ticksAnalyzed) {
        StringBuilder sb = new StringBuilder(1024);
        double totalMs = r.totalNanos() / 1e6;
        double spatialPct = pct(r.spatialNanos(), r.totalNanos());
        double globalPct = pct(r.globalNanos(), r.totalNanos());

        sb.append("=== Weft P0 Regionizability Report ===\n");
        sb.append(String.format("Ticks analyzed: %d | attributed cost: %.2f ms/tick avg\n",
                ticksAnalyzed, totalMs / Math.max(1, ticksAnalyzed)));
        sb.append(String.format("Parallelizable (region-attributable): %.1f%%\n", spatialPct));
        sb.append(String.format("Serial (global, no spatial home):     %.1f%%\n", globalPct));

        // AI sub-attribution (WS-1 sizing): only printed once the AI-slice
        // hook has produced data - a window of pure item/projectile traffic
        // (or a loader without the Mob hook) legitimately has no AI slice,
        // and "0.0% AI" would read as a measurement rather than absence.
        if (r.entityAiNanos() > 0) {
            sb.append(String.format(
                    "Entity cost split: %.1f%% AI step vs %.1f%% movement/physics/other\n",
                    pct(r.entityAiNanos(), r.entityNanos()),
                    pct(r.entityNanos() - r.entityAiNanos(), r.entityNanos())));
        }

        sb.append(String.format("Hypothetical regions: %d", r.regions().size()));
        if (!r.regions().isEmpty()) {
            RegionizabilityAnalyzer.RegionCost hottest = r.regions().get(0);
            sb.append(String.format(" (hottest: %.1f%% of tick, %d chunks)",
                    pct(hottest.nanos(), r.totalNanos()), hottest.chunkCount()));
        }
        sb.append('\n');

        sb.append("Estimated speedup if Weft owned this tick:\n");
        for (Map.Entry<Integer, Double> e : new TreeMap<>(r.speedupByWorkers()).entrySet()) {
            sb.append(String.format("  %2d workers -> %.2fx\n", e.getKey(), e.getValue()));
        }

        // WS-1 tie-in (RFC-0002): projection from per-sample activation
        // intervals recorded at measurement time under the *configured* tiers,
        // so the number is meaningful before the module is ever switched on.
        if (r.throttleableNanos() > 0) {
            sb.append(String.format(
                    "Projected WS-1 activation savings: up to %.1f%% of attributed cost\n",
                    pct(r.activationSavedNanos(), r.totalNanos())));
            sb.append(String.format(
                    "  (%.1f%% of cost is mob AI the configured tiers would throttle; upper\n"
                    + "  bound - only the AI share of a throttled mob's tick is skipped)\n",
                    pct(r.throttleableNanos(), r.totalNanos())));
            if (r.throttleableAiNanos() > 0) {
                sb.append(String.format(
                        "  Measured: widening WS-1 to gate the whole AI step is worth %.1f%%\n"
                        + "  of attributed cost (throttled mobs' AI slice at assigned intervals)\n",
                        pct(r.activationAiSavedNanos(), r.totalNanos())));
            }
        }

        sb.append("Top cost sources:\n");
        List<RegionizabilityAnalyzer.TypeCost> types = new ArrayList<>(r.topTypes());
        for (RegionizabilityAnalyzer.TypeCost t : types) {
            sb.append(String.format("  %5.1f%%  %-40s x%d\n",
                    pct(t.nanos(), r.totalNanos()), t.typeId(), t.count()));
        }
        sb.append("(speedup = attributed-cost model, LPT schedule over regions; ")
          .append("serial fraction is the Amdahl floor - see RFC-0001 sec. 9)\n");
        return sb.toString();
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }
}
