package dev.weft.neoforge.mixin.optimization;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.activation.ActivationMarker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WS-1 (RFC-0002): throttle the expensive AI calls inside
 * {@code Mob.serverAiStep} — sensing plus the goal/target selectors — for
 * mobs far from every player. Everything else in the method (noActionTime,
 * navigation, {@code customServerAiStep} i.e. brains and modded overrides,
 * move/look/jump controls) runs every tick, so in-flight movement never
 * freezes and despawn accounting is untouched. The navigation step's
 * periodic path <em>recompute</em> is the one exception: its cadence
 * stretches with the same interval via {@link PathRecomputeThrottleMixin}.
 *
 * <p>All gated calls share one decision per {@code serverAiStep} invocation,
 * so a skipped tick skips them coherently (goals never observe a sensing
 * cache from a tick they themselves skipped) and an active tick runs them
 * all. Fail-soft per RFC-0003 R2: this lives in the {@code required: false}
 * optimizations config with {@code defaultRequire: 0}; {@link ActivationMarker}
 * is the runtime applied-check, and the skip field defaults to false, so any
 * partially-failed application degrades toward vanilla (AI runs), never away
 * from it.
 */
@Mixin(Mob.class)
public abstract class MobAiThrottleMixin implements ActivationMarker {

    @Unique
    private boolean weft$skipAiThisTick;

    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void weft$decideAiTick(CallbackInfo ci) {
        weft$skipAiThisTick = !ActivationHooks.shouldTickAi((Mob) (Object) this);
    }

    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/sensing/Sensing;tick()V"))
    private boolean weft$gateSensing(Sensing sensing) {
        return !weft$skipAiThisTick;
    }

    // Matches both the targetSelector and goalSelector call sites.
    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private boolean weft$gateSelectorTick(GoalSelector selector) {
        return !weft$skipAiThisTick;
    }

    // The every-other-tick partial pass (tickRunningGoals) on both selectors.
    @WrapWithCondition(method = "serverAiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
    private boolean weft$gateSelectorRunningGoals(GoalSelector selector, boolean tickAll) {
        return !weft$skipAiThisTick;
    }
}
