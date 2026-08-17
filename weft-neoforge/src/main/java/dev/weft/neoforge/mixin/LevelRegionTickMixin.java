package dev.weft.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.weft.neoforge.regiontick.RegionizedBlockEntityTickMarker;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * P2 tick-ownership seam, block-entity half (RFC-0001 §11): the whole
 * {@code Level.tickBlockEntities} pass (chunk BE tickers + pending list) runs
 * through the engine on server levels. Increment 1 semantics are identical to
 * vanilla — same thread, same order — under a REGION thread context.
 *
 * <p>Client levels pass straight through: Weft is server-side only, and the
 * wrap targets {@code Level} because that is where the method is defined.
 * Fail-loud (RFC-0003 R2), same reasoning as
 * {@link ServerLevelRegionTickMixin}.
 */
@Mixin(Level.class)
public abstract class LevelRegionTickMixin implements RegionizedBlockEntityTickMarker {

    @WrapMethod(method = "tickBlockEntities")
    private void weft$ownBlockEntityTickSection(Operation<Void> original) {
        if ((Object) this instanceof ServerLevel serverLevel) {
            RegionizedTicking.tickBlockEntitySectionOwned(serverLevel, () -> original.call());
        } else {
            original.call();
        }
    }
}
