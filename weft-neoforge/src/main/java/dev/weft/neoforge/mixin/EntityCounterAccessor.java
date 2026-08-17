package dev.weft.neoforge.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read access to vanilla's global entity-id counter, for the vanilla-parity
 * suite (RFC-0005): entity ids leak into behavior wherever vanilla staggers
 * per-entity work by id, so the parity harness pins the counter to a fixed
 * base before each run's spawns — otherwise two identical runs would tick
 * with different ids and could legitimately diverge. Pure accessor; nothing
 * in production code paths touches it.
 */
@Mixin(Entity.class)
public interface EntityCounterAccessor {

    @Accessor("ENTITY_COUNTER")
    static AtomicInteger weft$entityCounter() {
        throw new AssertionError("mixin accessor not applied");
    }
}
