package dev.weft.neoforge.mixin;

import dev.weft.neoforge.profiler.WeftProfiler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P0 profiler hook: time every entity tick, attributed to the entity type
 * and its chunk (RFC-0001 §9.1). HEAD/RETURN pair around
 * {@code tickNonPassenger}; nesting (passenger chains re-entering) is
 * handled by the profiler's stack.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void weft$entityTickStart(Entity entity, CallbackInfo ci) {
        WeftProfiler.get().push();
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void weft$entityTickEnd(Entity entity, CallbackInfo ci) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        // WS-1 projection (RFC-0002): what interval would the configured
        // activation tiers give this entity where it stands? Feeds the
        // "projected WS-1 savings" report line; computed only when profiling
        // is on (this hook is behind PROFILING_ENABLED inside the profiler,
        // and the interval lookup is the expensive part we gate here).
        int aiInterval = 1;
        if (dev.weft.neoforge.WeftConfig.PROFILING_ENABLED
                && entity instanceof net.minecraft.world.entity.Mob mob) {
            aiInterval = dev.weft.neoforge.activation.ActivationHooks.projectedInterval(mob);
        }
        WeftProfiler.get().popEntity(
                key != null ? key.toString() : entity.getType().getClass().getName(),
                entity.chunkPosition().toLong(), aiInterval);
    }
}
