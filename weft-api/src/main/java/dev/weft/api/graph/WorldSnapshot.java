package dev.weft.api.graph;

import java.util.OptionalLong;

/**
 * Read-only view of the settled pre-tick world, restricted to the positions a
 * graph declared interest in. Loader-agnostic: block states and inventory
 * views are exposed through opaque handles here; the NeoForge adapter binds
 * them to real {@code BlockState} / {@code IItemHandler} views.
 */
public interface WorldSnapshot {

    /** Pipeline tick this snapshot was taken at. */
    long tick();

    /**
     * Opaque handle for the block state at a declared position, or empty if
     * the position is outside this graph's declared interest set or unloaded.
     * The handle is stable for the lifetime of the snapshot only.
     */
    OptionalLong blockStateHandle(int x, int y, int z);

    /** True if the chunk containing the position was loaded at snapshot time. */
    boolean isLoaded(int x, int y, int z);
}
