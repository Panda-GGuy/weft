package dev.weft.neoforge;

/** Placeholder until this moves to NeoForge's config system. RFC §4.2 tunables. */
public final class WeftConfig {
    private WeftConfig() {}

    /** Chebyshev chunk distance within which regions merge. Validate via P0 data. */
    public static final int MERGE_DISTANCE = 8;

    /** Threads reserved for IO / netty / GC breathing room. */
    public static final int RESERVED_THREADS = 2;
}
