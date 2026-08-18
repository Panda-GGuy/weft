package dev.weft.neoforge.gametest;

import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.profiler.WeftProfiler;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;

/**
 * <b>The first P2 throughput number of any kind.</b> Every other P2 gate
 * asserts correctness — bit-identical digests, conserved totals, contracts —
 * and none of them has ever answered "is it faster?". This one does, for
 * WS-10 block-entity sharding (RFC-0008), on the shape the profiler says
 * actually matters: a <em>single region</em>, where region-level parallelism
 * is arithmetically a no-op and intra-region sharding is the only lever.
 *
 * <p>Method is the same same-run A/B the P1 exit criterion used, because
 * cross-run comparisons on this project have already produced one round of
 * numbers that turned out to be variance:
 * <ol>
 *   <li><b>Phase A</b> — partitioned ticking, sharding OFF: the serial
 *       per-region path (increment 4), measured as full-tick MSPT.</li>
 *   <li><b>Phase B</b> — identical world, sharding ON.</li>
 * </ol>
 * Both phases run in one server, one world, back to back, so the delta is
 * attributable to the flag rather than to JIT state, GC mood, or machine
 * load. Full-tick MSPT (not a phase slice) is what an admin sees in
 * {@code /tps} or spark.
 *
 * <p>Marked {@code required = false}: this is a trend line, like the other
 * measurement batches. A throughput assertion that fails the build on a busy
 * machine teaches people to ignore the suite. The engagement guard below is
 * a hard failure though — a run where sharding never fanned out would report
 * a meaningless 1.00x and must not be recorded as if it meant something.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class ShardingBenchGameTests {

    private static final int WARMUP_TICKS = 60;
    private static final int PHASE_TICKS = 300;
    /** Rig chunks per axis. 10x10 = 100 chunks, 25 per colour. */
    private static final int GRID = 10;
    /** Hopper stacks per chunk — 400 ticking block entities total. */
    private static final int[][] STACK_OFFSETS = {{4, 4}, {4, 12}, {12, 4}, {12, 12}};
    private static final int STACK = 64;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @GameTest(template = "empty", batch = "p2shardbench", timeoutTicks = 2400, required = false)
    public void p2BlockEntityShardingMspt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BlockPos origin = new BlockPos(ground.getX() + 160, 0, ground.getZ() - 160);
        WeftBenchGameTests.forceChunks(level, origin, true);
        BlockPos base = new BlockPos(origin.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ()),
                origin.getZ());

        // Sharding must be the only variable.
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        LegacyRouting.setActive(false);
        WeftConfig.PROFILING_ENABLED = true;
        WeftConfig.PROFILE_WINDOW_TICKS = PHASE_TICKS + 64;

        long[] phaseStart = new long[1];
        double[] serial = new double[2];
        long[] baselines = new long[2];

        helper.runAfterDelay(1, () -> {
            buildRig(level, base);
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setBlockEntitySharding(false);
        });

        // Phase A: serial per-region block-entity ticking.
        helper.runAfterDelay(WARMUP_TICKS, () -> phaseStart[0] = WeftProfiler.get().tickCounter());

        // Phase B: same rig, sharding on.
        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            double[] mspt = WeftBenchGameTests.msptMsPerTick(helper, phaseStart[0]);
            serial[0] = mspt[0];
            serial[1] = mspt[1];
            baselines[0] = RegionizedTicking.shardPasses();
            baselines[1] = RegionizedTicking.shardedUnits();
            RegionizedTicking.setBlockEntitySharding(true);
            phaseStart[0] = WeftProfiler.get().tickCounter();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            double[] sharded = WeftBenchGameTests.msptMsPerTick(helper, phaseStart[0]);
            long passes = RegionizedTicking.shardPasses() - baselines[0];
            long units = RegionizedTicking.shardedUnits() - baselines[1];
            int maxConcurrent = RegionizedTicking.lastMaxConcurrentShards();
            int beCount = countBlockEntities(level, base);
            tearDown(level, base);

            if (passes == 0 || units == 0) {
                helper.fail("Sharding never engaged during phase B (" + passes + " passes, "
                        + units + " units) - any speedup number from this run would be a lie");
            }
            if (maxConcurrent < 2) {
                helper.fail("No colour pass had >= 2 concurrent chunks (max " + maxConcurrent
                        + ") - phase B was effectively serial");
            }

            double speedup = sharded[0] > 0 ? serial[0] / sharded[0] : 0.0;
            double reduction = 100.0 * (1.0 - sharded[0] / serial[0]);
            BenchRecorder.record(level.getServer(),
                    "p2_be_sharding_mspt_serial", "ms/tick", serial[0],
                    String.format(Locale.ROOT,
                            "full-tick MSPT, ONE region, partitioned ticking with "
                                    + "blockEntitySharding OFF; %d ticking block entities across "
                                    + "%d chunks; p95 %.3f ms; %d measured ticks",
                            beCount, GRID * GRID, serial[1], PHASE_TICKS));
            BenchRecorder.record(level.getServer(),
                    "p2_be_sharding_mspt_sharded", "ms/tick", sharded[0],
                    String.format(Locale.ROOT,
                            "same run, blockEntitySharding ON (4 colour passes): %.2fx speedup, "
                                    + "%.1f%% full-tick MSPT reduction; p95 %.3f ms; %d shard "
                                    + "passes over %d units, max %d concurrent chunks",
                            speedup, reduction, sharded[1], passes, units, maxConcurrent));
            helper.succeed();
        });
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

    private static BlockPos column(BlockPos base, int cx, int cz, int[] offset) {
        return new BlockPos(base.getX() + cx * 16 + offset[0], base.getY() + 2,
                base.getZ() + cz * 16 + offset[1]);
    }

    /** Chest → hopper → chest per stack, all in one block column (one chunk). */
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

    private static void tearDown(ServerLevel level, BlockPos base) {
        RegionizedTicking.setActive(false);
        WeftConfig.PROFILE_WINDOW_TICKS = 100;
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
        WeftBenchGameTests.forceChunks(level, base, false);
        WeftModules.resolve();
    }
}
