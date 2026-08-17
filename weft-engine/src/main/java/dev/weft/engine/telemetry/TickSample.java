package dev.weft.engine.telemetry;

/**
 * One timed unit of simulation work observed during a vanilla tick (P0,
 * RFC-0001 §8.4/§9.1). Loader hooks record these; the analyzer turns a
 * tick's worth into a regionizability report.
 *
 * @param source     what kind of work this was
 * @param typeId     registry id of the entity/block-entity type (cost attribution)
 * @param chunkKey   packed chunk position ({@code ChunkKey.pack}) — spatial
 *                   attribution; pass {@link #NO_CHUNK} for non-spatial work
 * @param nanos      measured duration
 * @param aiInterval WS-1 projection (RFC-0002): the AI tick interval the
 *                   configured activation tiers would assign this entity at
 *                   its measured distance — 1 for full rate, not throttleable,
 *                   or non-entity work. Recorded regardless of whether the
 *                   activation module is active, so the report can project
 *                   savings before anyone flips the switch.
 */
public record TickSample(Source source, String typeId, long chunkKey, long nanos, int aiInterval) {

    public static final long NO_CHUNK = Long.MIN_VALUE;

    /** Work with no WS-1 throttle projection (interval 1 = full rate). */
    public TickSample(Source source, String typeId, long chunkKey, long nanos) {
        this(source, typeId, chunkKey, nanos, 1);
    }

    public enum Source {
        ENTITY,
        BLOCK_ENTITY,
        /** Work with no spatial home: global lists, time, weather, commands. */
        GLOBAL
    }

    public boolean spatial() {
        return chunkKey != NO_CHUNK;
    }
}
