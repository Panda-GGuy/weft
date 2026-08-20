package dev.weft.api.telemetry;

/**
 * Where a {@link Collector} writes the series it produces (RFC-0009 §11).
 *
 * <p>Called on the <b>scrape thread</b>, never on the server thread. The
 * exporter supplies the implementation; collectors only push values in.
 *
 * <p><b>Naming contract.</b> {@code name} is the full Prometheus series name,
 * already namespaced {@code weft_} and already in base units — seconds, bytes,
 * ratio — with {@code _total} on counters. RFC-0009 Appendix A is the list; a
 * name not on it has not been reviewed, and these names are API.
 *
 * <p>{@code labelNames} and {@code labelValues} must be the same length.
 * Passing the same {@code name} with different {@code labelNames} in one
 * collection is a bug: Prometheus families own their label set.
 */
public interface MetricSink {

    /**
     * A monotonically non-decreasing total. Most Weft counters mirror an
     * existing cumulative source (a {@code LongAdder}, a {@code Map} of
     * per-mod nanos) rather than being incremented here, so this takes the
     * running total rather than a delta.
     */
    void counter(String name, String help, String[] labelNames, String[] labelValues,
                 double value);

    /** A value that may move in either direction. */
    void gauge(String name, String help, String[] labelNames, String[] labelValues,
               double value);

    /**
     * A cumulative histogram.
     *
     * @param upperBounds       bucket upper bounds, ascending, excluding
     *                          {@code +Inf} (the formatter adds it)
     * @param cumulativeCounts  counts per bucket, cumulative, same length as
     *                          {@code upperBounds}; observations above the
     *                          last bound are carried by {@code count}
     * @param sum               sum of all observed values
     * @param count             number of observations (the {@code +Inf} bucket)
     */
    void histogram(String name, String help, String[] labelNames, String[] labelValues,
                   double[] upperBounds, long[] cumulativeCounts, double sum, long count);

    String[] NO_LABELS = new String[0];

    default void counter(String name, String help, double value) {
        counter(name, help, NO_LABELS, NO_LABELS, value);
    }

    default void gauge(String name, String help, double value) {
        gauge(name, help, NO_LABELS, NO_LABELS, value);
    }

    /** Single-label convenience — the common shape (one {@code level}, one {@code modid}). */
    default void counter(String name, String help, String labelName, String labelValue,
                         double value) {
        counter(name, help, new String[]{labelName}, new String[]{labelValue}, value);
    }

    default void gauge(String name, String help, String labelName, String labelValue,
                       double value) {
        gauge(name, help, new String[]{labelName}, new String[]{labelValue}, value);
    }
}
