package dev.weft.api.graph;

import java.util.Set;

/**
 * What an adapter registers with the engine to give a cross-chunk mod system
 * (energy net, item network, rotational network) a first-class parallel home.
 */
public interface GraphDefinition {

    /** Stable identity — also the deterministic commit priority tiebreaker. */
    String graphId();

    /**
     * Chunk positions (packed as {@code ChunkPos.asLong}-style longs) this
     * graph currently spans. Used to build its snapshot interest set. Updated
     * via topology-delta mail when regions observe relevant block changes.
     */
    Set<Long> interestChunks();

    GraphTicker ticker();
}
