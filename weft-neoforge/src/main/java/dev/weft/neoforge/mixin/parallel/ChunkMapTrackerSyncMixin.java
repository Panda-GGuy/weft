package dev.weft.neoforge.mixin.parallel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * RFC-0006 (spawn/remove path): the entity tracker registry
 * ({@code ChunkMap.entityMap}, a plain Int2ObjectOpenHashMap) is mutated on
 * every spawn and removal — concurrent inside parallel buckets. Tracker
 * iteration (broadcast/tick) runs on the main thread outside the sections
 * and is untouched.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapTrackerSyncMixin {

    @Unique
    private final Object weft$lock = new Object();

    @WrapMethod(method = "addEntity")
    private void weft$syncAddEntity(Entity entity, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "removeEntity")
    private void weft$syncRemoveEntity(Entity entity, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(entity);
        }
    }
}
