package dev.weft.neoforge.mixin;

import dev.weft.neoforge.profiler.WeftProfiler;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * P0/P1 bridge: time vanilla's spawn-density scan ({@code createState})
 * into the profiler as GLOBAL cost. This is precisely the work the async
 * spawn-density service (shadow mode today) would take off the tick, so
 * the report shows the prize with real pack data before we flip anything.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Inject(method = "createState", at = @At("HEAD"))
    private static void weft$spawnStateStart(CallbackInfoReturnable<?> cir) {
        WeftProfiler.get().push();
    }

    @Inject(method = "createState", at = @At("RETURN"))
    private static void weft$spawnStateEnd(CallbackInfoReturnable<?> cir) {
        WeftProfiler.get().popGlobal("minecraft:natural_spawner/create_state");
    }
}
