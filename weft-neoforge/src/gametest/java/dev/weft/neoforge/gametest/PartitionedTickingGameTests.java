package dev.weft.neoforge.gametest;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * P2 increment-4 gate (RFC-0001 §4.2/§11): partitioned region ticking —
 * per-region buckets, canonical order, real region ids, still serial. Two
 * self-contained islands far enough apart to be distinct topology regions
 * run the same deterministic rig (fueled furnace + armor stand) twice:
 * inline (control) and partitioned, for identical tick counts.
 *
 * <p>Asserted:
 * <ol>
 *   <li><b>Independence-equivalence</b> — each island's end state (furnace
 *       block state + full NBT) is bit-identical between control and
 *       partitioned runs: regrouping execution by region must be
 *       unobservable for regions ≥ mergeDistance apart.</li>
 *   <li><b>Real partition</b> — the islands resolve to two distinct region
 *       ids, and both ids appear in the entity and block-entity partition
 *       probes of the final section: the buckets carried the topology's real
 *       ids, not the level-wide owner.</li>
 *   <li><b>Engagement + containment</b> — partitioned sections counted per
 *       tick (vacuous-run guard) and zero units leaked to the unmapped
 *       tail.</li>
 * </ol>
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class PartitionedTickingGameTests {

    private static final int SETTLE_TICKS = 10;
    /** >200 so each furnace produces output (a vacuous-arena guard in itself). */
    private static final int RUN_TICKS = 210;
    private static final int SIZE = 8;
    /** Island separation in chunks — far beyond mergeDistance (8). */
    private static final int ISLAND_GAP_CHUNKS = 40;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @GameTest(template = "empty", batch = "p2partition", timeoutTicks = 1600)
    public void partitionedTickingIndependentIslands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BlockPos columnA = new BlockPos(ground.getX() - 64, 0, ground.getZ() + 64);
        BlockPos columnB = new BlockPos(columnA.getX(), 0,
                columnA.getZ() + ISLAND_GAP_CHUNKS * 16);
        WeftBenchGameTests.forceChunks(level, columnA, true);
        WeftBenchGameTests.forceChunks(level, columnB, true);
        BlockPos baseA = surfaceBase(level, columnA);
        BlockPos baseB = surfaceBase(level, columnB);

        // Isolate partitioning as the only variable (RFC-0005 §3 discipline).
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);

        AtomicReference<String> controlA = new AtomicReference<>();
        AtomicReference<String> controlB = new AtomicReference<>();
        long[] baselines = new long[2];

        // Control window: inline vanilla ticking.
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            buildIsland(level, baseA);
            buildIsland(level, baseB);
        });

        // Control capture; rebuild; partitioned window.
        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS + 1, () -> {
            controlA.set(furnaceDigest(level, furnacePos(baseA)));
            controlB.set(furnaceDigest(level, furnacePos(baseB)));
            demolishIsland(level, baseA);
            demolishIsland(level, baseB);
            buildIsland(level, baseA);
            buildIsland(level, baseB);
            baselines[0] = RegionizedTicking.partitionedSections();
            baselines[1] = RegionizedTicking.unmappedUnits();
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
        });

        // Partitioned capture and the verdicts (partitioning still active
        // here, so the probes hold the final section's buckets).
        helper.runAfterDelay(SETTLE_TICKS + 2 * RUN_TICKS + 2, () -> {
            String laneA = furnaceDigest(level, furnacePos(baseA));
            String laneB = furnaceDigest(level, furnacePos(baseB));
            long regionA = regionIdAt(level, baseA);
            long regionB = regionIdAt(level, baseB);
            long[] entityPartition = RegionizedTicking.lastEntityPartition();
            long[] bePartition = RegionizedTicking.lastBlockEntityPartition();
            long partitionedSections = RegionizedTicking.partitionedSections() - baselines[0];
            long unmapped = RegionizedTicking.unmappedUnits() - baselines[1];
            tearDown(level, baseA, baseB);

            if (regionA < 0 || regionB < 0) {
                helper.fail("An island's chunk has no topology region: A=" + regionA
                        + " B=" + regionB + " (RFC-0001 §4.2 coverage invariant)");
            }
            if (regionA == regionB) {
                helper.fail("Islands " + ISLAND_GAP_CHUNKS + " chunks apart resolved to ONE "
                        + "region (" + regionA + ") - topology cannot express the partition "
                        + "this test must exercise");
            }
            if (!contains(entityPartition, regionA) || !contains(entityPartition, regionB)) {
                helper.fail("Entity partition probe missing an island region: probe="
                        + Arrays.toString(entityPartition) + " A=" + regionA + " B=" + regionB);
            }
            if (!contains(bePartition, regionA) || !contains(bePartition, regionB)) {
                helper.fail("Block-entity partition probe missing an island region: probe="
                        + Arrays.toString(bePartition) + " A=" + regionA + " B=" + regionB);
            }
            if (partitionedSections < 2L * (RUN_TICKS - 16)) {
                helper.fail("Vacuous partition run: only " + partitionedSections
                        + " partitioned sections across " + RUN_TICKS + " ticks");
            }
            if (unmapped != 0) {
                helper.fail("Partitioner leaked " + unmapped + " units into the unmapped tail");
            }
            if (!laneA.contains("minecraft:iron_ingot") || !laneB.contains("minecraft:iron_ingot")) {
                helper.fail("A partitioned furnace produced no output - the buckets did not "
                        + "really run.\nA: " + laneA + "\nB: " + laneB);
            }
            if (!laneA.equals(controlA.get())) {
                helper.fail("PARTITION EQUIVALENCE FAILURE on island A: regrouping by region "
                        + "changed an independent island's end state.\ncontrol: "
                        + controlA.get() + "\npartitioned: " + laneA);
            }
            if (!laneB.equals(controlB.get())) {
                helper.fail("PARTITION EQUIVALENCE FAILURE on island B.\ncontrol: "
                        + controlB.get() + "\npartitioned: " + laneB);
            }
            helper.succeed();
        });
    }

    /**
     * P2 increment-5 gate (RFC-0006, class E1): same two-island rig, but the
     * buckets run CONCURRENTLY on engine workers. Asserts everything the
     * partition gate asserts — distinct real region ids in both probes,
     * per-island end states bit-identical to an inline control at equal tick
     * counts, zero unmapped units — plus the fan-out proof: every bucket of
     * the final section executed off the server thread. Bit-identical end
     * states under real concurrency is the E1 claim made concrete: per-region
     * execution is untouched by parallelism; only cross-region interleaving
     * (unobservable here by construction) changed.
     */
    @GameTest(template = "empty", batch = "p2parallel", timeoutTicks = 1600)
    public void parallelTickingIndependentIslands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BlockPos columnA = new BlockPos(ground.getX() - 64, 0, ground.getZ() + 64);
        BlockPos columnB = new BlockPos(columnA.getX(), 0,
                columnA.getZ() + ISLAND_GAP_CHUNKS * 16);
        WeftBenchGameTests.forceChunks(level, columnA, true);
        WeftBenchGameTests.forceChunks(level, columnB, true);
        BlockPos baseA = surfaceBase(level, columnA);
        BlockPos baseB = surfaceBase(level, columnB);

        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);

        AtomicReference<String> controlA = new AtomicReference<>();
        AtomicReference<String> controlB = new AtomicReference<>();
        long[] baselines = new long[2];

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            buildIsland(level, baseA);
            buildIsland(level, baseB);
        });

        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS + 1, () -> {
            controlA.set(furnaceDigest(level, furnacePos(baseA)));
            controlB.set(furnaceDigest(level, furnacePos(baseB)));
            demolishIsland(level, baseA);
            demolishIsland(level, baseB);
            buildIsland(level, baseA);
            buildIsland(level, baseB);
            baselines[0] = RegionizedTicking.partitionedSections();
            baselines[1] = RegionizedTicking.unmappedUnits();
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setParallel(true);
        });

        helper.runAfterDelay(SETTLE_TICKS + 2 * RUN_TICKS + 2, () -> {
            String laneA = furnaceDigest(level, furnacePos(baseA));
            String laneB = furnaceDigest(level, furnacePos(baseB));
            long regionA = regionIdAt(level, baseA);
            long regionB = regionIdAt(level, baseB);
            long[] entityPartition = RegionizedTicking.lastEntityPartition();
            long[] bePartition = RegionizedTicking.lastBlockEntityPartition();
            String[] bucketThreads = RegionizedTicking.lastEntityPartitionThreads();
            String serverThread = level.getServer().getRunningThread().getName();
            long partitionedSections = RegionizedTicking.partitionedSections() - baselines[0];
            long unmapped = RegionizedTicking.unmappedUnits() - baselines[1];
            tearDown(level, baseA, baseB);

            if (regionA < 0 || regionB < 0 || regionA == regionB) {
                helper.fail("Island regions unusable for the parallel gate: A=" + regionA
                        + " B=" + regionB);
            }
            if (!contains(entityPartition, regionA) || !contains(entityPartition, regionB)
                    || !contains(bePartition, regionA) || !contains(bePartition, regionB)) {
                helper.fail("Partition probes missing an island region under parallel mode: "
                        + "entity=" + Arrays.toString(entityPartition)
                        + " be=" + Arrays.toString(bePartition)
                        + " A=" + regionA + " B=" + regionB);
            }
            // The fan-out proof: >=2 buckets, all executed on pool workers.
            if (bucketThreads.length < 2) {
                helper.fail("Parallel section had " + bucketThreads.length
                        + " buckets - fan-out never engaged");
            }
            for (String thread : bucketThreads) {
                if (thread == null || thread.equals(serverThread)) {
                    helper.fail("A parallel bucket ran on the server thread (" + thread
                            + ") - fan-out did not actually parallelize: "
                            + Arrays.toString(bucketThreads));
                }
            }
            if (partitionedSections < 2L * (RUN_TICKS - 16)) {
                helper.fail("Vacuous parallel run: only " + partitionedSections
                        + " partitioned sections across " + RUN_TICKS + " ticks");
            }
            if (unmapped != 0) {
                helper.fail("Partitioner leaked " + unmapped
                        + " units into the unmapped tail under parallel mode");
            }
            if (!laneA.contains("minecraft:iron_ingot") || !laneB.contains("minecraft:iron_ingot")) {
                helper.fail("A parallel furnace produced no output - the buckets did not really "
                        + "run.\nA: " + laneA + "\nB: " + laneB);
            }
            if (!laneA.equals(controlA.get())) {
                helper.fail("E1 EQUIVALENCE FAILURE on island A: concurrent bucket execution "
                        + "changed an independent island's end state.\ncontrol: "
                        + controlA.get() + "\nparallel: " + laneA);
            }
            if (!laneB.equals(controlB.get())) {
                helper.fail("E1 EQUIVALENCE FAILURE on island B.\ncontrol: "
                        + controlB.get() + "\nparallel: " + laneB);
            }
            helper.succeed();
        });
    }

    /**
     * Does increment 5 (parallel regions, already on main) survive block
     * entities that use NeoForge's <em>capability</em> path?
     *
     * <p>Motivation, and why this is not paranoia: the RFC-0008 sharding
     * attempt crashed with an NPE inside
     * {@code VanillaInventoryCodeHooks.extractHook} —
     * {@code ChestBlock.getContainer} returned null, i.e.
     * {@code level.getBlockEntity} answered null for a chest that exists,
     * on a worker thread. RFC-0006's audit enumerated vanilla structures
     * but never the modding platform's capability layer, and the existing
     * {@code p2parallel} gate cannot have caught it: its rig is furnaces and
     * armour stands, neither of which resolves an item-handler capability.
     * Hoppers do, every tick. If concurrent region buckets are enough to
     * reproduce it, the hazard is not sharding-specific and increment 5
     * needs an audit entry.
     */
    @GameTest(template = "empty", batch = "p2parallelcap", timeoutTicks = 1600)
    public void parallelRegionsWithCapabilityBlockEntities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BlockPos columnA = new BlockPos(ground.getX() - 96, 0, ground.getZ() + 96);
        BlockPos columnB = new BlockPos(columnA.getX(), 0,
                columnA.getZ() + ISLAND_GAP_CHUNKS * 16);
        WeftBenchGameTests.forceChunks(level, columnA, true);
        WeftBenchGameTests.forceChunks(level, columnB, true);
        BlockPos baseA = surfaceBase(level, columnA);
        BlockPos baseB = surfaceBase(level, columnB);

        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);

        int[] control = new int[2];

        // Control phase FIRST (RFC-0005 §3): the rig must work under plain
        // vanilla ticking before concurrency may be blamed for anything.
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            buildHopperBank(level, baseA);
            buildHopperBank(level, baseB);
        });

        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS, () -> {
            control[0] = hopperBankDelivered(level, baseA);
            control[1] = hopperBankDelivered(level, baseB);
            if (control[0] == 0 || control[1] == 0) {
                demolishHopperBank(level, baseA);
                demolishHopperBank(level, baseB);
                tearDown(level, baseA, baseB);
                helper.fail("Control failed: hoppers delivered nothing under VANILLA ticking (A="
                        + control[0] + " B=" + control[1] + ") - the rig is broken, so it cannot "
                        + "judge parallel execution");
            }
            demolishHopperBank(level, baseA);
            demolishHopperBank(level, baseB);
            buildHopperBank(level, baseA);
            buildHopperBank(level, baseB);
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setParallel(true);
        });

        helper.runAfterDelay(SETTLE_TICKS + 2 * RUN_TICKS, () -> {
            int movedA = hopperBankDelivered(level, baseA);
            int movedB = hopperBankDelivered(level, baseB);
            String[] threads = RegionizedTicking.lastEntityPartitionThreads();
            String serverThread = level.getServer().getRunningThread().getName();
            demolishHopperBank(level, baseA);
            demolishHopperBank(level, baseB);
            tearDown(level, baseA, baseB);

            // Reaching here at all is the headline result: no crash. Then the
            // usual engagement guard, so a serial fallback can't pass silently.
            boolean fannedOut = threads.length >= 2;
            for (String thread : threads) {
                if (thread == null || thread.equals(serverThread)) {
                    fannedOut = false;
                }
            }
            if (!fannedOut) {
                helper.fail("Parallel fan-out never engaged (" + Arrays.toString(threads)
                        + ") - this run did not actually test concurrent capability access");
            }
            if (movedA == 0 || movedB == 0) {
                helper.fail("Hoppers delivered nothing (A=" + movedA + " B=" + movedB
                        + ") - the capability path was never exercised");
            }
            helper.succeed();
        });
    }

    /**
     * Eight independent vertical hopper stacks: chest → hopper → chest.
     * Spaced three blocks apart on purpose — chests placed side by side pair
     * into <em>double</em> chests, which routes {@code ChestBlock.getContainer}
     * through {@code DoubleBlockCombiner} and makes a null container an
     * ordinary possibility rather than evidence of a race. The stacks must be
     * independent for this test to mean what it claims.
     */
    private static void buildHopperBank(ServerLevel level, BlockPos base) {
        for (int i = 0; i < 8; i++) {
            BlockPos column = base.offset(1 + i * 3, 1, 1);
            level.setBlock(column.below(1), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(column, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(column.above(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(column.above())
                    instanceof net.minecraft.world.Container source) {
                source.setItem(0, new ItemStack(Items.STICK, 48));
            }
        }
    }

    private static int hopperBankDelivered(ServerLevel level, BlockPos base) {
        int total = 0;
        for (int i = 0; i < 8; i++) {
            if (level.getBlockEntity(base.offset(1 + i * 3, 0, 1))
                    instanceof net.minecraft.world.Container dest) {
                for (int slot = 0; slot < dest.getContainerSize(); slot++) {
                    total += dest.getItem(slot).getCount();
                }
            }
        }
        return total;
    }

    private static void demolishHopperBank(ServerLevel level, BlockPos base) {
        for (int i = 0; i < 8; i++) {
            BlockPos column = base.offset(1 + i * 3, 1, 1);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos p = column.offset(0, dy, 0);
                if (level.getBlockEntity(p) instanceof net.minecraft.world.Container c) {
                    c.clearContent();
                }
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), DEMOLISH_FLAGS);
                }
            }
        }
    }

    private static BlockPos surfaceBase(ServerLevel level, BlockPos column) {
        return new BlockPos(column.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()),
                column.getZ());
    }

    private static BlockPos furnacePos(BlockPos base) {
        return base.offset(2, 0, 2);
    }

    private static long regionIdAt(ServerLevel level, BlockPos base) {
        var region = RegionTopology.managerFor(level).regionAtBlock(base.getX(), base.getZ());
        return region != null ? region.id() : -1;
    }

    private static boolean contains(long[] ids, long id) {
        for (long candidate : ids) {
            if (candidate == id) {
                return true;
            }
        }
        return false;
    }

    /** Same deterministic rig as the p2legacy gate: platform, furnace, stand. */
    private static void buildIsland(ServerLevel level, BlockPos base) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                level.setBlock(base.offset(x, -1, z),
                        Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos furnacePos = furnacePos(base);
        level.setBlock(furnacePos, Blocks.FURNACE.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(furnacePos) instanceof AbstractFurnaceBlockEntity furnace) {
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 8));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
        } else {
            throw new IllegalStateException("furnace block entity missing at " + furnacePos);
        }
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand == null) {
            throw new IllegalStateException("armor stand failed to create");
        }
        stand.moveTo(base.getX() + 5.5, base.getY(), base.getZ() + 5.5, 0.0f, 0.0f);
        level.addFreshEntity(stand);
    }

    private static String furnaceDigest(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return "<no block entity at " + pos + ">";
        }
        return level.getBlockState(pos) + " | "
                + be.saveWithoutMetadata(level.registryAccess());
    }

    private static void demolishIsland(ServerLevel level, BlockPos base) {
        level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(
                        base.getX() - 4, base.getY() - 4, base.getZ() - 4,
                        base.getX() + SIZE + 4, base.getY() + 8, base.getZ() + SIZE + 4),
                e -> !(e instanceof net.minecraft.world.entity.player.Player))
                .forEach(net.minecraft.world.entity.Entity::discard);
        for (int x = 0; x < SIZE; x++) {
            for (int y = -1; y < 4; y++) {
                for (int z = 0; z < SIZE; z++) {
                    BlockPos p = base.offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), DEMOLISH_FLAGS);
                    }
                }
            }
        }
    }

    private static void tearDown(ServerLevel level, BlockPos baseA, BlockPos baseB) {
        RegionizedTicking.setActive(false); // also clears partitioned
        demolishIsland(level, baseA);
        demolishIsland(level, baseB);
        for (BlockPos base : new BlockPos[] {baseA, baseB}) {
            for (int x = 0; x < SIZE; x++) {
                for (int z = 0; z < SIZE; z++) {
                    level.setBlock(base.offset(x, -1, z),
                            Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        WeftBenchGameTests.forceChunks(level, baseA, false);
        WeftBenchGameTests.forceChunks(level, baseB, false);
        // Later batches should see shipping/config-resolved module states.
        WeftModules.resolve();
    }
}
