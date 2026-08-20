package dev.weft.neoforge.gametest;

import dev.weft.engine.telemetry.SectionSamples;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.observability.WeftObservability;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
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
 * WS-10 block-entity sharding (RFC-0008), measured on the shape the profiler
 * says actually matters for solo play: a <em>single region</em>, where region
 * parallelism is arithmetically a no-op and intra-region sharding is the only
 * lever there is.
 *
 * <h2>Why this benchmark was rewritten</h2>
 *
 * <p>The first version measured <b>full-tick MSPT</b> across a two-phase
 * same-run A/B, and it could not be quoted. Six runs:
 *
 * <pre>
 *   400 BE, short warmup   2.048 → 1.288   1.59x     &lt;- warmup artifact
 *   400 BE, short warmup   1.300 → 1.264   1.03x
 *   400 BE, both warm      0.850 → 0.700   1.21x
 *   400 BE, both warm      0.573 → 0.672   0.85x     &lt;- slower!
 *  1600 BE, both warm      0.915 → 0.696   1.31x
 *  1600 BE, both warm      0.690 → 0.649   1.06x
 * </pre>
 *
 * <p>Three separate defects produced that spread, and all three are fixed here
 * (the methodology and its reasoning live in {@link SectionAb}):
 *
 * <ol>
 *   <li><b>The ruler was too wide.</b> Full-tick MSPT judged a change confined
 *       to the block-entity section, which at these workloads is a small slice
 *       of a tick that is mostly other things. The signal was swamped: the
 *       serial baseline ranged 0.573–2.048 ms while the sharded figure was
 *       strikingly stable at 0.649–0.700 ms. The section ruler measures only
 *       the section.</li>
 *   <li><b>Two phases leave warmup order bias.</b> The original 1.59x was
 *       phase A paying JIT costs phase B inherited the benefit of. Warming
 *       both paths fixed the worst of it; interleaving three phases per
 *       condition fixes the drift that warming alone cannot.</li>
 *   <li><b>The profiler was biasing the baseline — in sharding's favour.</b>
 *       This one was invisible until the region-parallelism bench forced the
 *       question. {@code WeftProfiler} is server-thread-confined, so its two
 *       clock reads per block entity happen in the serial phase and silently
 *       <em>stop</em> in the sharded phase, where units run on workers. Every
 *       number in that table was measured with profiling on, which means the
 *       baseline was carrying overhead the sharded phase did not pay — and it
 *       <em>still</em> could not show a consistent win. That strengthens rather
 *       than weakens the original verdict.</li>
 * </ol>
 *
 * <p><b>And a negative control</b>, which the old bench had no equivalent of.
 * {@link #blockEntityShardingBelowThreshold} runs the identical rig and flips
 * the identical flag with {@code blockEntityShardMinUnits} raised above the
 * unit count, so {@code RegionizedTicking} takes the serial path regardless.
 * Its ratio must come back ~1.0. Six readings spanning 0.85x–1.31x with
 * nothing in the rig having a known answer is exactly how a project ends up
 * quoting variance; this is the fix for that class of error, not just for the
 * one instance of it.
 *
 * <p>Marked {@code required = false}: this is a trend line, like the other
 * measurement batches. The engagement guards are hard failures — a run where
 * sharding never fanned out would report a meaningless 1.00x and must not be
 * recorded as if it meant something.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class ShardingBenchGameTests {

    private static final int WARMUP_TICKS = 120;
    private static final int PHASE_TICKS = 150;
    private static final int PHASES = 6;

    /**
     * Rig chunks per axis. 20x20 = 400 chunks, 100 per colour, 1600 ticking
     * block entities.
     *
     * <p>Sized up from 10x10 because 400 hoppers produced sub-millisecond
     * ticks where measurement noise swamped the effect. The section ruler
     * resolves far better than full-tick MSPT did, but the workload stays large
     * so the reading is about the mechanism and not about clock granularity.
     */
    private static final int GRID = 20;
    /** Hopper stacks per chunk — 1600 ticking block entities total. */
    private static final int[][] STACK_OFFSETS = {{4, 4}, {4, 12}, {12, 4}, {12, 12}};
    private static final int STACK = 64;

    /** See {@code ParallelRegionsBenchGameTests}: same reasoning, same number. */
    private static final double CONTROL_CEILING = 1.20;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** The real reading: 1600 hoppers in one region, 4 colour passes. */
    @GameTest(template = "empty", batch = "p2shardbench", timeoutTicks = 4000, required = false)
    public void blockEntityShardingSection(GameTestHelper helper) {
        run(helper, "p2_be_sharding", false);
    }

    /**
     * The negative control: identical rig, identical flag flips, but
     * {@code blockEntityShardMinUnits} is raised past the unit count so the
     * region takes the serial path in both phases. Any speedup reported here is
     * phase order, and would condemn the real reading with it.
     */
    @GameTest(template = "empty", batch = "p2shardbenchctl", timeoutTicks = 4000,
            required = false)
    public void blockEntityShardingBelowThreshold(GameTestHelper helper) {
        run(helper, "p2_be_sharding_control", true);
    }

    private void run(GameTestHelper helper, String metricPrefix, boolean control) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BlockPos origin = new BlockPos(ground.getX() + 160, 0, ground.getZ() - 160);
        WeftBenchGameTests.forceChunks(level, origin, true);
        BlockPos base = new BlockPos(origin.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ()),
                origin.getZ());

        // Sharding must be the only variable. Profiling included: it is
        // server-thread-confined, so leaving it on bills its per-block-entity
        // clock reads to the serial phase alone (see the class note).
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        LegacyRouting.setActive(false);
        WeftObservability.setActive(false);
        WeftConfig.PROFILING_ENABLED = false;
        int restoreMinUnits = WeftConfig.BLOCK_ENTITY_SHARD_MIN_UNITS;
        // The control's whole mechanism: a threshold no region in this rig can
        // reach, so `sharded` stays true but the colouring never runs.
        WeftConfig.BLOCK_ENTITY_SHARD_MIN_UNITS = control ? 1_000_000 : 64;

        int capacity = PHASES * PHASE_TICKS;
        SectionAb.Reading serial = new SectionAb.Reading(capacity);
        SectionAb.Reading shardedReading = new SectionAb.Reading(capacity);
        SectionAb.Reading[] live = new SectionAb.Reading[1];
        long[] lastPasses = new long[1];
        long[] engagement = new long[3];

        SectionAb.schedule(helper, level, WARMUP_TICKS, PHASE_TICKS, PHASES,
                () -> {
                    buildRig(level, base);
                    RegionizedTicking.setActive(true);
                    RegionizedTicking.setPartitioned(true);
                    RegionizedTicking.setParallel(false);
                    RegionizedTicking.setBlockEntitySharding(false);
                    lastPasses[0] = RegionizedTicking.shardPasses();
                    // One region never fans out across buckets, so `fannedOut`
                    // says nothing here; a rising shard-pass count is the only
                    // honest signal that the colouring actually ran.
                    SectionAb.install("BLOCK_ENTITY", live, (buckets, fannedOut) -> {
                        long now = RegionizedTicking.shardPasses();
                        boolean engaged = now > lastPasses[0];
                        lastPasses[0] = now;
                        return engaged;
                    });
                },
                on -> {
                    RegionizedTicking.setBlockEntitySharding(on);
                    // Rebase the pass counter at every phase boundary. Without
                    // this the detector compares against the count taken before
                    // the ON warmup ran, so the first baseline section sees the
                    // warmup's passes and is charged as "engaged" - which is
                    // exactly what the baseline-purity check caught (1 of 450).
                    lastPasses[0] = RegionizedTicking.shardPasses();
                },
                serial, shardedReading, live,
                () -> {
                    engagement[0] = RegionizedTicking.shardPasses();
                    engagement[1] = RegionizedTicking.shardedUnits();
                    engagement[2] = RegionizedTicking.lastMaxConcurrentShards();
                    int beCount = countBlockEntities(level, base);
                    tearDown(level, base);
                    WeftConfig.BLOCK_ENTITY_SHARD_MIN_UNITS = restoreMinUnits;
                    verdict(helper, level, metricPrefix, control, beCount, engagement,
                            serial, shardedReading);
                });
    }

    private void verdict(GameTestHelper helper, ServerLevel level, String metricPrefix,
                         boolean control, int beCount, long[] engagement,
                         SectionAb.Reading serial, SectionAb.Reading sharded) {
        String inadmissible = SectionAb.inadmissible(serial, sharded, true);
        if (inadmissible != null) {
            helper.fail("Reading inadmissible: " + inadmissible);
            return;
        }

        SectionSamples.Stats serialStats = serial.stats();
        SectionSamples.Stats shardedStats = sharded.stats();
        double ratio = serialStats.medianMillis() / shardedStats.medianMillis();
        double p95Ratio = shardedStats.p95Millis() > 0
                ? serialStats.p95Millis() / shardedStats.p95Millis() : 0.0;
        double serialMspt = serial.medianMspt();
        double shardedMspt = sharded.medianMspt();
        double msptRatio = shardedMspt > 0 ? serialMspt / shardedMspt : 0.0;

        String shape = String.format(Locale.ROOT,
                "ONE region, %d ticking block entities across %d chunks, 4 colour passes; "
                        + "%d+%d measured ticks (%d skipped/phase, %d phases interleaved)",
                beCount, GRID * GRID, serialStats.count(), shardedStats.count(),
                SectionAb.SKIP_LEADING, PHASES);

        BenchRecorder.record(level.getServer(), metricPrefix + "_section_serial", "ms/tick",
                serialStats.medianMillis(),
                String.format(Locale.ROOT,
                        "median block-entity-section wall time, blockEntitySharding OFF; "
                                + "p95 %.3f ms; %s", serialStats.p95Millis(), shape));
        BenchRecorder.record(level.getServer(), metricPrefix + "_section_sharded", "ms/tick",
                shardedStats.medianMillis(),
                String.format(Locale.ROOT,
                        "median block-entity-section wall time, blockEntitySharding ON; "
                                + "p95 %.3f ms; %.2fx vs serial (p95 %.2fx); %d shard passes over "
                                + "%d units, max %d concurrent chunks; %s",
                        shardedStats.p95Millis(), ratio, p95Ratio, engagement[0], engagement[1],
                        engagement[2], shape));
        BenchRecorder.record(level.getServer(), metricPrefix + "_mspt_serial", "ms/tick",
                serialMspt, "vanilla getAverageTickTimeNanos, blockEntitySharding OFF; " + shape);
        BenchRecorder.record(level.getServer(), metricPrefix + "_mspt_sharded", "ms/tick",
                shardedMspt,
                String.format(Locale.ROOT,
                        "vanilla getAverageTickTimeNanos, blockEntitySharding ON; %.2fx full-tick "
                                + "(Amdahl-bounded by the rest of the tick); %s",
                        msptRatio, shape));

        level.getServer().sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "[weft-bench] %s: BE section %.3f -> %.3f ms (%.2fx), p95 %.3f -> %.3f ms "
                        + "(%.2fx), MSPT %.3f -> %.3f ms (%.2fx), %d passes / %d units; %s",
                metricPrefix, serialStats.medianMillis(), shardedStats.medianMillis(), ratio,
                serialStats.p95Millis(), shardedStats.p95Millis(), p95Ratio,
                serialMspt, shardedMspt, msptRatio, engagement[0], engagement[1], shape)));

        if (control) {
            if (sharded.engagedSections != 0) {
                helper.fail("The below-threshold control sharded " + sharded.engagedSections
                        + " sections - blockEntityShardMinUnits did not hold it off and it is "
                        + "not a control");
                return;
            }
            if (ratio > CONTROL_CEILING) {
                helper.fail(String.format(Locale.ROOT,
                        "NEGATIVE CONTROL FAILED: a rig held below the sharding threshold "
                                + "reported %.2fx from a flag that cannot engage there. The "
                                + "harness is measuring phase order and the real reading must be "
                                + "discarded with it (serial %.3f ms, 'sharded' %.3f ms)",
                        ratio, serialStats.medianMillis(), shardedStats.medianMillis()));
                return;
            }
            helper.succeed();
            return;
        }

        if (sharded.engagedSections < sharded.sections) {
            helper.fail("Only " + sharded.engagedSections + " of " + sharded.sections
                    + " measured sections sharded - part of the 'sharded' phase ran serial and "
                    + "the ratio is unattributable");
            return;
        }
        if (engagement[0] == 0 || engagement[1] == 0) {
            helper.fail("Sharding never engaged (" + engagement[0] + " passes, " + engagement[1]
                    + " units) - any speedup number from this run would be a lie");
            return;
        }
        if (engagement[2] < 2) {
            helper.fail("No colour pass had >= 2 concurrent chunks (max " + engagement[2]
                    + ") - the sharded phase was effectively serial");
            return;
        }
        helper.succeed();
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
        WeftConfig.PROFILING_ENABLED = true;
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
