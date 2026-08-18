package dev.weft.neoforge.mixin.parallel;

import dev.weft.neoforge.regiontick.ParallelAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RFC-0006 hazards 1–4: vanilla's {@code getChunk} routes off-main-thread
 * callers through the main-thread processor — a guaranteed deadlock while
 * the main thread waits at a Weft section barrier — and {@code getChunkNow}
 * answers {@code null} off-main even for loaded chunks. Region workers take
 * a lock-free read instead: resolve the holder via the visible-chunk map (a
 * snapshot the parked main thread is not mutating) and take the completed
 * FULL chunk from it, bypassing the racy 4-slot lastChunk cache in both
 * directions.
 *
 * <p>A worker demanding a chunk that is not loaded is a real bug — ticket
 * rings make in-region access always loaded — so it fails loud with
 * position forensics rather than sync-loading (hazard 4).
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheWorkerReadMixin {

    @Shadow
    @Final
    Thread mainThread;

    @Shadow
    private ChunkHolder getVisibleChunkIfPresent(long pos) {
        throw new AssertionError("shadow");
    }

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void weft$workerGetChunk(int x, int z, ChunkStatus status, boolean load,
                                     CallbackInfoReturnable<ChunkAccess> cir) {
        if (Thread.currentThread() == this.mainThread || !ParallelAccess.isRegionWorker()) {
            return;
        }
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        ChunkAccess chunk = holder != null ? weft$resolve(holder, status) : null;
        if (chunk != null) {
            cir.setReturnValue(chunk);
        } else if (!load) {
            cir.setReturnValue(null);
        } else {
            throw new IllegalStateException("Weft region worker requires chunk [" + x + ", " + z
                    + "] at status " + status + " but it is not loaded/complete "
                    + "(RFC-0006 hazard 4: in-region access must be within ticket rings)");
        }
    }

    @Inject(method = "getChunkNow", at = @At("HEAD"), cancellable = true)
    private void weft$workerGetChunkNow(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        if (Thread.currentThread() == this.mainThread || !ParallelAccess.isRegionWorker()) {
            return;
        }
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        ChunkAccess chunk = holder != null ? weft$resolve(holder, ChunkStatus.FULL) : null;
        cir.setReturnValue(chunk instanceof LevelChunk levelChunk ? levelChunk : null);
    }

    /**
     * Resolve a holder to the chunk a worker may read.
     *
     * <p>At {@code FULL} this deliberately asks for the <b>ticking</b> chunk
     * rather than {@code getChunkIfPresent(FULL)}. The two are not
     * interchangeable: {@code getChunkIfPresent} reads
     * {@code GenerationChunkHolder}'s per-status future — the chunk as the
     * <em>generation pipeline</em> produced it — while {@code getTickingChunk}
     * returns the live {@link LevelChunk} the server is actually simulating.
     * <p><b>This was the p2parallelcap crash.</b> Workers saw {@code null}
     * from {@code level.getBlockEntity} for chests that demonstrably existed;
     * instrumentation showed {@code LevelChunk.getBlockEntity} never returned
     * null while holding the entry, which pinned the fault on
     * <em>which chunk object</em> the worker was handed. At {@code FULL} the
     * generation future can hold a different {@link LevelChunk} instance than
     * the one the server is ticking — same coordinates, different object,
     * and its block-entity map does not have the live entries. Block entities
     * that hold their own reference (a furnace ticker never asks the level)
     * cannot notice; anything that looks a neighbour up sees an empty world.
     * Hence a furnace-and-armour-stand rig stayed green for two increments
     * while hoppers crashed immediately.
     *
     * <p>So at {@code FULL} we only ever accept a <em>live</em> chunk — the
     * ticking chunk, else the full-chunk future — and never the generation
     * view. A null here falls through to the caller's fail-loud path, which
     * is correct: a worker ticking a chunk's contents requires that chunk to
     * be live.
     */
    @Unique
    private ChunkAccess weft$resolve(ChunkHolder holder, ChunkStatus status) {
        if (status != ChunkStatus.FULL) {
            return holder.getChunkIfPresent(status);
        }
        LevelChunk ticking = holder.getTickingChunk();
        if (ticking != null) {
            return ticking;
        }
        ChunkResult<LevelChunk> full = holder.getFullChunkFuture().getNow(null);
        return full != null ? full.orElse(null) : null;
    }
}
