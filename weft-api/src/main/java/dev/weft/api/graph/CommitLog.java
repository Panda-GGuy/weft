package dev.weft.api.graph;

/**
 * Ordered log of world writes a graph emits during compute (RFC-0001 §5.2).
 * Writes are applied in the commit phase by the region that owns each target
 * position. Inventory writes default to conditional (compare-against-snapshot)
 * semantics; a rejected conditional write is reported back to the graph on its
 * next tick rather than applied blind — this is the anti-item-dupe mechanism.
 */
public interface CommitLog {

    /** Unconditionally set a block state (by registry-stable handle). */
    void setBlock(int x, int y, int z, long blockStateHandle);

    /**
     * Insert {@code count} of the item identified by {@code itemHandle} into
     * the inventory at the given position, conditional on the target slot
     * contents still matching what the snapshot showed.
     *
     * @return an opaque write id, echoed back in next tick's rejection set if
     *         the condition failed.
     */
    long insertItemConditional(int x, int y, int z, int slot, long itemHandle, int count);

    /** Schedule a follow-up graph event visible to this graph next tick. */
    void deferToNextTick(Runnable graphLocalAction);
}
