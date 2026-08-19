package dev.weft.engine.telemetry.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A deliberately strict reader for Prometheus/OpenMetrics text exposition,
 * used by {@link ExpositionFormatTest} to prove our output round-trips
 * (RFC-0009 §10.1).
 *
 * <p><b>Why this exists rather than a library.</b> Verified 2026-08-17: the
 * official Java Prometheus client ships <em>writers only</em> —
 * {@code io.prometheus:prometheus-metrics-exposition-textformats:1.8.0}
 * contains {@code PrometheusTextFormatWriter} and
 * {@code OpenMetricsTextFormatWriter} and no parser at all. Using its writer as
 * an oracle instead would compare our bytes against another implementation's
 * equally-legal formatting choices (it renders {@code 1.0} where we render
 * {@code 1}, and sorts labels), so a mismatch would prove nothing. The
 * canonical parser is Go's, reached through {@code promtool check metrics},
 * which gates this in CI and additionally lints naming and unit conventions.
 * This class is the fast offline half: it runs in JUnit, on every build.
 *
 * <p>Strict on purpose. It rejects things the Go parser tolerates, because they
 * are things <em>our</em> exporter must never emit: a sample with no preceding
 * {@code # TYPE}, a duplicate series, a counter not suffixed {@code _total}, a
 * histogram missing its {@code +Inf} bucket or with non-cumulative counts.
 * Malformed exposition is the classic silent exporter bug; a lenient gate would
 * reproduce it.
 */
final class ExpositionParser {

    private static final Pattern METRIC_NAME = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");
    private static final Pattern LABEL_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    /** One parsed sample line. */
    record Sample(String name, Map<String, String> labels, double value) {

        String seriesId() {
            return name + labels;
        }
    }

    /** The whole parsed body. */
    record Parsed(Map<String, String> help, Map<String, String> type, Map<String, String> unit,
                  List<Sample> samples, boolean sawEof) {

        Sample sample(String name, String... labelPairs) {
            Map<String, String> want = new LinkedHashMap<>();
            for (int i = 0; i < labelPairs.length; i += 2) {
                want.put(labelPairs[i], labelPairs[i + 1]);
            }
            return samples.stream()
                    .filter(s -> s.name().equals(name) && s.labels().equals(want))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "no sample " + name + want + " in:\n" + samples));
        }

        double value(String name, String... labelPairs) {
            return sample(name, labelPairs).value();
        }

        List<Sample> family(String name) {
            return samples.stream().filter(s -> s.name().equals(name)).toList();
        }
    }

    /** Thrown for anything our exporter must never emit. */
    static final class MalformedExposition extends RuntimeException {
        MalformedExposition(String message) {
            super(message);
        }
    }

    private ExpositionParser() {}

    static Parsed parse(String body, ExpositionFormat.Dialect dialect) {
        Map<String, String> help = new LinkedHashMap<>();
        Map<String, String> type = new LinkedHashMap<>();
        Map<String, String> unit = new LinkedHashMap<>();
        List<Sample> samples = new ArrayList<>();
        Set<String> seenSeries = new LinkedHashSet<>();
        boolean sawEof = false;

        if (body.indexOf('\r') >= 0) {
            throw new MalformedExposition("body contains CR; lines must be LF-terminated");
        }
        for (String line : body.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            if (sawEof) {
                throw new MalformedExposition("content after '# EOF': " + line);
            }
            if (line.startsWith("#")) {
                if (line.equals("# EOF")) {
                    sawEof = true;
                    continue;
                }
                String[] parts = line.split(" ", 4);
                if (parts.length < 3) {
                    continue; // a free-form comment; legal and ignorable
                }
                String keyword = parts[1];
                String name = parts[2];
                String rest = parts.length > 3 ? parts[3] : "";
                switch (keyword) {
                    case "HELP" -> {
                        requireName(name);
                        help.put(name, rest);
                    }
                    case "TYPE" -> {
                        requireName(name);
                        if (!List.of("counter", "gauge", "histogram", "summary", "untyped")
                                .contains(rest)) {
                            throw new MalformedExposition("bad metric type: " + line);
                        }
                        type.put(name, rest);
                    }
                    case "UNIT" -> {
                        requireName(name);
                        if (!name.endsWith("_" + rest)) {
                            throw new MalformedExposition(
                                    "OpenMetrics requires the unit to suffix the name: " + line);
                        }
                        unit.put(name, rest);
                    }
                    default -> { }
                }
                continue;
            }
            Sample sample = parseSample(line);
            if (!seenSeries.add(sample.seriesId())) {
                throw new MalformedExposition("duplicate series: " + sample.seriesId());
            }
            samples.add(sample);
        }

        if (dialect == ExpositionFormat.Dialect.OPENMETRICS && !sawEof) {
            throw new MalformedExposition("OpenMetrics body must end with '# EOF'");
        }
        if (dialect == ExpositionFormat.Dialect.PROMETHEUS && sawEof) {
            throw new MalformedExposition("Prometheus text format must not emit '# EOF'");
        }
        Parsed parsed = new Parsed(help, type, unit, samples, sawEof);
        validate(parsed, dialect);
        return parsed;
    }

    private static Sample parseSample(String line) {
        int brace = line.indexOf('{');
        int nameEnd = brace >= 0 ? brace : line.indexOf(' ');
        if (nameEnd < 0) {
            throw new MalformedExposition("sample line has no value: " + line);
        }
        String name = line.substring(0, nameEnd);
        requireName(name);

        Map<String, String> labels = new LinkedHashMap<>();
        int valueStart;
        if (brace >= 0) {
            int close = line.lastIndexOf('}');
            if (close < brace) {
                throw new MalformedExposition("unterminated label set: " + line);
            }
            parseLabels(line.substring(brace + 1, close), labels, line);
            valueStart = close + 1;
        } else {
            valueStart = nameEnd;
        }
        String tail = line.substring(valueStart).trim();
        if (tail.isEmpty()) {
            throw new MalformedExposition("sample line has no value: " + line);
        }
        // A trailing timestamp is legal; we never emit one, so reject it rather
        // than silently accepting an exporter that started to.
        String[] fields = tail.split(" ");
        if (fields.length != 1) {
            throw new MalformedExposition("unexpected trailing field: " + line);
        }
        return new Sample(name, labels, parseValue(fields[0], line));
    }

    private static void parseLabels(String inner, Map<String, String> out, String line) {
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && (inner.charAt(i) == ',' || inner.charAt(i) == ' ')) {
                i++;
            }
            if (i >= inner.length()) {
                break;
            }
            int eq = inner.indexOf('=', i);
            if (eq < 0) {
                throw new MalformedExposition("label without '=': " + line);
            }
            String name = inner.substring(i, eq).trim();
            requireLabelName(name);
            if (eq + 1 >= inner.length() || inner.charAt(eq + 1) != '"') {
                throw new MalformedExposition("label value must be quoted: " + line);
            }
            StringBuilder value = new StringBuilder();
            int j = eq + 2;
            boolean closed = false;
            while (j < inner.length()) {
                char c = inner.charAt(j);
                if (c == '\\') {
                    if (j + 1 >= inner.length()) {
                        throw new MalformedExposition("dangling escape: " + line);
                    }
                    char next = inner.charAt(++j);
                    value.append(switch (next) {
                        case 'n' -> '\n';
                        case '\\' -> '\\';
                        case '"' -> '"';
                        default -> throw new MalformedExposition(
                                "illegal escape \\" + next + " in: " + line);
                    });
                } else if (c == '"') {
                    closed = true;
                    j++;
                    break;
                } else {
                    value.append(c);
                }
                j++;
            }
            if (!closed) {
                throw new MalformedExposition("unterminated label value: " + line);
            }
            if (out.put(name, value.toString()) != null) {
                throw new MalformedExposition("duplicate label " + name + " in: " + line);
            }
            i = j;
        }
    }

    private static double parseValue(String field, String line) {
        return switch (field) {
            case "NaN" -> Double.NaN;
            case "+Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> {
                try {
                    // Reject Java-isms the Go parser would refuse.
                    if (field.endsWith("d") || field.endsWith("f") || field.startsWith("0x")) {
                        throw new NumberFormatException(field);
                    }
                    yield Double.parseDouble(field);
                } catch (NumberFormatException e) {
                    throw new MalformedExposition("unparseable value in: " + line);
                }
            }
        };
    }

    /** Our own contracts, above and beyond what the format demands. */
    private static void validate(Parsed parsed, ExpositionFormat.Dialect dialect) {
        for (Sample sample : parsed.samples()) {
            String family = familyOf(sample.name(), parsed);
            if (family == null) {
                throw new MalformedExposition(
                        "sample " + sample.name() + " has no '# TYPE' line");
            }
            if (!parsed.help().containsKey(family)) {
                throw new MalformedExposition("family " + family + " has no '# HELP' line");
            }
            if ("counter".equals(parsed.type().get(family))
                    && !sample.name().endsWith("_total")) {
                throw new MalformedExposition(
                        "counter series must be suffixed _total: " + sample.name());
            }
            if (dialect == ExpositionFormat.Dialect.OPENMETRICS
                    && "counter".equals(parsed.type().get(family))
                    && family.endsWith("_total")) {
                throw new MalformedExposition(
                        "OpenMetrics counter metadata must drop _total: " + family);
            }
        }
        for (Map.Entry<String, String> e : parsed.type().entrySet()) {
            if ("histogram".equals(e.getValue())) {
                validateHistogram(e.getKey(), parsed);
            }
        }
    }

    /**
     * Which family a sample belongs to: itself, or the base name for a
     * histogram's {@code _bucket}/{@code _sum}/{@code _count} children and an
     * OpenMetrics counter's {@code _total}.
     */
    private static String familyOf(String sampleName, Parsed parsed) {
        if (parsed.type().containsKey(sampleName)) {
            return sampleName;
        }
        for (String suffix : new String[]{"_bucket", "_sum", "_count", "_total"}) {
            if (sampleName.endsWith(suffix)) {
                String base = sampleName.substring(0, sampleName.length() - suffix.length());
                if (parsed.type().containsKey(base)) {
                    return base;
                }
            }
        }
        return null;
    }

    private static void validateHistogram(String family, Parsed parsed) {
        List<Sample> buckets = parsed.family(family + "_bucket");
        if (buckets.isEmpty()) {
            throw new MalformedExposition("histogram " + family + " has no buckets");
        }
        // Group buckets by their non-`le` labels: one series per label tuple.
        Map<String, List<Sample>> bySeries = new LinkedHashMap<>();
        for (Sample bucket : buckets) {
            Map<String, String> key = new LinkedHashMap<>(bucket.labels());
            String le = key.remove("le");
            if (le == null) {
                throw new MalformedExposition(
                        "histogram bucket without 'le': " + bucket.seriesId());
            }
            bySeries.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(bucket);
        }
        for (Map.Entry<String, List<Sample>> entry : bySeries.entrySet()) {
            double previousBound = Double.NEGATIVE_INFINITY;
            double previousCount = -1;
            boolean sawInf = false;
            for (Sample bucket : entry.getValue()) {
                double bound = parseValue(bucket.labels().get("le"), bucket.seriesId());
                if (bound <= previousBound) {
                    throw new MalformedExposition(
                            "histogram bounds must ascend: " + family + " " + entry.getKey());
                }
                if (bucket.value() < previousCount) {
                    throw new MalformedExposition(
                            "histogram counts must be cumulative: " + family + " "
                                    + entry.getKey());
                }
                previousBound = bound;
                previousCount = bucket.value();
                sawInf |= Double.isInfinite(bound);
            }
            if (!sawInf) {
                throw new MalformedExposition(
                        "histogram missing le=\"+Inf\" bucket: " + family + " " + entry.getKey());
            }
        }
        if (parsed.family(family + "_sum").isEmpty()) {
            throw new MalformedExposition("histogram " + family + " has no _sum");
        }
        if (parsed.family(family + "_count").size() != bySeries.size()) {
            throw new MalformedExposition(
                    "histogram " + family + " needs one _count per label tuple");
        }
    }

    private static void requireName(String name) {
        if (!METRIC_NAME.matcher(name).matches()) {
            throw new MalformedExposition("illegal metric name: " + name);
        }
    }

    private static void requireLabelName(String name) {
        if (!LABEL_NAME.matcher(name).matches()) {
            throw new MalformedExposition("illegal label name: " + name);
        }
    }
}
