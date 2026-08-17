package dev.weft.engine.region;

/**
 * Packed (regionId, shardIndex) shard identity (RFC-0004 §2.1) — same
 * bit-packing idiom as {@link ChunkKey}. Region ids are sequential longs
 * starting at 1, so 48 bits is generous; 16 bits of shard index caps a
 * region at 65k shards, far above any sane worker count.
 */
public final class ShardKey {
    private ShardKey() {}

    private static final long MAX_REGION_ID = (1L << 48) - 1;
    private static final int MAX_SHARD_INDEX = (1 << 16) - 1;

    public static long pack(long regionId, int shardIndex) {
        if (regionId < 0 || regionId > MAX_REGION_ID) {
            throw new IllegalArgumentException("regionId out of range: " + regionId);
        }
        if (shardIndex < 0 || shardIndex > MAX_SHARD_INDEX) {
            throw new IllegalArgumentException("shardIndex out of range: " + shardIndex);
        }
        return (regionId << 16) | shardIndex;
    }

    public static long regionId(long key) {
        return key >>> 16;
    }

    public static int shardIndex(long key) {
        return (int) (key & 0xFFFF);
    }
}
