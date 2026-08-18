package dev.weft.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.weft.neoforge.legacy.LegacyLaneBlockEntityMarker;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The per-block-entity-unit seam: the single {@code TickingBlockEntity.tick()}
 * call site inside {@code Level.tickBlockEntities}. Two P2 increments compose
 * here, outermost first:
 *
 * <ol>
 *   <li><b>Partitioned ticking</b> (increment 4, {@link RegionizedTicking}):
 *       in partitioned mode the unit is captured into its region's bucket and
 *       runs at section end in canonical region order. The captured runnable
 *       goes through the lane check and calls the ticker's own {@code tick()}
 *       — never the stored Operation, which must not outlive this handler
 *       frame (MixinExtras contract).</li>
 *   <li><b>Legacy lane</b> (increment 3, {@link LegacyRouting}): Tier-2
 *       tickers are extracted to the LEGACY phase; everything else runs
 *       inline. Vanilla liveness re-checks (removed BEs become null-tickers,
 *       chunk tickability is re-tested) guard every deferred execution.</li>
 * </ol>
 *
 * <p>Client levels pass straight through. Fail-loud (RFC-0003 R2), same
 * reasoning as the other tick-ownership mixins: a silently-missing seam would
 * be dangerous the day regions parallelize.
 */
@Mixin(Level.class)
public abstract class LevelBlockEntityUnitMixin implements LegacyLaneBlockEntityMarker {

    @WrapOperation(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void weft$blockEntityUnit(TickingBlockEntity ticker, Operation<Void> original) {
        if ((Object) this instanceof ServerLevel serverLevel) {
            if (!RegionizedTicking.captureBlockEntityUnit(serverLevel, ticker,
                    () -> LegacyRouting.tickBlockEntityOrDefer(serverLevel, ticker, ticker::tick))) {
                LegacyRouting.tickBlockEntityOrDefer(serverLevel, ticker,
                        () -> original.call(ticker));
            }
        } else {
            original.call(ticker);
        }
    }
}
