package dev.weft.engine.telemetry.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import dev.weft.engine.telemetry.RegionizabilityAnalyzer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-7's event-stream gate (RFC-0009 §10.3). {@code weft-events.schema.json} is
 * the consumer-facing API contract, so it is checked in and enforced: every
 * emitted kind must validate, and no kind may exist without a schema branch.
 *
 * <p>Validated by a real JSON Schema implementation
 * ({@code com.networknt:json-schema-validator}, draft 2020-12) rather than by an
 * approximation. An approximate validator silently ignores the keywords it does
 * not implement, which is the same failure mode as having no gate.
 */
class EventSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JsonSchema schema;
    private static JsonNode schemaNode;

    @BeforeAll
    static void loadSchema() throws IOException {
        String text = schemaText();
        schemaNode = MAPPER.readTree(text);
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(text);
    }

    private static String schemaText() throws IOException {
        try (InputStream in = NdjsonEventSink.class
                .getResourceAsStream("/weft-events.schema.json")) {
            assertNotNull(in, "weft-events.schema.json missing from resources");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static NdjsonEventSink sink(Path dir) throws IOException {
        return new NdjsonEventSink(dir.resolve("weft-events.ndjson"), 1 << 20,
                "11111111-2222-3333-4444-555555555555", "0.1.0-alpha", "1.21.1",
                () -> Instant.parse("2026-08-17T14:03:11.482Z"));
    }

    /** One representative payload per kind, exercising the required fields. */
    private static Map<WeftEvent.Kind, WeftEvent> sampleEvents() {
        Map<WeftEvent.Kind, WeftEvent> out = new EnumMap<>(WeftEvent.Kind.class);

        out.put(WeftEvent.Kind.GUARD_TRIP, WeftEvent.of(WeftEvent.Kind.GUARD_TRIP)
                .put("kind", "region_mutation")
                .put("severity", "degraded_to_mail")
                .put("thread", "weft-worker-3")
                .put("context_kind", "SHARD")
                .put("context_owner", 4611686018427387904L)
                .put("target_kind", "region")
                .put("target_id", 7L)
                .put("degradation", "routed_as_mail")
                .put("stack", List.of("dev.weft.Foo.bar(Foo.java:12)"))
                .build());

        out.put(WeftEvent.Kind.MODULE_STATE_CHANGE,
                WeftEvent.of(WeftEvent.Kind.MODULE_STATE_CHANGE)
                        .put("module", "activation")
                        .put("from", "active")
                        .put("to", "yielded")
                        .put("reason", "ownership conflict")
                        .put("neighbor", "servercore")
                        .build());

        out.put(WeftEvent.Kind.SERVICE_FALLBACK, WeftEvent.of(WeftEvent.Kind.SERVICE_FALLBACK)
                .put("service", "spawn_density")
                .put("level", "minecraft:overworld")
                .put("reason", "result not exactly one tick fresh")
                .put("consecutive", 3L)
                .put("latched", false)
                .build());

        out.put(WeftEvent.Kind.REGION_MERGE, WeftEvent.of(WeftEvent.Kind.REGION_MERGE)
                .put("level", "minecraft:overworld")
                .put("region_ids", List.of(3L, 9L))
                .put("result_ids", List.of(3L))
                .put("chunks_before", 240L)
                .put("chunks_after", 241L)
                .build());

        out.put(WeftEvent.Kind.REGION_SPLIT, WeftEvent.of(WeftEvent.Kind.REGION_SPLIT)
                .put("level", "minecraft:the_nether")
                .put("region_ids", List.of(3L))
                .put("result_ids", List.of(3L, 12L))
                .put("chunks_before", 200L)
                .put("chunks_after", 199L)
                .build());

        RegionizabilityAnalyzer.Report report = new RegionizabilityAnalyzer.Report(
                1_000_000L, 800_000L, 200_000L,
                List.of(new RegionizabilityAnalyzer.RegionCost(1L, 64, 800_000L)),
                List.of(new RegionizabilityAnalyzer.TypeCost("create:mechanical_press",
                        400_000L, 12L)),
                Map.of(2, 1.6, 4, 2.4),
                50_000L, 25_000L, 700_000L, 140_000L, 40_000L, 20_000L);
        // The real serializer's output, not a hand-written stand-in: the thing
        // that ships is the thing the schema is checked against.
        out.put(WeftEvent.Kind.PROFILER_SNAPSHOT, new WeftEvent(
                WeftEvent.Kind.PROFILER_SNAPSHOT,
                ProfilerSnapshotJson.of(report, 100, 100, true)));

        out.put(WeftEvent.Kind.STARTUP_POSTURE, WeftEvent.of(WeftEvent.Kind.STARTUP_POSTURE)
                .put("modules", List.of(
                        orderedMap("module", "profiler", "state", "active",
                                "detail", "cooperating with spark"),
                        orderedMap("module", "observability", "state", "active",
                                "detail", "127.0.0.1:9940")))
                .build());

        out.put(WeftEvent.Kind.CONFIG_CHANGE, WeftEvent.of(WeftEvent.Kind.CONFIG_CHANGE)
                .put("key", "profilingEnabled")
                .put("from", true)
                .put("to", false)
                .put("source", "command")
                .build());

        out.put(WeftEvent.Kind.TICK_OUTLIER, WeftEvent.of(WeftEvent.Kind.TICK_OUTLIER)
                .put("tick", 128_400L)
                .put("duration_seconds", 0.412)
                .put("median_seconds", 0.048)
                .put("factor", 8.58)
                .put("top_sources", List.of(
                        orderedMap("source", "BLOCK_ENTITY", "type", "create:mechanical_press",
                                "seconds", 0.21)))
                .put("phase_breakdown", orderedMap("REGION", 0.30, "LEGACY", 0.09))
                .build());
        return out;
    }

    private static Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void everyEmittedKindValidatesAgainstTheCommittedSchema() throws IOException {
        Path dir = Files.createTempDirectory("weft-events");
        try (NdjsonEventSink sink = sink(dir)) {
            List<String> failures = new ArrayList<>();
            for (Map.Entry<WeftEvent.Kind, WeftEvent> entry : sampleEvents().entrySet()) {
                String line = sink.render(entry.getValue());
                Set<ValidationMessage> errors = schema.validate(MAPPER.readTree(line));
                if (!errors.isEmpty()) {
                    failures.add(entry.getKey() + " -> " + errors + "\n  line: " + line);
                }
            }
            assertTrue(failures.isEmpty(), String.join("\n", failures));
        }
    }

    /**
     * The gate that keeps the schema honest as kinds are added: a new
     * {@code Kind} with no schema branch would otherwise validate vacuously
     * against the permissive root {@code data} object and ship unspecified.
     */
    @Test
    void noKindExistsWithoutASchemaBranch() {
        Set<String> javaKinds = Arrays.stream(WeftEvent.Kind.values())
                .map(WeftEvent.Kind::wireName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> declared = new TreeSet<>();
        for (JsonNode value : schemaNode.at("/properties/kind/enum")) {
            declared.add(value.asText());
        }
        assertEquals(javaKinds, declared,
                "the schema's kind enum and WeftEvent.Kind must match exactly");

        Set<String> branched = new HashSet<>();
        for (JsonNode branch : schemaNode.get("allOf")) {
            JsonNode kind = branch.at("/if/properties/kind");
            if (kind.has("const")) {
                branched.add(kind.get("const").asText());
            }
            for (JsonNode value : kind.path("enum")) {
                branched.add(value.asText());
            }
        }
        assertEquals(javaKinds, new TreeSet<>(branched),
                "every kind needs its own payload branch; an unbranched kind ships unspecified");
    }

    @Test
    void theValidatorActuallyRejectsBadEvents() throws IOException {
        // A missing required forensic field. Without this test a permissive
        // schema would look exactly like a passing one.
        String missingDegradation = """
                {"v":1,"ts":"2026-08-17T14:03:11.482Z","kind":"guard_trip",
                 "server_id":"s","weft_version":"0.1.0","mc_version":"1.21.1",
                 "data":{"kind":"region_mutation","severity":"dev_throw","thread":"t",
                         "context_kind":"SHARD","context_owner":1,"target_kind":"region",
                         "target_id":2}}""";
        assertFalse(schema.validate(MAPPER.readTree(missingDegradation)).isEmpty(),
                "a guard_trip without 'degradation' must be rejected");

        String badSeverity = missingDegradation
                .replace("\"target_id\":2}", "\"target_id\":2,\"degradation\":\"shrugged\"}");
        assertFalse(schema.validate(MAPPER.readTree(badSeverity)).isEmpty(),
                "an undefined degradation value must be rejected");

        String badTimestamp = """
                {"v":1,"ts":"2026-08-17 14:03:11Z","kind":"config_change","server_id":"s",
                 "weft_version":"0.1.0","mc_version":"1.21.1",
                 "data":{"key":"k","to":1,"source":"file"}}""";
        assertFalse(schema.validate(MAPPER.readTree(badTimestamp)).isEmpty(),
                "a non-RFC-3339 timestamp must be rejected");

        String unknownEnvelopeField = """
                {"v":1,"ts":"2026-08-17T14:03:11.482Z","kind":"config_change","server_id":"s",
                 "weft_version":"0.1.0","mc_version":"1.21.1","extra":true,
                 "data":{"key":"k","to":1,"source":"file"}}""";
        assertFalse(schema.validate(MAPPER.readTree(unknownEnvelopeField)).isEmpty(),
                "an unknown envelope field must be rejected, not tolerated");
    }

    @Test
    void aVacuousProfilerWindowOmitsDerivedFieldsRatherThanZeroingThem() {
        Map<String, Object> empty = ProfilerSnapshotJson.of(null, 0, 100, true);
        assertEquals(0, empty.get("ticks_analyzed"));
        // Absent, not zero: a document full of zeros reads as a measurement of
        // an idle server rather than as the absence of measurement.
        assertFalse(empty.containsKey("total_nanos"));
        assertFalse(empty.containsKey("top_types"));
    }
}
