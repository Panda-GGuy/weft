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
 * <p>A worker demanding a chunk that is genuinely absent is a real bug, so it
 * fails loud with position forensics rather than sync-loading (hazard 4).
 *
 * <p><b>Hazard 22 corrected the reason that guard used to fire.</b> Hazard 4's
 * original justification — "ticket rings make in-region access always loaded" —
 * was the right instinct resting on the wrong invariant, and the first live
 * soak crashed on it three times in five minutes. See {@link #weft$resolve} for
 * the mechanism and the fix.
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
        if (chunk == null && holder != null && load) {
            // Hazard 22, and ONLY on this path: vanilla would sync-load here, so
            // answering null is not available to us and the alternative to the
            // border view is the crash this replaced.
            chunk = weft$borderView(holder, status);
        }
        if (chunk != null) {
            cir.setReturnValue(chunk);
        } else if (!load) {
            cir.setReturnValue(null);
        } else {
            throw new IllegalStateException("Weft region worker requires chunk [" + x + ", " + z
                    + "] at status " + status + " but it is not loaded/complete, and no "
                    + "generated FULL view exists either (RFC-0006 hazard 4; hazard 22 "
                    + "explains why the border ring is not this case)");
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
     * <p>So at {@code FULL} we accept the ticking chunk, else the promoted
     * full chunk. What that ordering must <b>not</b> do — and did, until
     * hazard 22 — is stop there.
     *
     * <h2>Hazard 22: the border ring, and why refusing it crashed servers</h2>
     *
     * <p>The fix above generalised from "the chunk a worker <em>ticks</em> must
     * be live" to "every chunk a worker <em>touches</em> must be live". Those
     * are different requirements, and vanilla is explicit about the difference.
     * {@code ChunkMap.prepareEntityTickingChunk} (ChunkMap:364) is
     * {@code getChunkRangeFuture(holder, radius 2, ChunkStatus.FULL)}: before a
     * chunk may tick entities, <b>every chunk within radius 2 is generated to
     * {@code ChunkStatus.FULL}</b>. That is the border an entity tick reads
     * into, and vanilla guarantees it.
     *
     * <p>The trap is that {@code ChunkStatus.FULL} (a <em>generation</em>
     * status) and {@code FullChunkStatus.FULL} (a <em>promotion</em> status) are
     * different things reached by different futures. Vanilla's radius-2
     * guarantee is about generation. This method was asking about promotion —
     * and promotion completes through {@code prepareAccessibleChunk}, whose
     * continuation is scheduled onto {@code ChunkMap.mainThreadMailbox}
     * (ChunkMap:704). During a fanned-out section <b>the main thread is parked
     * at our barrier and cannot drain that mailbox</b>, so a border chunk's
     * {@code fullChunkFuture} stays incomplete for exactly as long as the
     * parallel section runs. Every read into it answered null, and hazard 4's
     * guard turned that into a crash.
     *
     * <p>So hazard 22 is hazard 1 wearing a different hat: a dependency on a
     * future only the parked main thread can complete. It is not a race, which
     * is why it reproduced deterministically — three consecutive boots, three
     * different chunks, all short-reach reads at a chunk boundary
     * ({@code Entity.updateFluidHeightAndDoFluidPushing} → {@code getFluidState}
     * twice, and {@code Mob.serverAiStep} → {@code getBlockState}).
     *
     * <p><b>The fix</b> is to answer the question vanilla's invariant actually
     * guarantees: fall back to the generated-{@code FULL} view when no promoted
     * view exists, provided it really is a {@link LevelChunk}. That is the same
     * object and the same block states the main thread would hand a vanilla
     * caller reading the same border chunk — {@code replaceProtoChunk}
     * (GenerationChunkHolder:90) rewrites every status future <em>except</em>
     * the last, so the {@code FULL} future holds the real {@code LevelChunk}
     * rather than an {@link net.minecraft.world.level.chunk.ImposterProtoChunk}.
     *
     * <p>What the fallback deliberately does not do is restore the p2parallelcap
     * bug. That was a worker resolving <em>its own ticking chunk</em> to a view
     * whose block-entity map was not yet post-processed; this path is only
     * reached when no promoted view exists at all, i.e. for a border chunk the
     * bucket is reading and not ticking. The {@code p2parallelcap} gate is the
     * standing check on that distinction, and border reads are
     * {@linkplain ParallelAccess#recordBorderRead() counted} so the concession
     * stays visible in {@code /weft status} instead of becoming folklore.
     *
     * <p>Beyond radius 2 a null still falls through to the fail-loud path, and
     * still should: a read that far out is one vanilla would have had to
     * sync-load, which is the real bug hazard 4 was written to catch.
     *
     * <p>The fallback itself lives in {@link #weft$borderView}, reachable only
     * from {@code getChunk(..., load=true)} — see that method for why the
     * distinction matters.
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

    /**
     * Hazard 22's fallback: the radius-2 border ring vanilla guarantees is
     * generated to {@code ChunkStatus.FULL}, whose <em>promotion</em> the parked
     * main thread cannot complete.
     *
     * <p><b>Reachable only from {@code getChunk(..., load=true)}</b>, and that
     * scoping is load-bearing rather than tidiness. {@code getChunkNow} is
     * vanilla's "only if it is loaded" probe: callers read its null as a
     * loadedness test, and it never crashed, so it has nothing to be rescued
     * from. Wiring this into the shared resolve path made {@code getChunkNow}
     * answer with generated-but-unloaded chunks and turned that null into a
     * lie — visible immediately as 8.26 million border reads in thirty seconds
     * on a rig whose actual border ring is a few hundred chunks. The counter
     * caught it, which is the argument for having counted it.
     */
    @Unique
    private ChunkAccess weft$borderView(ChunkHolder holder, ChunkStatus status) {
        if (status != ChunkStatus.FULL) {
            return null;
        }
        if (holder.getChunkIfPresent(ChunkStatus.FULL) instanceof LevelChunk generated) {
            ParallelAccess.recordBorderRead();
            return generated;
        }
        return null;
    }
}
