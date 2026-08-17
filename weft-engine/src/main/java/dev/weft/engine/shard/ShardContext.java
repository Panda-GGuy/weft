package dev.weft.engine.shard;

import dev.weft.api.entity.EntityEffectLog;
import dev.weft.engine.region.ShardKey;

import java.util.SplittableRandom;

/**
 * What one shard worker owns for the duration of one REGION-phase pass
 * (RFC-0004 §2): its identity, its pre-split child RNG stream, and its
 * effect recorder. Handed to every {@code Region.Tickable} ticked on the
 * sharded path; the serial path never constructs one.
 */
public final class ShardContext {

    private final long shardKey;
    private final SplittableRandom random;
    private final EntityEffects.ShardLog effects;

    public ShardContext(long shardKey, SplittableRandom random, EntityEffects.ShardLog effects) {
        this.shardKey = shardKey;
        this.random = random;
        this.effects = effects;
    }

    public long shardKey() {
        return shardKey;
    }

    public int shardIndex() {
        return ShardKey.shardIndex(shardKey);
    }

    /**
     * This shard's exclusive RNG substream for the tick — pre-split from the
     * region's deterministic stream by the coordinator, in shard-index order
     * (RFC-0004 §2.4).
     */
    public SplittableRandom random() {
        return random;
    }

    /** Effect log view for the entity identified by {@code sourceHandle}. */
    public EntityEffectLog effects(long sourceHandle) {
        return effects.forSource(sourceHandle);
    }
}
