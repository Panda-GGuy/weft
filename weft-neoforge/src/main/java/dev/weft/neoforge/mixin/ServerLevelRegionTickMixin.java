package dev.weft.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.weft.neoforge.regiontick.RegionizedEntityTickMarker;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Consumer;

/**
 * P2 tick-ownership seam, entity half (RFC-0001 §11): the whole
 * {@code entityTickList.forEach(...)} section of {@code ServerLevel.tick}
 * runs through the engine. Increment 1 executes it serially on the calling
 * (server) thread in vanilla's own order — bit-identical by construction —
 * under a REGION thread context, so ownership exists before semantics change.
 *
 * <p>Fail-loud on purpose (RFC-0003 R2 reserves {@code require = 1} for
 * exactly this): a tick-ownership hook that silently failed to apply would be
 * dangerous once later increments change execution, so non-application is a
 * load-time crash, not a degraded mode.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelRegionTickMixin implements RegionizedEntityTickMarker {

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/entity/EntityTickList;forEach("
                            + "Ljava/util/function/Consumer;)V"))
    private void weft$ownEntityTickSection(EntityTickList list, Consumer<Entity> ticker,
                                           Operation<Void> original) {
        RegionizedTicking.tickEntitySectionOwned((ServerLevel) (Object) this,
                () -> original.call(list, ticker));
    }
}
