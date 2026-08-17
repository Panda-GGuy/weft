package dev.weft.api;

/**
 * Execution tier assigned to every mod (and, at finer grain, every class)
 * at load time. RFC-0001 §7.1.
 */
public enum CompatTier {
    /** Vanilla content reimplemented region-aware by Weft itself. */
    ENGINE,
    /** Verified thread-safe (annotation or signed compat manifest). Runs parallel. */
    VERIFIED,
    /** Unknown code. Runs serialized on the legacy lane with single-thread semantics. */
    LEGACY,
    /** Patches the tick loop itself or otherwise cannot coexist. Load-time report. */
    CONFLICTING
}
