package dev.weft.neoforge.mixin.parallel;

import dev.weft.neoforge.regiontick.ParallelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RFC-0006 hazard 18 — the twin of hazard 3, and the bug that crashed
 * {@code p2parallelcap}.
 *
 * <p>{@code Level.getBlockEntity} carries its own off-thread guard:
 *
 * <pre>
 *   return !this.isClientSide &amp;&amp; Thread.currentThread() != this.thread
 *       ? null
 *       : this.getChunkAt(pos).getBlockEntity(pos, IMMEDIATE);
 * </pre>
 *
 * On a server, <em>any</em> call from a thread other than the server thread
 * answers {@code null} — not because the block entity is missing, but
 * because the caller is not the main thread. It is the same silent-wrongness
 * shape as hazard 3 ({@code getChunkNow} answering null off-main for loaded
 * chunks), and it is invisible to most block entities: a furnace ticker holds
 * its own reference and never asks the level, which is why the original
 * {@code p2parallel} rig ran green for two increments. Anything that looks a
 * <em>neighbour</em> up sees an empty world instead. Hoppers do that every
 * tick, and NeoForge routes them through
 * {@code VanillaInventoryCodeHooks} → {@code ChestBlock.getContainer}, which
 * turns the null into {@code new InvWrapper(null)} and then throws
 * {@code NullPointerException} on the first {@code getSlots()}.
 *
 * <p>Region and shard workers are legitimate owners of the chunks they tick,
 * so they take the real lookup. Safety rests on what the surrounding
 * increments already establish: the main thread is parked at the section
 * barrier while workers run, chunk resolution goes through the worker read
 * path ({@link ServerChunkCacheWorkerReadMixin}, which hands back the live
 * ticking chunk), and the per-chunk block-entity map is locked
 * ({@link LevelChunkBlockEntitySyncMixin}) because this bypass is precisely
 * what lets several workers reach those maps at once.
 *
 * <p>Non-worker off-thread callers keep vanilla's null: they have no
 * ownership claim, and quietly widening the guard for everyone would trade a
 * loud crash for silent corruption.
 */
@Mixin(Level.class)
public abstract class LevelWorkerBlockEntityMixin {

    @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
    private void weft$workerGetBlockEntity(BlockPos pos,
                                           CallbackInfoReturnable<BlockEntity> cir) {
        Level self = (Level) (Object) this;
        if (self.isClientSide() || !ParallelAccess.isRegionWorker()) {
            return;
        }
        if (self.isOutsideBuildHeight(pos)) {
            cir.setReturnValue(null);
            return;
        }
        LevelChunk chunk = self.getChunkAt(pos);
        cir.setReturnValue(chunk == null
                ? null
                : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE));
    }
}
