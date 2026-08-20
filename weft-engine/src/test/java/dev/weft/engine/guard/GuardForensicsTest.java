package dev.weft.engine.guard;

import dev.weft.api.telemetry.WeftTelemetry;
import dev.weft.engine.region.ShardKey;
import dev.weft.engine.telemetry.export.ExpositionFormat;
import dev.weft.engine.telemetry.export.MetricSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-7's guard forensics (RFC-0009 §3.8). A trip already carried the RFC-0001
 * §4.4 report — as an exception message string, which the count outlived and the
 * story did not. These assert the structured record, and that it costs nothing
 * when nobody is listening.
 */
class GuardForensicsTest {

    private final List<WeftGuards.GuardTrip> observed = new ArrayList<>();

    @BeforeEach
    void install() {
        WeftTelemetry.reset();
        WeftTelemetry.setEnabled(true);
        WeftGuards.setTripListener(observed::add);
    }

    @AfterEach
    void detach() {
        WeftGuards.setTripListener(null);
        WeftGuards.setMode(WeftGuards.Mode.DEV);
        WeftTelemetry.setEnabled(false);
        WeftTelemetry.reset();
        observed.clear();
    }

    @Test
    void aDegradedTripCarriesTheOwnershipContextTargetAndDegradation() {
        WeftGuards.setMode(WeftGuards.Mode.DEGRADE);
        ThreadContext.enter(ThreadContext.Kind.REGION, 3L);
        try {
            // Region 3 reaching into region 9: the compat fact the lane exists
            // to find, routed rather than crashed.
            assertFalse(WeftGuards.checkRegionMutation(9L));
        } finally {
            ThreadContext.exit();
        }

        assertEquals(1, observed.size());
        WeftGuards.GuardTrip trip = observed.get(0);
        assertEquals(WeftGuards.TripKind.REGION_MUTATION, trip.kind());
        assertEquals(WeftGuards.Severity.DEGRADED_TO_MAIL, trip.severity());
        assertEquals(WeftGuards.Degradation.ROUTED_AS_MAIL, trip.degradation());
        assertEquals(ThreadContext.Kind.REGION, trip.contextKind());
        assertEquals(3L, trip.contextOwner());
        assertEquals("region", trip.targetKind());
        assertEquals(9L, trip.targetId());
        assertEquals(Thread.currentThread().getName(), trip.thread());
    }

    @Test
    void aThrowingTripIsRecordedBeforeTheExceptionPropagates() {
        WeftGuards.setMode(WeftGuards.Mode.DEV);
        ThreadContext.enter(ThreadContext.Kind.GRAPH, 1L);
        try {
            assertThrows(WeftGuards.WrongOwnerException.class,
                    () -> WeftGuards.checkRegionMutation(4L));
        } finally {
            ThreadContext.exit();
        }

        // The forensics must survive the throw, or a crash report loses the one
        // piece of context that explains it.
        assertEquals(1, observed.size());
        assertEquals(WeftGuards.Severity.DEV_THROW, observed.get(0).severity());
        assertEquals(WeftGuards.Degradation.THREW, observed.get(0).degradation());
    }

    @Test
    void hardenedModeIsADistinctSeverityFromDev() {
        WeftGuards.setMode(WeftGuards.Mode.HARDENED);
        ThreadContext.enter(ThreadContext.Kind.NONE, 0L);
        try {
            assertThrows(WeftGuards.WrongOwnerException.class,
                    () -> WeftGuards.checkRegionMutation(1L));
        } finally {
            ThreadContext.exit();
        }
        assertEquals(WeftGuards.Severity.HARDENED_THROW, observed.get(0).severity());
    }

    @Test
    void aShardTripNamesTheShardNotItsRegion() {
        WeftGuards.setMode(WeftGuards.Mode.DEGRADE);
        long shard = ShardKey.pack(7L, 2);
        ThreadContext.enter(ThreadContext.Kind.SHARD, ShardKey.pack(7L, 1));
        try {
            assertFalse(WeftGuards.checkShardMutation(shard));
        } finally {
            ThreadContext.exit();
        }
        // RFC-0004 §2.2 keeps shard identity distinct from region identity; the
        // forensics have to preserve that or a cross-shard bug reads as a
        // cross-region one.
        assertEquals(WeftGuards.TripKind.SHARD_MUTATION, observed.get(0).kind());
        assertEquals("shard", observed.get(0).targetKind());
        assertEquals(shard, observed.get(0).targetId());
    }

    @Test
    void theStackNamesTheCallerNotTheGuard() {
        WeftGuards.setMode(WeftGuards.Mode.DEGRADE);
        ThreadContext.enter(ThreadContext.Kind.GRAPH, 1L);
        try {
            WeftGuards.checkRegionMutation(2L);
        } finally {
            ThreadContext.exit();
        }
        List<String> stack = observed.get(0).stack();
        assertFalse(stack.isEmpty());
        assertFalse(stack.get(0).contains("WeftGuards"),
                "the first frame should be the offending caller: " + stack.get(0));
        assertTrue(stack.get(0).contains("GuardForensicsTest"), stack.get(0));
        assertTrue(stack.size() <= 12, "stack is capped so a trip storm cannot fill the file");
    }

    @Test
    void tripsAreExportedByKindAndSeverity() {
        WeftGuards.setMode(WeftGuards.Mode.DEGRADE);
        ThreadContext.enter(ThreadContext.Kind.REGION, 1L);
        try {
            WeftGuards.checkRegionMutation(2L);
            WeftGuards.checkRegionMutation(3L);
        } finally {
            ThreadContext.exit();
        }

        MetricSnapshot snapshot = new MetricSnapshot(50);
        WeftTelemetry.collectInto(snapshot, (c, e) -> {
            throw e;
        });
        String body = ExpositionFormat.render(snapshot,
                ExpositionFormat.Dialect.PROMETHEUS).body();
        assertTrue(body.contains(
                "weft_guard_trips_total{kind=\"region_mutation\",severity=\"degraded_to_mail\"} 2"),
                body);
    }

    @Test
    void withNoListenerATripStillCountsButBuildsNoRecord() {
        WeftGuards.setTripListener(null);
        WeftGuards.setMode(WeftGuards.Mode.DEGRADE);
        long before = WeftGuards.tripCount();
        ThreadContext.enter(ThreadContext.Kind.GRAPH, 1L);
        try {
            WeftGuards.checkRegionMutation(5L);
        } finally {
            ThreadContext.exit();
        }
        assertEquals(before + 1, WeftGuards.tripCount());
        // R6: with the module inactive there is no listener, so the stack walk —
        // the only expensive part — never happens.
        assertTrue(observed.isEmpty());
    }
}
