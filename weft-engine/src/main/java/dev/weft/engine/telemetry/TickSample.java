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
 * @param aiNanos    the slice of {@code nanos} spent inside the mob's AI step
 *                   (loader-side: {@code Mob.serverAiStep} — sensing, goal and
 *                   target selectors, navigation, brain/custom step, move/look/
 *                   jump controls; exactly the universe WS-1 gating can widen
 *                   into). 0 for non-mob work, and 0 for mobs whose class
 *                   overrides the AI step without calling super (their cost
 *                   conservatively counts as movement/physics). A passenger
 *                   mob's AI slice accumulates into its vehicle's sample, the
 *                   same way its total cost already does.
 */
public record TickSample(Source source, String typeId, long chunkKey, long nanos, int aiInterval,
                         long aiNanos) {

    public static final long NO_CHUNK = Long.MIN_VALUE;

    /** Work with no WS-1 throttle projection (interval 1 = full rate). */
    public TickSample(Source source, String typeId, long chunkKey, long nanos) {
        this(source, typeId, chunkKey, nanos, 1, 0L);
    }

    /** Work with no measured AI slice (pre-sub-attribution callers, non-mobs). */
    public TickSample(Source source, String typeId, long chunkKey, long nanos, int aiInterval) {
        this(source, typeId, chunkKey, nanos, aiInterval, 0L);
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
