package dev.weft.neoforge.mixin.shard;

import dev.weft.neoforge.regiontick.ShardDomain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The enforcement seam for RFC-0008 §3 point 5: while a block-entity shard
 * task runs, reaching a block entity outside the shard's own chunk means some
 * ticker's reach exceeded what {@link
 * dev.weft.neoforge.regiontick.WideReachBlockEntities} accounts for.
 *
 * <p>This is deliberately on {@code getBlockEntity} rather than
 * {@code getBlockState}: cross-chunk <em>container</em> access is the
 * hopper-shaped hazard the colouring must not get wrong, while block-state
 * reads are both far hotter and already covered by the separation argument
 * itself. {@link ShardDomain#check} returns after one ThreadLocal read
 * whenever no shard task is running, so the cost outside sharding is nil.
 *
 * <p>Registered in the fail-loud config: if the seam stops applying, boot
 * crashes rather than leaving the sharded path unenforced.
 */
@Mixin(Level.class)
public abstract class LevelShardDomainMixin {

    @Inject(method = "getBlockEntity", at = @At("HEAD"))
    private void weft$checkShardDomain(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        ShardDomain.check(pos.getX(), pos.getZ(), "getBlockEntity");
    }
}
