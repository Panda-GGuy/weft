package dev.weft.engine.shard;

import dev.weft.engine.region.ChunkKey;

/**
 * The spatial-separation scheme behind intra-region block-entity sharding
 * (RFC-0008 §3): a 2×2 chunk coloring whose same-color chunks are far enough
 * apart that a short-reach tickable in one cannot touch another.
 *
 * <p>Colors are {@code (cx mod 2, cz mod 2)} → 0..3. Two <em>distinct</em>
 * chunks of the same color have both coordinate deltas even, so at least one
 * delta is ≥ 2 chunks; chunk {@code c} spans blocks {@code [16c, 16c+15]},
 * so a delta of 2 leaves a gap of {@link #MIN_BLOCK_GAP} empty blocks
 * between them along that axis. Any interaction whose reach is at most that
 * gap therefore cannot cross between two concurrently-running same-color
 * chunks — which is what lets RFC-0006's "confined by construction"
 * argument survive at sub-region grain, where regions no longer provide the
 * separation.
 *
 * <p>The scheme is deliberately a pure function of position: it needs no
 * state, produces the same colors on every run, and so keeps the sharded
 * execution order reproducible (RFC-0004 §2.4's determinism requirement
 * applied to space instead of RNG).
 */
public final class ChunkColoring {

    private ChunkColoring() {}

    /** Number of passes one section is split into; every color runs alone. */
    public static final int COLORS = 4;

    /**
     * Empty blocks guaranteed between two distinct same-color chunks along
     * their separating axis. Chunks at {@code cx} and {@code cx+2} span
     * {@code [16cx, 16cx+15]} and {@code [16cx+32, 16cx+47]}, leaving blocks
     * {@code 16cx+16 .. 16cx+31} between them: 16 blocks. An interaction of
     * reach ≤ 16 blocks cannot span the gap; vanilla's short-reach block
     * entities (hopper: one block) sit far inside it, and the types that do
     * not are excluded from sharding entirely (RFC-0008 §3 point 4).
     */
    public static final int MIN_BLOCK_GAP = 16;

    /** Color of a chunk, 0..{@code COLORS-1}. Pure function of position. */
    public static int of(int chunkX, int chunkZ) {
        return (Math.floorMod(chunkX, 2) << 1) | Math.floorMod(chunkZ, 2);
    }

    /** Color of a packed {@link ChunkKey}. */
    public static int ofKey(long chunkKey) {
        return of(ChunkKey.x(chunkKey), ChunkKey.z(chunkKey));
    }

    /**
     * Whether two chunks may run concurrently: same color and distinct is
     * always safe by the separation argument; different colors never run in
     * the same pass. Exposed for the invariant tests, which are the actual
     * proof that the scheme means what the class doc claims.
     */
    public static boolean separated(int ax, int az, int bx, int bz) {
        if (ax == bx && az == bz) {
            return false;
        }
        return Math.abs(ax - bx) >= 2 || Math.abs(az - bz) >= 2;
    }
}
