package dev.weft.engine.mail;

/**
 * Typed messages routed between owners at phase boundaries. Sealed so the
 * engine can exhaustively switch; loaders extend via {@link Custom}.
 */
public sealed interface Message {

    /** An entity (opaque handle) crossing a region border — ownership transfer. */
    record EntityHandoff(long entityHandle, long fromChunk, long toChunk) implements Message {}

    /** A block write requested by a non-owner (guard degrade path, RFC §4.4). */
    record BlockWrite(int x, int y, int z, long blockStateHandle) implements Message {}

    /** A region observed a block change relevant to a registered graph. */
    record GraphTopologyDelta(String graphId, long chunkKey, int x, int y, int z) implements Message {}

    /** Arbitrary owner-thread work (the runOnOwner path). */
    record Task(Runnable action) implements Message {}

    /** Loader-defined payloads. */
    record Custom(Object payload) implements Message {}
}
