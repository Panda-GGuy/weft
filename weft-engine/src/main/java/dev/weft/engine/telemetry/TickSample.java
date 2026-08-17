package dev.weft.engine.telemetry;

/**
 * One timed unit of simulation work observed during a vanilla tick (P0,
 * RFC-0001 §8.4/§9.1). Loader hooks record these; the analyzer turns a
 * tick's worth into a regionizability report.
 *
 * @param source   what kind of work this was
 * @param typeId   registry id of the entity/block-entity type (cost attribution)
 * @param chunkKey packed chunk position ({@code ChunkKey.pack}) — spatial
 *                 attribution; pass {@link #NO_CHUNK} for non-spatial work
 * @param nanos    measured duration
 */
public record TickSample(Source source, String typeId, long chunkKey, long nanos) {

    public static final long NO_CHUNK = Long.MIN_VALUE;

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
