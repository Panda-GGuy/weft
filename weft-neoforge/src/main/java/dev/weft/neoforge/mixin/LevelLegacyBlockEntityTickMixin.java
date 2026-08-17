package dev.weft.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.weft.neoforge.legacy.LegacyLaneBlockEntityMarker;
import dev.weft.neoforge.legacy.LegacyRouting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * P2 legacy-lane seam, block-entity half (RFC-0001 §7.2): the single
 * {@code TickingBlockEntity.tick()} call site inside
 * {@code Level.tickBlockEntities} routes each ticker through
 * {@link LegacyRouting} — Tier-2 tickers are extracted to the LEGACY phase,
 * everything else runs inline exactly as vanilla wrote it. The deferred call
 * is the ticker wrapper's own {@code tick()}, so vanilla's liveness re-checks
 * (removed BEs become null-tickers; chunk tickability is re-tested) still
 * guard the deferred execution.
 *
 * <p>Sits inside the section that {@link LevelRegionTickMixin} wraps for tick
 * ownership; the two compose (MixinExtras). Client levels pass straight
 * through. Fail-loud (RFC-0003 R2), same reasoning as the other
 * tick-ownership mixins: a silently-missing extraction seam would be
 * dangerous the day regions parallelize.
 */
@Mixin(Level.class)
public abstract class LevelLegacyBlockEntityTickMixin implements LegacyLaneBlockEntityMarker {

    @WrapOperation(
            method = "tickBlockEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void weft$extractLegacyBlockEntityTick(TickingBlockEntity ticker,
                                                   Operation<Void> original) {
        if ((Object) this instanceof ServerLevel serverLevel) {
            LegacyRouting.tickBlockEntityOrDefer(serverLevel, ticker,
                    () -> original.call(ticker));
        } else {
            original.call(ticker);
        }
    }
}
