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
        WeftProfiler.get().popEntity(
                key != null ? key.toString() : entity.getType().getClass().getName(),
                entity.chunkPosition().toLong());
    }
}
