package dev.weft.neoforge.mixin.parallel;

import dev.weft.neoforge.regiontick.ParallelAccess;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RFC-0006 hazard 14: {@code Entity.baseTick → handlePortal} executes
 * {@code changeDimension} immediately mid-tick — from a region worker that
 * would mutate another dimension's structures concurrently. Worker-context
 * calls defer to the section-end queue (server thread, same tick, after the
 * barrier) and answer {@code null} — vanilla's established "no travel right
 * now" result, which every call site already handles; the deferred call
 * re-runs vanilla's own guards (removal, cooldown) before traveling.
 */
@Mixin(Entity.class)
public abstract class EntityChangeDimensionDeferMixin {

    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void weft$deferFromWorker(DimensionTransition transition,
                                      CallbackInfoReturnable<Entity> cir) {
        if (ParallelAccess.isRegionWorker()) {
            Entity self = (Entity) (Object) this;
            RegionizedTicking.deferToSectionEnd(() -> self.changeDimension(transition));
            cir.setReturnValue(null);
        }
    }
}
