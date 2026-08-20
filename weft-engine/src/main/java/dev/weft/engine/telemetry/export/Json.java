package dev.weft.engine.telemetry.export;

import java.util.List;
import java.util.Map;

/**
 * A minimal JSON writer for the event stream and the profiler snapshot
 * (RFC-0009 §5, §6).
 *
 * <p>Hand-rolled because {@code weft-engine} carries no third-party
 * dependencies (the build enforces no Minecraft imports; the module has kept
 * itself dependency-free besides). Gson is available loader-side, but the
 * exporter's pure parts live here, and one small writer is a cheaper price than
 * a dependency in the module every other module builds on.
 *
 * <p>Writes only what the schema needs: objects with insertion-ordered keys,
 * arrays, strings, finite numbers, booleans, null. Ordered keys are not
 * cosmetic — stable output is what lets the schema test and the golden-file
 * assertions diff cleanly.
 */
public final class Json {

    private Json() {}

    /**
     * Render a value: {@code Map} (object), {@code List}/{@code Object[]}
     * (array), {@code CharSequence} (string), {@code Number}, {@code Boolean},
     * or null.
     *
     * @throws IllegalArgumentException on a type with no JSON spelling — better
     *         a loud failure in a test than a silently truncated event line
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof CharSequence s) {
            writeString(out, s);
        } else if (value instanceof Number n) {
            writeNumber(out, n);
        } else if (value instanceof Boolean b) {
            out.append(b.booleanValue());
        } else if (value instanceof Map<?, ?> map) {
            writeObject(out, map);
        } else if (value instanceof List<?> list) {
            writeArray(out, list);
        } else if (value instanceof Object[] array) {
            writeArray(out, List.of(array));
        } else if (value instanceof long[] longs) {
            out.append('[');
            for (int i = 0; i < longs.length; i++) {
                out.append(i == 0 ? "" : ",").append(longs[i]);
            }
            out.append(']');
        } else if (value instanceof Enum<?> e) {
            writeString(out, e.name());
        } else {
            throw new IllegalArgumentException(
                    "no JSON spelling for " + value.getClass().getName());
        }
    }

    private static void writeObject(StringBuilder out, Map<?, ?> map) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, String.valueOf(entry.getKey()));
            out.append(':');
            writeValue(out, entry.getValue());
        }
        out.append('}');
    }

    private static void writeArray(StringBuilder out, List<?> list) {
        out.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(out, list.get(i));
        }
        out.append(']');
    }

    /**
     * JSON has no spelling for NaN or the infinities, and emitting the Java
     * ones produces a line no consumer can parse. They become null: absence of
     * a number, which is the truth about them.
     */
    private static void writeNumber(StringBuilder out, Number n) {
        double d = n.doubleValue();
        if (n instanceof Double || n instanceof Float) {
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                out.append("null");
                return;
            }
        }
        out.append(n);
    }

    static void writeString(StringBuilder out, CharSequence value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
