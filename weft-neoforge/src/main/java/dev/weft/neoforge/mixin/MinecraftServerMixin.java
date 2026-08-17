package dev.weft.neoforge.mixin;

import dev.weft.neoforge.WeftMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * First engine entry point (P1, telemetry mode): run the Weft pipeline
 * alongside each vanilla tick. In P2 this becomes the replacement of the
 * tick body behind a config flag (RFC-0001 §11).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void weft$onTickStart(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        WeftMod.onVanillaTick();
    }
}
