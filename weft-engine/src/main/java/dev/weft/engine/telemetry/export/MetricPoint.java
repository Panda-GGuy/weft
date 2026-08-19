package dev.weft.engine.telemetry.export;

/**
 * One collected series (RFC-0009 §11): a metric name, a label tuple, and a
 * value. The immutable result of a {@link dev.weft.api.telemetry.Collector}
 * run, and the only thing {@link ExpositionFormat} knows how to render.
 *
 * <p>Label names and values are parallel arrays of equal length, kept in the
 * order the collector supplied them — Prometheus does not care about label
 * order, but stable order keeps our output diffable in tests.
 */
public sealed interface MetricPoint {

    String name();

    String help();

    String[] labelNames();

    String[] labelValues();

    /**
     * The value used to rank this point when a family exceeds the cardinality
     * cap (§7): the total for counters and gauges, the observation count for
     * histograms. Ranking a histogram by its {@code sum} would let one slow
     * outlier outrank a genuinely hot series.
     */
    double rankValue();

    record Counter(String name, String help, String[] labelNames, String[] labelValues,
                   double value) implements MetricPoint {
        @Override
        public double rankValue() {
            return value;
        }
    }

    record Gauge(String name, String help, String[] labelNames, String[] labelValues,
                 double value) implements MetricPoint {
        @Override
        public double rankValue() {
            return value;
        }
    }

    /**
     * A cumulative histogram. {@code cumulativeCounts} is parallel to
     * {@code upperBounds} and already cumulated; {@code count} is the
     * {@code +Inf} bucket, so observations above the last bound are visible as
     * {@code count} exceeding the last cumulative entry.
     */
    record Histogram(String name, String help, String[] labelNames, String[] labelValues,
                     double[] upperBounds, long[] cumulativeCounts, double sum,
                     long count) implements MetricPoint {
        @Override
        public double rankValue() {
            return count;
        }
    }
}
