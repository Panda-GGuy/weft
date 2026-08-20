package dev.weft.engine.telemetry.export;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a {@link MetricSnapshot} as Prometheus text exposition or as
 * OpenMetrics text (RFC-0009 §3.10).
 *
 * <p><b>Why both.</b> RFC-0002 §WS-7 says "emit standard OpenMetrics text"; the
 * WS-7 brief says "text exposition format". They are different formats, so the
 * endpoint serves Prometheus text by default and OpenMetrics when the scraper's
 * {@code Accept} header asks for it. Both readings are satisfied and neither is
 * guessed at.
 *
 * <p><b>The differences that actually bite.</b> OpenMetrics strips a trailing
 * {@code _total} from the family name on the {@code # TYPE}/{@code # HELP}
 * lines while keeping it on the sample lines, and the body must end with
 * {@code # EOF}. Getting either wrong produces a body that one of the two
 * parsers rejects — and malformed exposition is the classic silent exporter
 * bug, which is why {@code promtool check metrics} gates this in CI.
 *
 * <p>Stateless and thread-safe.
 */
public final class ExpositionFormat {

    /** Response content types, exactly as a scraper expects to see them. */
    public static final String PROMETHEUS_CONTENT_TYPE =
            "text/plain; version=0.0.4; charset=utf-8";
    public static final String OPENMETRICS_CONTENT_TYPE =
            "application/openmetrics-text; version=1.0.0; charset=utf-8";

    public enum Dialect { PROMETHEUS, OPENMETRICS }

    /**
     * What a render produced besides bytes.
     *
     * @param body           the exposition text
     * @param dialect        which format it is in
     * @param seriesWritten  sample lines' worth of distinct series
     * @param duplicatesDropped series skipped because an identical
     *                       name+labels pair had already been written —
     *                       duplicate series make a scrape invalid, so they are
     *                       dropped rather than emitted, and counted rather
     *                       than swallowed
     */
    public record Rendered(String body, Dialect dialect, int seriesWritten,
                           int duplicatesDropped) {

        public String contentType() {
            return dialect == Dialect.OPENMETRICS
                    ? OPENMETRICS_CONTENT_TYPE : PROMETHEUS_CONTENT_TYPE;
        }
    }

    private ExpositionFormat() {}

    /** Pick a dialect from a scraper's {@code Accept} header; null/absent wins Prometheus. */
    public static Dialect negotiate(String acceptHeader) {
        return acceptHeader != null && acceptHeader.contains("application/openmetrics-text")
                ? Dialect.OPENMETRICS : Dialect.PROMETHEUS;
    }

    public static Rendered render(MetricSnapshot snapshot, Dialect dialect) {
        StringBuilder out = new StringBuilder(8192);
        Set<String> written = new HashSet<>();
        int series = 0;
        int duplicates = 0;

        for (List<MetricPoint> family : snapshot.families()) {
            if (family.isEmpty()) {
                continue;
            }
            MetricPoint first = family.get(0);
            String metadataName = metadataName(first, dialect);
            out.append("# HELP ").append(metadataName).append(' ')
               .append(escapeHelp(first.help())).append('\n');
            out.append("# TYPE ").append(metadataName).append(' ')
               .append(typeOf(first)).append('\n');
            if (dialect == Dialect.OPENMETRICS) {
                String unit = unitOf(metadataName);
                if (unit != null) {
                    out.append("# UNIT ").append(metadataName).append(' ').append(unit).append('\n');
                }
            }
            for (MetricPoint point : family) {
                String identity = point.name() + labels(point, null, null);
                if (!written.add(identity)) {
                    duplicates++;
                    continue;
                }
                series++;
                writePoint(out, point);
            }
        }

        if (dialect == Dialect.OPENMETRICS) {
            out.append("# EOF\n");
        }
        return new Rendered(out.toString(), dialect, series, duplicates);
    }

    private static void writePoint(StringBuilder out, MetricPoint point) {
        if (point instanceof MetricPoint.Histogram h) {
            for (int i = 0; i < h.upperBounds().length; i++) {
                out.append(h.name()).append("_bucket")
                   .append(labels(h, "le", formatDouble(h.upperBounds()[i])))
                   .append(' ').append(h.cumulativeCounts()[i]).append('\n');
            }
            out.append(h.name()).append("_bucket").append(labels(h, "le", "+Inf"))
               .append(' ').append(h.count()).append('\n');
            out.append(h.name()).append("_sum").append(labels(h, null, null))
               .append(' ').append(formatDouble(h.sum())).append('\n');
            out.append(h.name()).append("_count").append(labels(h, null, null))
               .append(' ').append(h.count()).append('\n');
            return;
        }
        double value = point.rankValue();
        out.append(point.name()).append(labels(point, null, null))
           .append(' ').append(formatDouble(value)).append('\n');
    }

    /**
     * OpenMetrics carries counter metadata under the family name without the
     * {@code _total} suffix, while the samples keep it. Prometheus text uses the
     * full name in both places.
     */
    private static String metadataName(MetricPoint point, Dialect dialect) {
        if (dialect == Dialect.OPENMETRICS && point instanceof MetricPoint.Counter
                && point.name().endsWith("_total")) {
            return point.name().substring(0, point.name().length() - "_total".length());
        }
        return point.name();
    }

    /** OpenMetrics {@code # UNIT} must be a suffix of the family name, or absent. */
    private static String unitOf(String metadataName) {
        for (String unit : new String[]{"seconds", "bytes", "ratio"}) {
            if (metadataName.endsWith('_' + unit)) {
                return unit;
            }
        }
        return null;
    }

    private static String typeOf(MetricPoint point) {
        if (point instanceof MetricPoint.Counter) {
            return "counter";
        }
        return point instanceof MetricPoint.Histogram ? "histogram" : "gauge";
    }

    /**
     * Render a label set, optionally with one appended pair (the histogram
     * {@code le}). Empty label sets render as the empty string, not
     * {@code "{}"} — legal either way, but bare names are what every other
     * exporter emits and what a human expects to read.
     */
    private static String labels(MetricPoint point, String extraName, String extraValue) {
        String[] names = point.labelNames();
        if (names.length == 0 && extraName == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(32);
        sb.append('{');
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(names[i]).append("=\"")
              .append(escapeLabelValue(point.labelValues()[i])).append('"');
        }
        if (extraName != null) {
            if (names.length > 0) {
                sb.append(',');
            }
            sb.append(extraName).append("=\"").append(extraValue).append('"');
        }
        return sb.append('}').toString();
    }

    /** {@code \} and newline are escaped in HELP; quotes are not. CR dropped, as below. */
    static String escapeHelp(String help) {
        return help.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n");
    }

    /**
     * {@code \}, {@code "} and newline are escaped in a label value.
     *
     * <p>Carriage return is <em>removed</em> rather than escaped: the format
     * defines only the three escapes above, so there is no legal spelling for a
     * CR, and a raw one would terminate the line early and corrupt the rest of
     * the scrape. No Weft label value (mod id, registry id, dimension id) can
     * contain one — this is here so a future one cannot break the endpoint.
     */
    static String escapeLabelValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "\\n");
    }

    /**
     * Prometheus-compatible number rendering. Integral values print without a
     * decimal point (what every mainstream exporter emits, and what keeps a
     * scrape body small), and the three non-finite values get their spec
     * spellings rather than Java's.
     */
    static String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }
}
