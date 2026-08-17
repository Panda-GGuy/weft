package dev.weft.engine.sched;

/** The seven-phase pipeline of RFC-0001 §4.3. Order is the contract. */
public enum TickPhase {
    INGEST,   // drain inputs, route to owners
    REGION,   // parallel region ticks + graph computes
    MAIL,     // deliver cross-owner mail
    COMMIT,   // apply graph commit logs via owning regions
    LEGACY,   // serialized unverified-mod execution (single-thread semantics)
    GLOBAL,   // global lane (time, weather, players, scoreboards)
    EGRESS    // trackers, network flush, async save handoff
}
