package dev.weft.neoforge.mixin;

import dev.weft.neoforge.profiler.WeftProfiler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P0 profiler hook: time every block-entity tick, attributed to the BE type
 * and chunk. Targets the vanilla ticker wrapper so every ticking BE — vanilla
 * or modded — is measured at one choke point.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {

    @Shadow private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"))
    private void weft$beTickStart(CallbackInfo ci) {
        WeftProfiler.get().push();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void weft$beTickEnd(CallbackInfo ci) {
        var key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        WeftProfiler.get().popBlockEntity(
                key != null ? key.toString() : blockEntity.getClass().getName(),
                new ChunkPos(blockEntity.getBlockPos()).toLong());
    }
}
