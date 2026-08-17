package dev.weft.neoforge.mixin;

import dev.weft.neoforge.profiler.WeftProfiler;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P0 profiler sub-attribution (RFC-0002 WS-1 sizing): time the AI step
 * inside every mob tick. {@code serverAiStep} covers sensing, the goal and
 * target selectors, navigation, the brain/custom step, and the move/look/
 * jump controls — exactly the universe WS-1's activation gating could ever
 * widen into — so the report can split the entity phase into "AI (WS-1
 * addressable)" vs "movement/physics (not addressable by throttling)".
 *
 * <p>The slice accumulates into the innermost open entity frame (the
 * {@code tickNonPassenger} HEAD/RETURN pair in {@link ServerLevelMixin}).
 * Mob classes that override {@code serverAiStep} without calling super
 * (dragon-style bosses) simply record no slice; their cost counts as
 * movement, which errs conservative for the sizing question.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void weft$aiSliceStart(CallbackInfo ci) {
        WeftProfiler.get().aiSliceStart();
    }

    @Inject(method = "serverAiStep", at = @At("RETURN"))
    private void weft$aiSliceEnd(CallbackInfo ci) {
        WeftProfiler.get().aiSliceEnd();
    }
}
