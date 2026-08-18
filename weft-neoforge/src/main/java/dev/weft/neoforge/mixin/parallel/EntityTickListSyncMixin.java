package dev.weft.neoforge.mixin.parallel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * RFC-0006 hazard 6: the tick list's active map is a plain
 * Int2ObjectLinkedOpenHashMap; spawns and deaths inside parallel buckets
 * add/remove concurrently. Iteration ({@code forEach}) stays main-thread —
 * Weft's collection pass — and is untouched.
 */
@Mixin(EntityTickList.class)
public abstract class EntityTickListSyncMixin {

    @Unique
    private final Object weft$lock = new Object();

    @WrapMethod(method = "add")
    private void weft$syncAdd(Entity entity, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private void weft$syncRemove(Entity entity, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "contains")
    private boolean weft$syncContains(Entity entity, Operation<Boolean> original) {
        synchronized (weft$lock) {
            return original.call(entity);
        }
    }
}
