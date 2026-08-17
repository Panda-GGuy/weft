package dev.weft.neoforge.mixin.optimization;

import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.activation.RepathThrottleMarker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WS-1 widening step 1 (RFC-0002): a mob whose AI is throttled also
 * recomputes its path at reduced frequency. Vanilla already rate-limits
 * {@code recomputePath} to once per 20 ticks and marks callers inside the
 * window as delayed; this hook stretches that window to {@code 20 * interval}
 * for throttled mobs by mirroring the exact under-window branch (mark
 * delayed, keep following the current path) and cancelling. The recompute
 * that eventually runs is byte-for-byte vanilla — only its cadence changes,
 * and only outside the full-rate ring (exemptions inviolate: interval is 1
 * near players, mid-fight, and for exempt types, and 1 never defers).
 *
 * <p>Synergy: every deferred recompute is a {@code createPath} — and with
 * WS-2 active, a path-service request — that never happens.
 *
 * <p>Fail-soft per RFC-0003 R2: {@code required: false} optimizations config,
 * {@code defaultRequire: 0}; an unapplied hook leaves vanilla repath cadence.
 * Same module switch as the AI throttle ({@code activationScheduling}) — this
 * is WS-1 behavior, not a new module.
 */
@Mixin(PathNavigation.class)
public abstract class PathRecomputeThrottleMixin implements RepathThrottleMarker {

    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    @Final
    protected Level level;

    @Shadow
    protected boolean hasDelayedRecomputation;

    @Shadow
    protected long timeLastRecompute;

    @Inject(method = "recomputePath", at = @At("HEAD"), cancellable = true)
    private void weft$deferThrottledRepath(CallbackInfo ci) {
        if (ActivationHooks.shouldDeferRepath(
                this.mob, this.level.getGameTime(), this.timeLastRecompute)) {
            this.hasDelayedRecomputation = true;
            ci.cancel();
        }
    }
}
