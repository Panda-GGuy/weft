package dev.weft.neoforge;

/** Placeholder until this moves to NeoForge's config system. RFC §4.2 tunables. */
public final class WeftConfig {
    private WeftConfig() {}

    /** Chebyshev chunk distance within which regions merge. Validate via P0 data. */
    public static final int MERGE_DISTANCE = 8;

    /** Threads reserved for IO / netty / GC breathing room. */
    public static final int RESERVED_THREADS = 2;

    // --- P0 profiler (RFC §9.1) ---

    /** Rolling window of completed ticks the report is computed over. */
    public static final int PROFILE_WINDOW_TICKS = 100;

    /** Log a report summary to console every N ticks (1200 = 60s). 0 = off. */
    public static final int REPORT_LOG_INTERVAL_TICKS = 1200;

    /** Worker counts to estimate hypothetical speedup for. */
    public static final int[] SPEEDUP_WORKER_COUNTS = {2, 4, 8, 16};

    /** How many cost sources the report lists. */
    public static final int REPORT_TOP_TYPES = 12;
}
