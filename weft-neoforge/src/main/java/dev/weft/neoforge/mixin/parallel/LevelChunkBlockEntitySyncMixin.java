package dev.weft.neoforge.mixin.parallel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/**
 * RFC-0006 audit gap, found by the RFC-0008 work and confirmed against
 * increment 5: <b>{@code LevelChunk.getBlockEntity} is a mutating operation
 * on a non-thread-safe map, and ordinary block-entity ticks call it.</b>
 *
 * <p>{@code ChunkAccess} declares {@code blockEntities} as a fastutil
 * {@code Object2ObjectOpenHashMap} and {@code pendingBlockEntities} as a
 * {@code HashMap}. Despite reading like an accessor,
 * {@code getBlockEntity(pos, IMMEDIATE)} drops entries whose block entity is
 * removed, drains {@code pendingBlockEntities}, and — the dangerous one —
 * <em>creates and registers</em> a block entity when the map misses. A
 * concurrent {@code get} against another thread's {@code put} on an
 * open-addressing table can answer {@code null} for a key that is present.
 *
 * <p>RFC-0006 audited {@code Level.blockEntityTickers} and synchronized
 * {@code addBlockEntityTicker}, but never the per-chunk block-entity maps
 * underneath them, so this closes a genuine hole in that audit.
 *
 * <p><b>Honest scope: this does NOT fix the open p2parallelcap crash.</b> It
 * was written while chasing that crash (a {@code NullPointerException} in
 * NeoForge's {@code VanillaInventoryCodeHooks.extractHook}, where
 * {@code ChestBlock.getContainer} is handed {@code null} by
 * {@code level.getBlockEntity} for a chest that demonstrably exists) and the
 * crash reproduces unchanged with this lock in place — so map corruption is
 * ruled out as that crash's cause. The lock is kept anyway on its own
 * merits: a mutating call on a fastutil open-addressing map, reachable from
 * concurrent tick workers, is unsafe whether or not it is the bug currently
 * being hunted.
 *
 * <p>Treatment matches the increment-5 idiom: a per-chunk lock over the
 * mutators <em>and</em> the reader, because the reader mutates. The lock is
 * per {@link LevelChunk} instance, so chunks stay independent and the
 * uncontended cost is a thin-lock acquire on a path that already does a hash
 * lookup. Active regardless of any flag — single-threaded semantics are
 * identical.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkBlockEntitySyncMixin {

    @Unique
    private final Object weft$blockEntityLock = new Object();

    @WrapMethod(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;")
    private BlockEntity weft$syncGetBlockEntity(BlockPos pos, LevelChunk.EntityCreationType type,
                                                Operation<BlockEntity> original) {
        synchronized (weft$blockEntityLock) {
            return original.call(pos, type);
        }
    }

    @WrapMethod(method = "setBlockEntity")
    private void weft$syncSetBlockEntity(BlockEntity blockEntity, Operation<Void> original) {
        synchronized (weft$blockEntityLock) {
            original.call(blockEntity);
        }
    }

    @WrapMethod(method = "removeBlockEntity")
    private void weft$syncRemoveBlockEntity(BlockPos pos, Operation<Void> original) {
        synchronized (weft$blockEntityLock) {
            original.call(pos);
        }
    }
}
