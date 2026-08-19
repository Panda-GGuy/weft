package dev.weft.api.telemetry;

/**
 * One source of metric series, read at scrape time (RFC-0009 §9.1).
 *
 * <p><b>Thread contract, and it is load-bearing.</b> {@link #collect} runs on
 * the scrape thread — <em>not</em> the server thread. A collector may read only
 * state that is safe from any thread: atomics, concurrent maps, immutable
 * snapshots published at a tick boundary.
 *
 * <p>In particular a collector must <b>not</b> walk the live
 * {@code RegionManager} maps. Those are plain collections mutated on the server
 * thread between ticks and at INGEST (RFC-0007 §3.1); reading them off-thread
 * is a data race. Region topology reaches the exporter through an immutable
 * snapshot instead. If a quantity cannot be read safely off-thread, snapshot it
 * at a phase boundary — do not reach across.
 *
 * <p><b>Fail-soft.</b> A collector that throws is logged once and skipped for
 * that scrape; the rest of the response still renders. A telemetry failure
 * must never affect the tick, and must not cost an operator their whole
 * dashboard either (RFC-0003 R2).
 *
 * <p><b>Absent, not zero</b> (RFC-0009 §4). A collector whose source module is
 * inactive emits <em>nothing</em>. A zero is a measurement claim: it says "this
 * cost nothing", where absence says "Weft is not measuring this right now".
 */
@FunctionalInterface
public interface Collector {

    /** Write this source's current series into {@code sink}. */
    void collect(MetricSink sink);
}
