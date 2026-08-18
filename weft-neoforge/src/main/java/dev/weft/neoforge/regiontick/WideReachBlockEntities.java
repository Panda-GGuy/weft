package dev.weft.neoforge.regiontick;

import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/**
 * Block-entity types excluded from chunk-colored sharding because their
 * per-tick reach can exceed the coloring's separation gap
 * ({@link dev.weft.engine.shard.ChunkColoring#MIN_BLOCK_GAP}, 16 blocks) or
 * touches state that is not chunk-owned at all (RFC-0008 §3 point 4). These
 * tick serially on the region's own thread after the colored passes — the
 * same treatment increment 4 gives topology-unmapped units.
 *
 * <p>Each entry is a reach fact, not a guess:
 * <ul>
 *   <li>{@code PISTON} — a moving-piston block entity carries a push line of
 *       up to 12 blocks and writes every block along it. Inside the gap
 *       numerically, but it writes <em>blocks</em> rather than reading a
 *       neighbour container, so it is excluded rather than argued about.</li>
 *   <li>{@code BEACON}, {@code CONDUIT} — apply mob effects to players over
 *       a radius far beyond the gap (beacons up to 64+ blocks), and player
 *       state belongs to the GLOBAL lane, not to any chunk.</li>
 *   <li>{@code SCULK_CATALYST}, {@code SCULK_SHRIEKER}, {@code SCULK_SENSOR},
 *       {@code CALIBRATED_SCULK_SENSOR} — vibration listeners and spreading
 *       charge cursors both propagate outward across chunk boundaries.</li>
 *   <li>{@code END_GATEWAY} — teleports entities, arbitrarily far.</li>
 *   <li>{@code TRIAL_SPAWNER}, {@code VAULT} — track and spawn for nearby
 *       players over a radius, and mutate player-linked state.</li>
 *   <li>{@code BELL} — its resonance sweep scans for nearby raiders.</li>
 * </ul>
 *
 * <p>Modded types are <em>not</em> listed and therefore shard: the list is a
 * conservative set of known-wide vanilla reaches, and the fail-loud
 * out-of-domain guard (RFC-0008 §3 point 5) is what catches a type — vanilla
 * or modded — that reaches further than this list assumes. A guard trip is
 * the signal to add an entry here, and it is a bug until it is.
 */
final class WideReachBlockEntities {

    private WideReachBlockEntities() {}

    private static final Set<BlockEntityType<?>> EXCLUDED = Set.of(
            BlockEntityType.PISTON,
            BlockEntityType.BEACON,
            BlockEntityType.CONDUIT,
            BlockEntityType.SCULK_CATALYST,
            BlockEntityType.SCULK_SHRIEKER,
            BlockEntityType.SCULK_SENSOR,
            BlockEntityType.CALIBRATED_SCULK_SENSOR,
            BlockEntityType.END_GATEWAY,
            BlockEntityType.TRIAL_SPAWNER,
            BlockEntityType.VAULT,
            BlockEntityType.BELL);

    /** Whether this type must tick on the serial tail rather than a shard. */
    static boolean isWideReach(BlockEntityType<?> type) {
        return type != null && EXCLUDED.contains(type);
    }

    static int excludedTypeCount() {
        return EXCLUDED.size();
    }
}
