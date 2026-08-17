package dev.weft.neoforge.mixin.optimization;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Constructor access to the package-private {@code NaturalSpawner.SpawnState}
 * so the authoritative spawn-density service can hand vanilla a real
 * SpawnState built from the async result (same four fields vanilla's own
 * {@code createState} fills). Fail-soft: if this doesn't apply, the build
 * path throws on first use, is caught, and every tick falls back to
 * vanilla's synchronous scan (RFC-0003 R2).
 */
@Mixin(NaturalSpawner.SpawnState.class)
public interface SpawnStateInvoker {

    @Invoker("<init>")
    static NaturalSpawner.SpawnState weft$newSpawnState(int spawnableChunkCount,
                                                        Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
                                                        PotentialCalculator spawnPotential,
                                                        LocalMobCapCalculator localMobCapCalculator) {
        throw new AssertionError("mixin invoker not applied");
    }
}
