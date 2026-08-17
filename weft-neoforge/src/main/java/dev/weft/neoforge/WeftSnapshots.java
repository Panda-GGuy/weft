package dev.weft.neoforge;

import dev.weft.api.graph.WorldSnapshot;

import java.util.OptionalLong;

/** Snapshot implementations. P3 replaces EMPTY with copy-on-read views (RFC §5.2). */
final class WeftSnapshots {
    private WeftSnapshots() {}

    static final WorldSnapshot EMPTY = new WorldSnapshot() {
        @Override public long tick() { return 0; }
        @Override public OptionalLong blockStateHandle(int x, int y, int z) { return OptionalLong.empty(); }
        @Override public boolean isLoaded(int x, int y, int z) { return false; }
    };
}
