package dev.weft.neoforge.gametest;

import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.regiontick.RegionTopology;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

/**
 * RFC-0006 <b>hazard 23</b>: {@code parallelRegions} and
 * {@code blockEntitySharding} engaged <em>together</em>.
 *
 * <p>This test exists because nothing tested that combination, and the gap was
 * not subtle in hindsight — both throughput benchmarks explicitly switch the
 * other flag off. {@code p2parallelbench} calls
 * {@code setBlockEntitySharding(false)} so region parallelism is the only
 * variable; {@code p2shardbench} calls {@code setParallel(false)} so sharding
 * is. Each proved its own mechanism in isolation, both stayed green, and the
 * product ships a config that turns on both.
 *
 * <p><b>What the gap cost.</b> A live single-player world with two regions and
 * block entities in them hung the server outright. Two jstack dumps, taken
 * minutes apart, showed the same picture and the same task object: the server
 * thread parked in {@code WeftScheduler.awaitAll} under
 * {@code tickBlockEntitySectionOwned}, and all fourteen engine workers idle in
 * {@code ForkJoinPool.awaitWork}. Nothing was running; nothing ever would.
 *
 * <p>The mechanism is nested blocking submission into a fixed-size pool. With
 * two or more region buckets, {@code runBuckets} fans the block-entity section
 * out, so a bucket body executes <em>on a pool worker</em>. That body calls
 * {@code BlockEntityShards.runColoured}, which submits its colour-pass tasks to
 * <b>the same pool</b> and blocks on {@code Future.get()}. The worker holding
 * the outer barrier is therefore unavailable to run the inner tasks it is
 * waiting for. It is hazard 1's family — a thread waiting on work only a thread
 * it is blocking could do — one level further in.
 *
 * <p>The fix stands sharding down whenever the section fans out, which costs
 * nothing: RFC-0008 §1 already scopes sharding as "the solo-play lever, where
 * region-level parallelism is a no-op because the world is one region". This
 * test pins both halves of that — that the combination runs at all, and that it
 * resolves to region fan-out rather than sharding.
 *
 * <p><b>Required, and a hang is a failure.</b> The gametest framework fails a
 * test that does not finish inside {@code timeoutTicks}, so the deadlock this
 * guards against is caught by the test never completing — no assertion needed
 * for the headline case. The assertions below cover the quieter half: that the
 * run was not vacuous, and that sharding really did stand down instead of the
 * combination merely never engaging.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class CombinedParallelShardGameTests {

    private static final int SETTLE_TICKS = 20;
    private static final int RUN_TICKS = 200;
    /**
     * Hopper-stack chunks per axis per island. 5x5 = 25 chunks x 4 stacks = 100
     * ticking block entities, comfortably over the 64-unit sharding gate, and
     * small enough to sit inside the proven forced-chunk radius below.
     */
    private static final int GRID = 5;
    private static final int[][] STACK_OFFSETS = {{4, 4}, {4, 12}, {12, 4}, {12, 12}};
    private static final int ISLAND_GAP_CHUNKS = 40;
    private static final int STACK = 64;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @GameTest(template = "empty", batch = "p2combined", timeoutTicks = 1600)
    public void parallelRegionsWithBlockEntityShardingBothOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        // Geometry copied from PartitionedTickingGameTests, which is the rig
        // already proven to produce two DISTINCT regions here. The first attempt
        // placed the islands elsewhere and they merged (A=2 B=2): the gametest
        // world holds a band of loaded test structures that bridged them, so a
        // 30-chunk gap between the islands was not a 30-chunk gap in the
        // topology. The distinct-region guard caught it, which is the argument
        // for having written that guard.
        BlockPos columnA = new BlockPos(ground.getX() - 64, 0, ground.getZ() + 64);
        BlockPos columnB = new BlockPos(columnA.getX(), 0,
                columnA.getZ() + ISLAND_GAP_CHUNKS * 16);
        WeftBenchGameTests.forceChunks(level, columnA, true);
        WeftBenchGameTests.forceChunks(level, columnB, true);
        BlockPos baseA = surface(level, columnA);
        BlockPos baseB = surface(level, columnB);

        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        LegacyRouting.setActive(false);

        long[] baselines = new long[3];

        helper.runAfterDelay(1, () -> {
            buildRig(level, baseA);
            buildRig(level, baseB);
            baselines[0] = RegionizedTicking.partitionedSections();
            baselines[1] = RegionizedTicking.shardPasses();
            baselines[2] = RegionizedTicking.unmappedUnits();
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            // Both. This is the whole point of the test.
            RegionizedTicking.setParallel(true);
            RegionizedTicking.setBlockEntitySharding(true);
        });

        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS, () -> {
            long[] bePartition = RegionizedTicking.lastBlockEntityPartition();
            String[] beThreads = RegionizedTicking.lastBlockEntityPartitionThreads();
            long sections = RegionizedTicking.partitionedSections() - baselines[0];
            long shardPasses = RegionizedTicking.shardPasses() - baselines[1];
            long unmapped = RegionizedTicking.unmappedUnits() - baselines[2];
            long regionA = regionIdAt(level, baseA);
            long regionB = regionIdAt(level, baseB);
            int beCount = countBlockEntities(level, baseA) + countBlockEntities(level, baseB);
            tearDown(level, baseA, baseB, columnA, columnB);

            // Reaching this callback at all is the headline result: before the
            // fix the server thread never returned from the block-entity
            // section and the framework killed the run on timeout.
            if (regionA == regionB || regionA < 0 || regionB < 0) {
                helper.fail("Islands " + ISLAND_GAP_CHUNKS + " chunks apart did not resolve to "
                        + "two regions (A=" + regionA + " B=" + regionB + ") - the fan-out this "
                        + "test needs never happened, so it proves nothing");
                return;
            }
            if (sections < 2L * (RUN_TICKS - 32)) {
                helper.fail("Vacuous run: only " + sections + " partitioned sections across "
                        + RUN_TICKS + " ticks");
                return;
            }
            if (beCount < 2 * 64) {
                helper.fail("Only " + beCount + " block entities built; the 64-unit sharding gate "
                        + "would not engage even single-bucket, so a stood-down shard count "
                        + "below would be meaningless");
                return;
            }
            if (bePartition.length < 2) {
                helper.fail("Block-entity section ran " + bePartition.length + " bucket(s), not 2+ "
                        + "- with both islands ticking it must fan out, or this test is not "
                        + "exercising hazard 23");
                return;
            }
            long workers = Arrays.stream(beThreads)
                    .filter(t -> t != null && !t.equals("Server thread")).distinct().count();
            if (workers < 2) {
                helper.fail("Block-entity buckets ran on " + workers + " worker thread(s) "
                        + Arrays.toString(beThreads) + " - no real fan-out, so the nesting this "
                        + "test guards against could not have occurred");
                return;
            }
            // The fix, asserted directly: fan-out wins and sharding stands down.
            if (shardPasses != 0) {
                helper.fail("Sharding ran " + shardPasses + " colour passes while the section was "
                        + "fanned out across " + bePartition.length + " region buckets. That is "
                        + "the hazard 23 nesting: a bucket on a pool worker submitting colour "
                        + "passes back into the same pool and blocking on them. Sharding must "
                        + "stand down when the section fans out (RFC-0008 §1 scopes it to the "
                        + "single-region case)");
                return;
            }
            if (unmapped != 0) {
                helper.fail("Partitioner leaked " + unmapped + " units into the unmapped tail");
                return;
            }
            helper.succeed();
        });
    }

    private static BlockPos surface(ServerLevel level, BlockPos column) {
        return new BlockPos(column.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()),
                column.getZ());
    }

    private static long regionIdAt(ServerLevel level, BlockPos pos) {
        var region = RegionTopology.managerFor(level).regionAtBlock(pos.getX(), pos.getZ());
        return region == null ? -1L : region.id();
    }

    private static BlockPos column(BlockPos base, int cx, int cz, int[] offset) {
        return new BlockPos(base.getX() + cx * 16 + offset[0], base.getY() + 2,
                base.getZ() + cz * 16 + offset[1]);
    }

    private static int countBlockEntities(ServerLevel level, BlockPos base) {
        int total = 0;
        for (int cx = 0; cx < GRID; cx++) {
            for (int cz = 0; cz < GRID; cz++) {
                for (int[] offset : STACK_OFFSETS) {
                    if (level.getBlockEntity(column(base, cx, cz, offset)) != null) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    /** Chest → hopper → chest per stack, one column per site (same rig as p2shardbench). */
    private static void buildRig(ServerLevel level, BlockPos base) {
        for (int cx = 0; cx < GRID; cx++) {
            for (int cz = 0; cz < GRID; cz++) {
                for (int[] offset : STACK_OFFSETS) {
                    BlockPos col = column(base, cx, cz, offset);
                    level.setBlock(col.below(2), Blocks.SMOOTH_STONE.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                    level.setBlock(col.below(1), Blocks.CHEST.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                    level.setBlock(col, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(col.above(), Blocks.CHEST.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                    if (level.getBlockEntity(col.above()) instanceof Container source) {
                        source.setItem(0, new ItemStack(Items.STICK, STACK));
                    }
                }
            }
        }
    }

    private static void tearDown(ServerLevel level, BlockPos baseA, BlockPos baseB,
                                BlockPos columnA, BlockPos columnB) {
        RegionizedTicking.setActive(false);
        WeftConfig.PROFILE_WINDOW_TICKS = 100;
        for (BlockPos base : new BlockPos[]{baseA, baseB}) {
            for (int cx = 0; cx < GRID; cx++) {
                for (int cz = 0; cz < GRID; cz++) {
                    for (int[] offset : STACK_OFFSETS) {
                        BlockPos col = column(base, cx, cz, offset);
                        for (int dy = -2; dy <= 1; dy++) {
                            BlockPos p = col.offset(0, dy, 0);
                            if (level.getBlockEntity(p) instanceof Container c) {
                                c.clearContent();
                            }
                            if (!level.getBlockState(p).isAir()) {
                                level.setBlock(p, Blocks.AIR.defaultBlockState(), DEMOLISH_FLAGS);
                            }
                        }
                    }
                }
            }
        }
        WeftBenchGameTests.forceChunks(level, columnA, false);
        WeftBenchGameTests.forceChunks(level, columnB, false);
        WeftModules.resolve();
    }
}
