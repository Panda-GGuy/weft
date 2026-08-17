package dev.weft.engine.region;

/**
 * Chunk-coordinate packing, kept bit-compatible with vanilla's
 * {@code ChunkPos.asLong} (low 32 bits = x, high 32 bits = z) so handles can
 * cross the engine/loader boundary without translation.
 */
public final class ChunkKey {
    private ChunkKey() {}

    public static long pack(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    public static int x(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    public static int z(long key) {
        return (int) (key >>> 32);
    }

    public static long fromBlock(int blockX, int blockZ) {
        return pack(blockX >> 4, blockZ >> 4);
    }

    /** Chebyshev distance between two chunk keys. */
    public static int distance(long a, long b) {
        return Math.max(Math.abs(x(a) - x(b)), Math.abs(z(a) - z(b)));
    }
}
