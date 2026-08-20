package dev.weft.engine.telemetry.export;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One line of the newline-delimited JSON event stream (RFC-0009 §5): the
 * discrete things a 10-second gauge sample loses.
 *
 * <p>A producer supplies only {@link #kind} and {@link #data}. The envelope —
 * {@code v}, {@code ts}, {@code server_id}, {@code weft_version},
 * {@code mc_version} — is stamped by {@link NdjsonEventSink}, because a producer
 * on the server thread should not be reading a clock or a version table, and
 * because one owner of the envelope means one place for it to be right.
 *
 * <p><b>Field names are API.</b> Once shipped, a consumer's parser depends on
 * them. Adding a field is compatible; renaming or removing one is not, and
 * needs the envelope's {@code v} to change so a consumer can reject rather than
 * silently misinterpret. {@code weft-events.schema.json} is the contract and is
 * enforced by {@code EventSchemaTest}.
 */
public record WeftEvent(Kind kind, Map<String, Object> data) {

    /** The envelope version. Bump only on an incompatible change to any kind. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * The emitted kinds. The wire value is the lower-case name; the enum is the
     * closed set, so a typo cannot invent a new kind that no consumer expects
     * and no schema covers.
     */
    public enum Kind {
        /** RFC-0001 §4.4 forensics: the highest-value line in the file. */
        GUARD_TRIP,
        MODULE_STATE_CHANGE,
        SERVICE_FALLBACK,
        REGION_MERGE,
        REGION_SPLIT,
        PROFILER_SNAPSHOT,
        /** The full RFC-0003 R5 posture table, once at boot. */
        STARTUP_POSTURE,
        CONFIG_CHANGE,
        /** A tick past {@code tickOutlierFactor} x the rolling median. */
        TICK_OUTLIER;

        public String wireName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Kind ofWireName(String wire) {
            return valueOf(wire.toUpperCase(java.util.Locale.ROOT));
        }
    }

    public WeftEvent {
        if (kind == null) {
            throw new IllegalArgumentException("event kind is required");
        }
        // Not Map.copyOf: that returns an unordered map, and field order in the
        // rendered line is part of what keeps the output diffable.
        data = data == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    /**
     * Start a payload. Insertion-ordered so the rendered line is stable, which
     * is what makes the schema test and any golden-file comparison diffable.
     */
    public static Builder of(Kind kind) {
        return new Builder(kind);
    }

    /** Payload builder. Null values are dropped rather than emitted as null. */
    public static final class Builder {

        private final Kind kind;
        private final Map<String, Object> data = new LinkedHashMap<>();

        private Builder(Kind kind) {
            this.kind = kind;
        }

        public Builder put(String key, Object value) {
            if (value != null) {
                data.put(key, value);
            }
            return this;
        }

        public Builder put(String key, long value) {
            data.put(key, value);
            return this;
        }

        public Builder put(String key, double value) {
            data.put(key, value);
            return this;
        }

        public Builder put(String key, boolean value) {
            data.put(key, value);
            return this;
        }

        public WeftEvent build() {
            return new WeftEvent(kind, data);
        }
    }
}
