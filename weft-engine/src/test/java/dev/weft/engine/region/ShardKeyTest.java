package dev.weft.engine.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShardKeyTest {

    @Test
    void roundTrips() {
        long key = ShardKey.pack(123_456_789L, 42);
        assertEquals(123_456_789L, ShardKey.regionId(key));
        assertEquals(42, ShardKey.shardIndex(key));

        long max = ShardKey.pack((1L << 48) - 1, 65_535);
        assertEquals((1L << 48) - 1, ShardKey.regionId(max));
        assertEquals(65_535, ShardKey.shardIndex(max));

        long zero = ShardKey.pack(0, 0);
        assertEquals(0, ShardKey.regionId(zero));
        assertEquals(0, ShardKey.shardIndex(zero));
    }

    @Test
    void distinctPairsDistinctKeys() {
        assertEquals(ShardKey.pack(1, 0), ShardKey.pack(1, 0));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                ShardKey.pack(1, 1), ShardKey.pack(2, 0));
    }

    @Test
    void rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> ShardKey.pack(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> ShardKey.pack(1L << 48, 0));
        assertThrows(IllegalArgumentException.class, () -> ShardKey.pack(1, -1));
        assertThrows(IllegalArgumentException.class, () -> ShardKey.pack(1, 1 << 16));
    }
}
