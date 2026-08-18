package dev.weft.neoforge.mixin.parallel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.weft.neoforge.regiontick.WeftThreadAwareNeighborUpdater;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.ThreadSafeLegacyRandomSource;
import net.minecraft.world.level.redstone.NeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-level shared-state hardening for parallel region buckets (RFC-0006):
 *
 * <ul>
 *   <li><b>Hazard 5</b> — server levels swap {@code level.random} from
 *       {@code LegacyRandomSource} (whose ThreadingDetector hard-crashes on
 *       concurrent draws) to {@code ThreadSafeLegacyRandomSource}: identical
 *       LCG constants, identical seed scramble, identical single-threaded
 *       sequence — worldgen has shipped on it for years. (Nuance: the
 *       thread-safe variant's {@code setSeed} does not reset the cached
 *       gaussian pair; the parity suite compares runs against each other on
 *       the same source, so its gates are unaffected.)</li>
 *   <li><b>Hazard 10</b> — the neighbor updater becomes thread-aware
 *       ({@link WeftThreadAwareNeighborUpdater}): update chains from region
 *       workers run on per-thread collectors.</li>
 *   <li><b>Hazard 13</b> — {@code addBlockEntityTicker} synchronizes: BE
 *       ticks that add new tickers land on plain ArrayLists.</li>
 *   <li><b>Sub-tick counter</b> — {@code nextSubTickCount} ({@code
 *       subTickCount++} on a plain long) synchronizes: scheduled-tick
 *       ordering keys must stay unique under concurrent scheduling.</li>
 * </ul>
 *
 * <p>The swaps and locks are active regardless of the parallelRegions flag:
 * single-threaded semantics are identical and the uncontended cost is
 * nanoseconds, so there is no mode split to reason about.
 */
@Mixin(Level.class)
public abstract class LevelParallelSafetyMixin {

    @Shadow
    @Final
    @Mutable
    public RandomSource random;

    @Shadow
    @Final
    @Mutable
    protected NeighborUpdater neighborUpdater;

    @Shadow
    @Final
    public boolean isClientSide;

    @Unique
    private final Object weft$tickerLock = new Object();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void weft$hardenSharedState(CallbackInfo ci) {
        if (!this.isClientSide && (Object) this instanceof ServerLevel) {
            this.random = new ThreadSafeLegacyRandomSource(RandomSupport.generateUniqueSeed());
            this.neighborUpdater =
                    new WeftThreadAwareNeighborUpdater((Level) (Object) this, this.neighborUpdater);
        }
    }

    @WrapMethod(method = "addBlockEntityTicker")
    private void weft$syncAddTicker(TickingBlockEntity ticker, Operation<Void> original) {
        synchronized (weft$tickerLock) {
            original.call(ticker);
        }
    }

    @WrapMethod(method = "nextSubTickCount")
    private long weft$syncSubTick(Operation<Long> original) {
        synchronized (weft$tickerLock) {
            return original.call();
        }
    }
}
