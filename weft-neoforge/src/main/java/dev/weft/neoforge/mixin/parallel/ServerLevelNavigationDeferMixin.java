package dev.weft.neoforge.mixin.parallel;

import dev.weft.neoforge.regiontick.ParallelAccess;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RFC-0006 <b>hazard 21</b>: {@code ServerLevel.sendBlockUpdated} touches
 * <em>level-wide</em> navigation state, so any block change made from a region
 * worker races every other region.
 *
 * <p>Found the way hazards should be found — a live server, not a rig. A
 * four-island forceloaded world with 560 mobs and {@code parallelRegions} on
 * crashed inside 60 seconds:
 *
 * <pre>
 *   NullPointerException: Cannot invoke "ObjectArrayList.get(int)"
 *       because "this.wrapped" is null
 *     at ObjectOpenHashSet$SetIterator.next
 *     at ServerLevel.sendBlockUpdated          (ServerLevel:1078)
 *     at Level.markAndNotifyBlock → Level.setBlock
 *     at DoorBlock.setOpen
 *     at InteractWithDoor  → Brain.tick → Villager.customServerAiStep
 *     at RegionizedTicking.lambda$runBuckets$9 → ForkJoinWorkerThread.run
 * </pre>
 *
 * <p>A villager opened a door on a worker thread. That is only the trigger;
 * the hazard is structural, and there are two distinct races in it:
 *
 * <ol>
 *   <li><b>Iterate versus mutate.</b> {@code navigatingMobs} (ServerLevel:193)
 *       is a plain {@code ObjectOpenHashSet<Mob>} holding <em>every</em> mob in
 *       the level, i.e. across every region. {@code sendBlockUpdated} iterates
 *       it (ServerLevel:1078) while {@code onTrackingStart}/{@code
 *       onTrackingEnd} (ServerLevel:1757/1784) add and remove from it — and
 *       those fire on entity spawn, death and chunk load, which is to say from
 *       other regions' buckets, concurrently. fastutil's iterator answers that
 *       with the NPE above. Vanilla's own guard here is {@code
 *       isUpdatingNavigations}, a recursion check that assumes one thread.</li>
 *   <li><b>Cross-region writes.</b> Worse, and invisible in the stack trace:
 *       the loop calls {@code recomputePath()} on mobs selected from the whole
 *       level. A worker ticking region A thus mutates the {@code PathNavigation}
 *       of a mob in region B <em>while region B's bucket is ticking it</em>.
 *       That is a cross-region write, which RFC-0006 §2's safety argument
 *       forbids outright — a lock around the iteration would have silenced the
 *       crash while leaving this in place.</li>
 * </ol>
 *
 * <p>So the fix is deferral, not locking: on a region worker the whole call
 * goes to the section-end queue and runs on the server thread after the
 * barrier, same tick — hazard 14's idiom
 * ({@link EntityChangeDimensionDeferMixin}), for the same reason. There, all
 * buckets have joined, {@code navigatingMobs} has no concurrent mutator, and
 * touching any region's mobs is legal again.
 *
 * <p><b>The deferral costs nothing observable</b>, which is worth stating
 * precisely rather than hoping. {@code sendBlockUpdated}'s client-visible half
 * is {@code chunkSource.blockChanged}, and those changes are broadcast by
 * {@code ServerChunkCache.tick} — called at ServerLevel:379, <em>before</em>
 * the entity section at ServerLevel:420. Block changes made during entity
 * ticking therefore already broadcast on the following tick in unmodified
 * vanilla, so moving the call to the end of the section does not delay a single
 * packet. Deferring the whole method rather than surgically deferring the
 * navigation block also means no vanilla logic is duplicated here, so a
 * Minecraft update cannot leave a stale copy behind.
 *
 * <p>Not fixed here, and deliberately: {@code ServerLevel.pathTypesByPosCache}
 * is a direct-mapped array cache with no synchronization, so concurrent
 * {@code getOrCompute}/{@code invalidate} can read a position and a path type
 * from different writes. It cannot crash (the arrays are fixed-size) and it
 * predates region parallelism — WS-2's async pathfinding already reaches it
 * off-thread — so it belongs to its own finding rather than to this fix.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelNavigationDeferMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void weft$deferFromWorker(BlockPos pos, BlockState oldState, BlockState newState,
                                      int flags, CallbackInfo ci) {
        if (ParallelAccess.isRegionWorker()) {
            ServerLevel self = (ServerLevel) (Object) this;
            RegionizedTicking.deferToSectionEnd(
                    () -> self.sendBlockUpdated(pos, oldState, newState, flags));
            ci.cancel();
        }
    }
}
