package dev.weft.engine.shard;

import dev.weft.engine.region.ChunkKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The separation invariant is the entire safety argument for RFC-0008's
 * block-entity sharding, so it is proved here exhaustively rather than
 * asserted in a doc comment: any two distinct chunks that share a color are
 * at least two chunks apart on some axis, hence at least
 * {@link ChunkColoring#MIN_BLOCK_GAP} empty blocks apart.
 */
class ChunkColoringTest {

    /** Exhaustive over a window that covers every residue combination. */
    @Test
    void sameColorChunksAreAlwaysSeparated() {
        int range = 24;
        for (int ax = -range; ax <= range; ax++) {
            for (int az = -range; az <= range; az++) {
                for (int bx = -range; bx <= range; bx++) {
                    for (int bz = -range; bz <= range; bz++) {
                        if (ax == bx && az == bz) {
                            continue;
                        }
                        if (ChunkColoring.of(ax, az) != ChunkColoring.of(bx, bz)) {
                            continue;
                        }
                        assertTrue(ChunkColoring.separated(ax, az, bx, bz),
                                "same-colored chunks (" + ax + "," + az + ") and ("
                                        + bx + "," + bz + ") are adjacent - the coloring's "
                                        + "separation guarantee is broken");
                    }
                }
            }
        }
    }

    /** Adjacent chunks — the interacting case — must never share a color. */
    @Test
    void adjacentChunksNeverShareAColor() {
        for (int cx = -12; cx <= 12; cx++) {
            for (int cz = -12; cz <= 12; cz++) {
                int color = ChunkColoring.of(cx, cz);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        assertNotEquals(color, ChunkColoring.of(cx + dx, cz + dz),
                                "chunk (" + cx + "," + cz + ") shares a color with its "
                                        + "neighbour (" + (cx + dx) + "," + (cz + dz) + ")");
                    }
                }
            }
        }
    }

    /** Negative coordinates must color consistently (floorMod, not %). */
    @Test
    void coloringIsConsistentAcrossTheOrigin() {
        assertEquals(ChunkColoring.of(0, 0), ChunkColoring.of(-2, -2));
        assertEquals(ChunkColoring.of(1, 1), ChunkColoring.of(-1, -1));
        assertEquals(ChunkColoring.of(-3, 5), ChunkColoring.of(1, 1));
        for (int cx = -9; cx <= 9; cx++) {
            for (int cz = -9; cz <= 9; cz++) {
                int color = ChunkColoring.of(cx, cz);
                assertTrue(color >= 0 && color < ChunkColoring.COLORS, "color out of range");
                assertEquals(color, ChunkColoring.ofKey(ChunkKey.pack(cx, cz)),
                        "packed-key coloring must match coordinate coloring");
            }
        }
    }
}
