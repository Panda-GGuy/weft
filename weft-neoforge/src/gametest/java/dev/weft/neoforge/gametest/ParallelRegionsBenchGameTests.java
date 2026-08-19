package dev.weft.neoforge.gametest;

import dev.weft.engine.telemetry.SectionSamples;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.observability.WeftObservability;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.regiontick.RegionTopology;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import dev.weft.neoforge.service.SpawnDensityHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * <b>Does running regions on threads make the server faster?</b> Every P2 gate
 * so far has answered a different question. Increments 4 and 5 proved the
 * partition and then the concurrency are <em>correct</em> — bit-identical end
 * states on independent islands, zero unmapped units, buckets provably off the
 * server thread. RFC-0008's bench asked about throughput but asked it of
 * <em>intra-region</em> block-entity sharding on a one-region world, where
 * region parallelism is arithmetically a no-op. The headline mechanism of the
 * whole project — {@code parallelRegions}, RFC-0006, class E1 — had never had
 * its speed measured at all.
 *
 * <p>The methodology, and the three corrections it is built out of, lives in
 * {@link SectionAb}. What this class adds is the rig and the two things that
 * make its reading admissible:
 *
 * <ul>
 *   <li><b>Engagement guards.</b> Every measured ON section must have fanned
 *       out across all {@link #ISLANDS} buckets on at least two worker threads,
 *       no OFF section may have fanned out at all, the islands must have
 *       resolved to distinct regions, and the mob population must not have
 *       shrunk — a phase that ticked a different workload is not a phase.</li>
 *   <li><b>A negative control.</b>
 *       {@link #parallelRegionsOneRegionControl} runs this identical harness
 *       over the identical mob population packed into <em>one</em> region,
 *       where {@code runBuckets} cannot fan out because it needs two buckets.
 *       Its ratio must come back ~1.0. Without it, "the ON phase was faster"
 *       and "whichever phase we labelled ON is faster" produce identical
 *       output — and that is precisely the ambiguity RFC-0008's bench could
 *       not resolve, because nothing in its rig had a known answer.</li>
 * </ul>
 *
 * <p><b>Two numbers, two audiences.</b> The section ratio is the honest measure
 * of the mechanism: what region parallelism does to the work it actually owns.
 * Full-tick MSPT is what an admin feels, and it is Amdahl-bounded by
 * everything else in the tick — chunk IO, networking, the global sections.
 * Both are recorded; neither is quoted as the other.
 *
 * <p><b>And the shape of the world is part of the result.</b> The win is a
 * function of how many independent ticking areas exist, which is why the
 * control reads ~1.0x: one region cannot be parallelised across threads no
 * matter how many threads there are. This benchmark reports its region count
 * with every number for exactly that reason.
 *
 * <p>Marked {@code required = false} like every other measurement batch: a
 * throughput assertion that fails the build on a busy machine teaches people
 * to ignore the suite. The engagement guards are hard failures though — a run
 * where the fan-out never happened would report a meaningless number and must
 * not be recorded as if it meant something.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class ParallelRegionsBenchGameTests {

    /**
     * Islands, hence regions, hence concurrent buckets. Eight against a default
     * pool of {@code availableProcessors - reservedThreads}: enough to show
     * scaling on an ordinary machine, few enough that an 8-core CI box still
     * fans every bucket out rather than queueing them, which would measure the
     * queue instead of the mechanism.
     */
    private static final int ISLANDS = 8;
    /** Forced chunks per axis per island: 7x7 = 49 chunks, 112 blocks across. */
    private static final int ISLAND_CHUNK_RADIUS = 3;
    /**
     * Island centre separation. Must exceed {@code mergeDistance} (8) plus both
     * islands' radii, or the topology merges them and this benchmark silently
     * becomes its own control — which the distinct-region guard catches.
     */
    private static final int ISLAND_GAP_CHUNKS = 30;
    /** Mobs per island. Equal counts, so the buckets are balanced. */
    private static final int MOBS_PER_ISLAND = 220;
    /** Spawn radius in blocks — inside the forced grid, with wander slack. */
    private static final int CLUSTER_RADIUS = 30;

    private static final int WARMUP_TICKS = 120;
    private static final int PHASE_TICKS = 150;
    private static final int PHASES = 6;

    /**
     * The control's ceiling. One region cannot fan out, so flipping the flag
     * there changes nothing but a boolean read. A control that nonetheless
     * "shows" more than this much means the harness is measuring phase order,
     * and the real reading must be thrown out with it.
     */
    private static final double CONTROL_CEILING = 1.20;

    /** The real reading: eight regions, eight buckets, fan-out on. */
    @GameTest(template = "empty", batch = "p2parallelbench", timeoutTicks = 4000,
            required = false)
    public void parallelRegionsEntitySection(GameTestHelper helper) {
        run(helper, ISLANDS, "p2_parallel_regions", false);
    }

    /**
     * The negative control: identical harness, identical mob count, one region.
     * {@code runBuckets} fans out only at two or more buckets, so the flag it
     * flips is architecturally inert here — and if this shows a speedup anyway,
     * {@link #parallelRegionsEntitySection}'s number is worthless too.
     */
    @GameTest(template = "empty", batch = "p2parallelbenchctl", timeoutTicks = 4000,
            required = false)
    public void parallelRegionsOneRegionControl(GameTestHelper helper) {
        run(helper, 1, "p2_parallel_regions_control", true);
    }

    private void run(GameTestHelper helper, int islands, String metricPrefix, boolean control) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BenchmarkWorld.configure(level);
        // Cramming kills mobs, and a population that shrinks mid-run changes the
        // workload between phases - the one thing an A/B must not do.
        level.getGameRules().getRule(GameRules.RULE_MAX_ENTITY_CRAMMING)
                .set(0, level.getServer());

        // Everything else that could move the entity section stays off, so the
        // fan-out flag is the only variable (RFC-0005 §3 discipline). Profiling
        // included - see SectionAb for why leaving it on would bias the ratio.
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        LegacyRouting.setActive(false);
        WeftObservability.setActive(false);
        WeftConfig.PROFILING_ENABLED = false;

        // The control packs every mob into one island, so both rigs tick the
        // same number of mobs and only the region count differs.
        int mobsPerIsland = control ? MOBS_PER_ISLAND * ISLANDS : MOBS_PER_ISLAND;
        int clusterRadius = control ? CLUSTER_RADIUS * 2 : CLUSTER_RADIUS;
        List<BlockPos> centres = new ArrayList<>(islands);
        for (int i = 0; i < islands; i++) {
            centres.add(new BlockPos(ground.getX() + 256 + i * ISLAND_GAP_CHUNKS * 16, 0,
                    ground.getZ() - 256));
        }

        int capacity = PHASES * PHASE_TICKS;
        SectionAb.Reading serial = new SectionAb.Reading(capacity);
        SectionAb.Reading fanned = new SectionAb.Reading(capacity);
        SectionAb.Reading[] live = new SectionAb.Reading[1];
        List<Mob> population = new ArrayList<>();
        long[] regionIds = new long[islands];
        String[][] threadProbe = new String[1][];

        SectionAb.schedule(helper, level, WARMUP_TICKS, PHASE_TICKS, PHASES,
                () -> {
                    for (BlockPos centre : centres) {
                        forceIsland(level, centre, true);
                        BlockPos base = surface(level, centre);
                        population.addAll(BenchmarkWorld.spawnHostileCluster(
                                level, base, mobsPerIsland, clusterRadius,
                                0xA11E_0006L + base.getX()));
                    }
                    RegionizedTicking.setActive(true);
                    RegionizedTicking.setPartitioned(true);
                    RegionizedTicking.setBlockEntitySharding(false);
                    RegionizedTicking.setParallel(false);
                    SectionAb.install("ENTITY", live, (buckets, fannedOut) -> fannedOut);
                },
                new SectionAb.PhaseFlag() {
                    @Override
                    public void set(boolean on) {
                        RegionizedTicking.setParallel(on);
                    }

                    @Override
                    public void endOfPhase(boolean on) {
                        // Must be sampled here, not at the phase boundary: at a
                        // phase's START no section of that phase has run yet, so
                        // the probe still describes the phase before it - which
                        // reported "Server thread" everywhere for a parallel
                        // phase and tripped the fan-out guard.
                        if (on) {
                            threadProbe[0] = RegionizedTicking.lastEntityPartitionThreads();
                        }
                    }
                },
                serial, fanned, live,
                () -> {
                    for (int i = 0; i < islands; i++) {
                        regionIds[i] = regionIdAt(level, surface(level, centres.get(i)));
                    }
                    int survivors = (int) population.stream().filter(m -> !m.isRemoved()).count();
                    int expected = population.size();
                    String[] threads = threadProbe[0];
                    tearDown(level, centres, population);
                    verdict(helper, level, metricPrefix, control, islands, expected, survivors,
                            regionIds, threads, serial, fanned);
                });
    }

    private void verdict(GameTestHelper helper, ServerLevel level, String metricPrefix,
                         boolean control, int islands, int mobs, int survivors, long[] regionIds,
                         String[] threads, SectionAb.Reading serial, SectionAb.Reading fanned) {
        String inadmissible = SectionAb.inadmissible(serial, fanned, true);
        if (inadmissible != null) {
            helper.fail("Reading inadmissible: " + inadmissible);
            return;
        }
        if (survivors < mobs) {
            helper.fail((mobs - survivors) + " of " + mobs + " benchmark mobs died mid-run - the "
                    + "phases did not tick the same workload");
            return;
        }
        long distinctRegions = Arrays.stream(regionIds).distinct().count();
        if (distinctRegions != islands) {
            helper.fail(islands + " islands " + ISLAND_GAP_CHUNKS + " chunks apart resolved to "
                    + distinctRegions + " region(s) " + Arrays.toString(regionIds)
                    + " - the topology cannot express the partition this benchmark needs");
            return;
        }

        SectionSamples.Stats serialStats = serial.stats();
        SectionSamples.Stats fannedStats = fanned.stats();
        double ratio = serialStats.medianMillis() / fannedStats.medianMillis();
        double p95Ratio = fannedStats.p95Millis() > 0
                ? serialStats.p95Millis() / fannedStats.p95Millis() : 0.0;
        double serialMspt = serial.medianMspt();
        double fannedMspt = fanned.medianMspt();
        double msptRatio = fannedMspt > 0 ? serialMspt / fannedMspt : 0.0;
        int workers = threads == null ? 0 : (int) Arrays.stream(threads)
                .filter(t -> t != null && !t.equals("Server thread")).distinct().count();

        String shape = String.format(Locale.ROOT,
                "%d region(s), %d mobs, %d-%d buckets/section; %d+%d measured ticks "
                        + "(%d skipped/phase, %d phases interleaved)",
                islands, mobs, fanned.bucketsSeen(), fanned.maxBuckets,
                serialStats.count(), fannedStats.count(), SectionAb.SKIP_LEADING, PHASES);

        BenchRecorder.record(level.getServer(), metricPrefix + "_entity_section_serial", "ms/tick",
                serialStats.medianMillis(),
                String.format(Locale.ROOT,
                        "median entity-section wall time, partitionedTicking ON / parallelRegions "
                                + "OFF; p95 %.3f ms; %s", serialStats.p95Millis(), shape));
        BenchRecorder.record(level.getServer(), metricPrefix + "_entity_section_parallel",
                "ms/tick", fannedStats.medianMillis(),
                String.format(Locale.ROOT,
                        "median entity-section wall time, parallelRegions ON; p95 %.3f ms; %.2fx "
                                + "vs serial (p95 %.2fx); %d worker threads seen; %s",
                        fannedStats.p95Millis(), ratio, p95Ratio, workers, shape));
        BenchRecorder.record(level.getServer(), metricPrefix + "_mspt_serial", "ms/tick",
                serialMspt, "vanilla getAverageTickTimeNanos, parallelRegions OFF; " + shape);
        BenchRecorder.record(level.getServer(), metricPrefix + "_mspt_parallel", "ms/tick",
                fannedMspt,
                String.format(Locale.ROOT,
                        "vanilla getAverageTickTimeNanos, parallelRegions ON; %.2fx full-tick "
                                + "(Amdahl-bounded by the rest of the tick); %s",
                        msptRatio, shape));

        level.getServer().sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "[weft-bench] %s: entity section %.3f -> %.3f ms (%.2fx), p95 %.3f -> %.3f ms "
                        + "(%.2fx), MSPT %.3f -> %.3f ms (%.2fx), %d workers, %s",
                metricPrefix, serialStats.medianMillis(), fannedStats.medianMillis(), ratio,
                serialStats.p95Millis(), fannedStats.p95Millis(), p95Ratio,
                serialMspt, fannedMspt, msptRatio, workers, shape)));

        if (control) {
            if (fanned.engagedSections != 0) {
                helper.fail("The one-region control fanned out " + fanned.engagedSections
                        + " sections - it is not a control");
                return;
            }
            if (ratio > CONTROL_CEILING) {
                helper.fail(String.format(Locale.ROOT,
                        "NEGATIVE CONTROL FAILED: a one-region world reported %.2fx from a flag "
                                + "that cannot fan out there. The harness is measuring phase "
                                + "order, not parallelism, and the multi-region reading must be "
                                + "discarded with it (serial %.3f ms, 'parallel' %.3f ms)",
                        ratio, serialStats.medianMillis(), fannedStats.medianMillis()));
                return;
            }
            helper.succeed();
            return;
        }

        if (fanned.engagedSections < fanned.sections) {
            helper.fail("Only " + fanned.engagedSections + " of " + fanned.sections
                    + " measured sections fanned out - part of the 'parallel' phase ran serial "
                    + "and the ratio is unattributable");
            return;
        }
        if (fanned.bucketsSeen() < islands) {
            helper.fail("A measured section ran only " + fanned.bucketsSeen() + " buckets, not "
                    + islands + " - some island stopped ticking mid-run");
            return;
        }
        if (workers < 2) {
            helper.fail("Entity buckets ran on " + workers + " worker thread(s) "
                    + Arrays.toString(threads) + " - the section did not really fan out");
            return;
        }
        helper.succeed();
    }

    private static void forceIsland(ServerLevel level, BlockPos centre, boolean forced) {
        ChunkPos c = new ChunkPos(centre);
        for (int cx = c.x - ISLAND_CHUNK_RADIUS; cx <= c.x + ISLAND_CHUNK_RADIUS; cx++) {
            for (int cz = c.z - ISLAND_CHUNK_RADIUS; cz <= c.z + ISLAND_CHUNK_RADIUS; cz++) {
                level.setChunkForced(cx, cz, forced);
                if (forced) {
                    level.getChunk(cx, cz);
                }
            }
        }
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

    private static void tearDown(ServerLevel level, List<BlockPos> centres, List<Mob> population) {
        population.forEach(Entity::discard);
        for (BlockPos centre : centres) {
            forceIsland(level, centre, false);
        }
        RegionizedTicking.setActive(false);
        WeftConfig.PROFILING_ENABLED = true;
    }
}
