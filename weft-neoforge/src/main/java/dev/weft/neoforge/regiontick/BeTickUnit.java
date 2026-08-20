package dev.weft.neoforge.regiontick;

import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * One captured block-entity tick, tagged with the chunk that owns it so the
 * sharded path can colour it (RFC-0008 §3).
 *
 * @param ticker    vanilla ticker retained across fused ticks
 * @param chunkKey  packed {@link dev.weft.engine.region.ChunkKey} of the
 *                  ticker's position — the shard domain the unit may touch
 * @param wideReach true for types whose per-tick reach can leave the
 *                  colouring's separation gap ({@link WideReachBlockEntities});
 *                  these never shard, they run on the serial tail
 * @param unit      the ticker's own {@code tick()} call, already composed
 *                  with the increment-3 legacy-lane check
 */
record BeTickUnit(TickingBlockEntity ticker, long chunkKey, boolean wideReach, Runnable unit) {
    boolean isRemoved() {
        return ticker.isRemoved();
    }
}
