package dev.weft.neoforge.gametest;

import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.regiontick.RegionTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P2 increment 2 hard gate: the real chunk→region mapping
 * ({@link RegionTopology}, fed by actual chunk load events) must satisfy the
 * RFC-0001 §4.2 invariants on real chunk layouts — every loaded chunk maps
 * to a region, far-apart islands are distinct regions, and loading a chain
 * of chunks between them merges everything into one region.
 *
 * <p>Unload→split is not asserted here: releasing a forced ticket does not
 * unload the chunk synchronously (vanilla ticket decay), so a gametest
 * cannot deterministically observe the removal path. That path is covered by
 * the engine unit tests ({@code RegionManagerTest}) — this test's job is the
 * event feed and the layout invariants over chunks vanilla actually loaded.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class RegionTopologyGameTests {

    /** Chunk gap between the two islands; must exceed mergeDistance. */
    private static final int ISLAND_GAP_CHUNKS = 40;
    /** Half-width of each forced island (radius 1 = 3x3 chunks). */
    private static final int ISLAND_RADIUS = 1;

    @GameTest(template = "empty", batch = "p2regions", timeoutTicks = 400)
    public void regionTopologyTracksRealChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos origin = new ChunkPos(WeftBenchGameTests.groundOrigin(helper));
        // Two 3x3 islands, far outside every other batch's field and far
        // apart (gap >> mergeDistance), plus a bridge lane between them.
        int az = origin.z + 60;
        int ax = origin.x + 60;
        int bx = ax + ISLAND_GAP_CHUNKS;
        List<ChunkPos> forced = new ArrayList<>();

        forceIsland(level, forced, ax, az);
        forceIsland(level, forced, bx, az);

        helper.runAfterDelay(5, () -> {
            RegionManager topology = RegionTopology.managerFor(level);
            Region regionA = topology.regionAt(ax, az);
            Region regionB = topology.regionAt(bx, az);
            if (regionA == null || regionB == null) {
                release(level, forced);
                helper.fail("Loaded forced chunks did not enter the topology: A="
                        + regionA + " B=" + regionB + " (" + RegionTopology.summary() + ")");
            }
            if (regionA == regionB) {
                release(level, forced);
                helper.fail(String.format(Locale.ROOT,
                        "Islands %d chunks apart (mergeDistance %d) must be distinct regions",
                        ISLAND_GAP_CHUNKS, WeftConfig.MERGE_DISTANCE));
            }
            // Every chunk of each island maps to its island's region.
            for (int dx = -ISLAND_RADIUS; dx <= ISLAND_RADIUS; dx++) {
                for (int dz = -ISLAND_RADIUS; dz <= ISLAND_RADIUS; dz++) {
                    if (topology.regionAt(ax + dx, az + dz) != regionA
                            || topology.regionAt(bx + dx, az + dz) != regionB) {
                        release(level, forced);
                        helper.fail("An island chunk mapped outside its island's region");
                    }
                }
            }
            // Bridge: a chunk every mergeDistance steps chains the islands
            // into one region (merge-on-proximity, RFC-0001 sec. 4.2).
            for (int x = ax; x <= bx; x += WeftConfig.MERGE_DISTANCE) {
                force(level, forced, x, az);
            }
        });

        helper.runAfterDelay(10, () -> {
            RegionManager topology = RegionTopology.managerFor(level);
            Region merged = topology.regionAt(ax, az);
            boolean ok = merged != null && topology.regionAt(bx, az) == merged;
            release(level, forced);
            if (!ok) {
                helper.fail("Bridged islands did not merge into one region ("
                        + RegionTopology.summary() + ")");
            }
            helper.succeed();
        });
    }

    private static void forceIsland(ServerLevel level, List<ChunkPos> forced, int cx, int cz) {
        for (int dx = -ISLAND_RADIUS; dx <= ISLAND_RADIUS; dx++) {
            for (int dz = -ISLAND_RADIUS; dz <= ISLAND_RADIUS; dz++) {
                force(level, forced, cx + dx, cz + dz);
            }
        }
    }

    private static void force(ServerLevel level, List<ChunkPos> forced, int cx, int cz) {
        level.setChunkForced(cx, cz, true);
        level.getChunk(cx, cz); // synchronous load -> ChunkEvent.Load fires
        forced.add(new ChunkPos(cx, cz));
    }

    private static void release(ServerLevel level, List<ChunkPos> forced) {
        for (ChunkPos pos : forced) {
            level.setChunkForced(pos.x, pos.z, false);
        }
    }
}
