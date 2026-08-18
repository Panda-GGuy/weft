package dev.weft.neoforge.mixin.parallel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * RFC-0006 hazard 7: the section index (plain Long2ObjectOpenHashMap +
 * LongAVLTreeSet) is mutated when an entity moves into a never-populated
 * section and read by every entity query — racing under parallel buckets.
 * One coarse per-storage lock covers mutators and the eager readers; the
 * per-section multimaps stay lock-free (queries cannot reach another
 * region's sections across a ≥ mergeDistance gap). Stream-returning
 * accessors are locked for their construction only — their consumers are
 * main-thread chunk-status plumbing, documented as such in RFC-0006.
 */
@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageSyncMixin {

    @Unique
    private final Object weft$lock = new Object();

    @WrapMethod(method = "forEachAccessibleNonEmptySection")
    private void weft$syncForEach(AABB bounds, AbortableIterationConsumer<EntitySection<?>> consumer,
                                  Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(bounds, consumer);
        }
    }

    @WrapMethod(method = "getOrCreateSection")
    private EntitySection<?> weft$syncGetOrCreate(long sectionKey,
                                                  Operation<EntitySection<?>> original) {
        synchronized (weft$lock) {
            return original.call(sectionKey);
        }
    }

    @WrapMethod(method = "getSection")
    private EntitySection<?> weft$syncGetSection(long sectionKey,
                                                 Operation<EntitySection<?>> original) {
        synchronized (weft$lock) {
            return original.call(sectionKey);
        }
    }

    @WrapMethod(method = "remove")
    private void weft$syncRemove(long sectionKey, Operation<Void> original) {
        synchronized (weft$lock) {
            original.call(sectionKey);
        }
    }

    @WrapMethod(method = "getExistingSectionPositionsInChunk")
    private LongStream weft$syncSectionPositions(long chunkKey, Operation<LongStream> original) {
        synchronized (weft$lock) {
            return original.call(chunkKey);
        }
    }

    @WrapMethod(method = "getExistingSectionsInChunk")
    private Stream<EntitySection<?>> weft$syncSectionsInChunk(long chunkKey,
                                                              Operation<Stream<EntitySection<?>>> original) {
        synchronized (weft$lock) {
            return original.call(chunkKey);
        }
    }

    @WrapMethod(method = "getAllChunksWithExistingSections")
    private LongSet weft$syncAllChunks(Operation<LongSet> original) {
        synchronized (weft$lock) {
            return original.call();
        }
    }
}
