package dev.weft.neoforge.mixin.optimization;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Consumer;

/**
 * Access to the private {@code ServerChunkCache.getFullChunk} — the exact
 * chunk gate vanilla's {@code createState} scan uses (visible holder map +
 * completed full-chunk FUTURE). The spawn-density capture must use this
 * gate, not {@code getChunkNow} (which gates on chunk STATUS only): the two
 * disagree for chunks whose status is FULL but whose full future has not
 * completed under current ticket levels, and vanilla skips those entities
 * from its spawn counts. The graduation gametest caught the difference as a
 * systematic parity mismatch (400 counted vs vanilla's 115) on a
 * freshly force-loaded world.
 */
@Mixin(ServerChunkCache.class)
public interface ServerChunkCacheAccessor {

    @Invoker("getFullChunk")
    void weft$invokeGetFullChunk(long chunkKey, Consumer<LevelChunk> consumer);
}
