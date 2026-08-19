package dev.weft.engine.telemetry.export;

import dev.weft.api.telemetry.MetricSink;
import dev.weft.api.telemetry.WeftTelemetry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One scrape's worth of collected series, grouped into families and capped
 * (RFC-0009 §7). Built on the scrape thread by acting as the {@link MetricSink}
 * collectors write into, then rendered by {@link ExpositionFormat}.
 *
 * <p><b>The cardinality cap.</b> {@code modid} and {@code type} label values
 * are unbounded in principle — a 400-mod pack has hundreds of the first and
 * thousands of the second. Past {@code maxLabelCardinality} series in one
 * family, the tail is folded into a single {@code __other__} series and its
 * label breakdown is deliberately lost. That is the point: an exporter that
 * OOMs a Prometheus instance on a big pack is a bug report filed against us,
 * not against the pack.
 *
 * <p>Ranking is by {@link MetricPoint#rankValue()} descending, so the series
 * that survive are the expensive ones rather than whichever arrived first.
 * Arrival order would mean the mod that loaded 51st is invisible no matter what
 * it costs.
 *
 * <p>Every label of a folded series becomes {@code __other__}, including
 * low-cardinality ones. Folding only the high-cardinality label would need this
 * class to guess which one that is; keeping the tail legible enough to
 * mis-read is worse than one honest bucket.
 *
 * <p>Not thread-safe: one instance per scrape, used by one thread.
 */
public final class MetricSnapshot implements MetricSink {

    /** Series sharing one name, in first-seen order. */
    private final Map<String, List<MetricPoint>> families = new LinkedHashMap<>();
    private final int maxCardinality;
    private int foldedSeries;

    public MetricSnapshot(int maxCardinality) {
        if (maxCardinality < 1) {
            throw new IllegalArgumentException("maxCardinality must be >= 1");
        }
        this.maxCardinality = maxCardinality;
    }

    @Override
    public void counter(String name, String help, String[] labelNames, String[] labelValues,
                        double value) {
        add(new MetricPoint.Counter(name, help, labelNames, labelValues, value));
    }

    @Override
    public void gauge(String name, String help, String[] labelNames, String[] labelValues,
                      double value) {
        add(new MetricPoint.Gauge(name, help, labelNames, labelValues, value));
    }

    @Override
    public void histogram(String name, String help, String[] labelNames, String[] labelValues,
                          double[] upperBounds, long[] cumulativeCounts, double sum, long count) {
        add(new MetricPoint.Histogram(name, help, labelNames, labelValues,
                upperBounds, cumulativeCounts, sum, count));
    }

    private void add(MetricPoint point) {
        families.computeIfAbsent(point.name(), n -> new ArrayList<>()).add(point);
    }

    /**
     * Families in collection order, each capped. Call once per scrape — the cap
     * is applied here rather than on the way in, because ranking needs the
     * whole family.
     */
    public List<List<MetricPoint>> families() {
        List<List<MetricPoint>> out = new ArrayList<>(families.size());
        foldedSeries = 0;
        for (List<MetricPoint> family : families.values()) {
            out.add(cap(family));
        }
        return out;
    }

    /** Series dropped into {@code __other__} by the most recent {@link #families()}. */
    public int foldedSeries() {
        return foldedSeries;
    }

    public int familyCount() {
        return families.size();
    }

    /** Total series collected, before the cap. */
    public int pointCount() {
        return families.values().stream().mapToInt(List::size).sum();
    }

    private List<MetricPoint> cap(List<MetricPoint> family) {
        if (family.size() <= maxCardinality) {
            return family;
        }
        List<MetricPoint> sorted = new ArrayList<>(family);
        sorted.sort(Comparator.comparingDouble(MetricPoint::rankValue).reversed());
        List<MetricPoint> kept = new ArrayList<>(sorted.subList(0, maxCardinality));
        List<MetricPoint> tail = sorted.subList(maxCardinality, sorted.size());
        foldedSeries += tail.size();
        kept.add(fold(tail));
        return kept;
    }

    /** Merge a family's tail into one {@code __other__} series. */
    private static MetricPoint fold(List<MetricPoint> tail) {
        MetricPoint first = tail.get(0);
        String[] labelNames = first.labelNames();
        String[] other = new String[labelNames.length];
        java.util.Arrays.fill(other, WeftTelemetry.OTHER);

        if (first instanceof MetricPoint.Histogram h) {
            long[] counts = new long[h.upperBounds().length];
            double sum = 0;
            long count = 0;
            for (MetricPoint p : tail) {
                MetricPoint.Histogram t = (MetricPoint.Histogram) p;
                for (int i = 0; i < counts.length && i < t.cumulativeCounts().length; i++) {
                    counts[i] += t.cumulativeCounts()[i];
                }
                sum += t.sum();
                count += t.count();
            }
            return new MetricPoint.Histogram(h.name(), h.help(), labelNames, other,
                    h.upperBounds(), counts, sum, count);
        }

        double total = 0;
        for (MetricPoint p : tail) {
            total += p.rankValue();
        }
        return first instanceof MetricPoint.Counter
                ? new MetricPoint.Counter(first.name(), first.help(), labelNames, other, total)
                : new MetricPoint.Gauge(first.name(), first.help(), labelNames, other, total);
    }
}
