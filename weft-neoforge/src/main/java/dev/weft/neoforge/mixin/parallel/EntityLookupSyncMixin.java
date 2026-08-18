package dev.weft.neoforge.mixin.parallel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * RFC-0006 hazard 8: the id/uuid registries are plain maps mutated on every
 * spawn/death, which parallel buckets perform concurrently. Whole-registry
 * iteration ({@code getAllEntities}/{@code getEntities}) is main-thread-only
 * by vanilla usage in the tick path and remains unsynchronized — the lazy
 * iterables could not be protected by a method-scoped lock anyway.
 */
@Mixin(EntityLookup.class)
public abstract class EntityLookupSyncMixin {

    @Unique
    private final Object weft$lock = new Object();

    @WrapMethod(method = "add")
    private void weft$syncAdd(EntityAccess entity, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "remove")
    private void weft$syncRemove(EntityAccess entity, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(entity);
        }
    }

    @WrapMethod(method = "getEntity(I)Lnet/minecraft/world/level/entity/EntityAccess;")
    private EntityAccess weft$syncGetById(int id, Operation<EntityAccess> original) {
        synchronized (weft$lock) {
            return original.call(id);
        }
    }

    @WrapMethod(method = "getEntity(Ljava/util/UUID;)Lnet/minecraft/world/level/entity/EntityAccess;")
    private EntityAccess weft$syncGetByUuid(UUID uuid, Operation<EntityAccess> original) {
        synchronized (weft$lock) {
            return original.call(uuid);
        }
    }

    @WrapMethod(method = "count")
    private int weft$syncCount(Operation<Integer> original) {
        synchronized (weft$lock) {
            return original.call();
        }
    }
}
