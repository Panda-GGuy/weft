package dev.weft.api.entity;

/**
 * The entity-layer commit log (RFC-0004 §2.3): everything an entity tick
 * does to *another* entity, or to the region's shared entity list, is
 * recorded here instead of written directly — the mirror of
 * {@link dev.weft.api.graph.CommitLog} one grain down. An entity tick may
 * mutate only its own component state directly (RFC-0004 §2.2).
 *
 * <p>Effects accumulate per shard during the parallel pass and are applied
 * after all shards in the region finish, in one deterministic order — sorted
 * by (source entity, emission sequence), never thread-finish order — so the
 * outcome is reproducible and independent of shard count.
 */
public interface EntityEffectLog {

    /** Deal damage to another entity, resolved at effect-apply time. */
    void damage(long targetHandle, float amount, long sourceHandle);

    /**
     * Claim a dropped item stack, conditional on nobody earlier in the
     * deterministic order having claimed it this tick (the anti-dupe
     * mechanism, same trick as {@code CommitLog.insertItemConditional}).
     *
     * @return an opaque write id (unique per source entity per tick), echoed
     *         back through the rejection callback if the claim loses.
     */
    long claimItem(long itemHandle, long claimantHandle);

    /** Enter or leave love mode (breeding partner claims race otherwise). */
    void setLoveMode(long targetHandle, boolean active);

    /** Remove an entity from the world; the live list is mutated only between phases. */
    void removeEntity(long handle);

    /** Spawn an entity; the live list is mutated only between phases. */
    void spawnEntity(EntitySpec spec);
}
