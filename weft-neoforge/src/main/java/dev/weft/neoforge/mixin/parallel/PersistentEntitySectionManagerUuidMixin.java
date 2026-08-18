package dev.weft.neoforge.mixin.parallel;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RFC-0006 hazard 9: {@code knownUuids} is a plain HashSet touched on every
 * spawn (add) and removal (remove) — both performed inside parallel buckets.
 * Swapped for a concurrent set at construction; every other shared mutation
 * on the add/remove paths is covered by the storage/lookup/tick-list/tracker
 * locks, and the per-section multimaps are region-confined.
 */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerUuidMixin {

    @Shadow
    @Final
    @Mutable
    Set<UUID> knownUuids;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void weft$concurrentUuids(CallbackInfo ci) {
        Set<UUID> concurrent = ConcurrentHashMap.newKeySet();
        concurrent.addAll(this.knownUuids);
        this.knownUuids = concurrent;
    }
}
