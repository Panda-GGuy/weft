package dev.weft.neoforge.mixin.optimization;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.weft.neoforge.service.SpawnDensityHooks;
import dev.weft.neoforge.service.SpawnDensityMarker;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * P1 spawn-density graduation (RFC-0001 §11): replace vanilla's per-tick
 * synchronous {@code NaturalSpawner.createState} scan — O(all loaded
 * entities), every tick, on the server thread — with the spawn-density
 * service's off-thread result from the previous tick. The hook decides per
 * tick: fresh async result → build the SpawnState from it; anything else →
 * run the original scan unchanged (fail-soft, RFC-0003 R2). Verify ticks
 * run the original anyway and diff, so parity evidence keeps accumulating
 * in production.
 *
 * <p>Lives in the {@code required: false} optimizations config with
 * {@code defaultRequire: 0}; {@link SpawnDensityMarker} is the runtime
 * applied-check. A failed application leaves vanilla's scan untouched.
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheSpawnStateMixin implements SpawnDensityMarker {

    @WrapOperation(
            method = "tickChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/NaturalSpawner;createState("
                            + "ILjava/lang/Iterable;"
                            + "Lnet/minecraft/world/level/NaturalSpawner$ChunkGetter;"
                            + "Lnet/minecraft/world/level/LocalMobCapCalculator;)"
                            + "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;"))
    private NaturalSpawner.SpawnState weft$createState(int spawnableChunkCount,
                                                       Iterable<Entity> entities,
                                                       NaturalSpawner.ChunkGetter chunkGetter,
                                                       LocalMobCapCalculator localMobCaps,
                                                       Operation<NaturalSpawner.SpawnState> original) {
        return SpawnDensityHooks.createStateHook(
                ((ServerChunkCache) (Object) this).level, spawnableChunkCount, localMobCaps,
                () -> original.call(spawnableChunkCount, entities, chunkGetter, localMobCaps));
    }
}
