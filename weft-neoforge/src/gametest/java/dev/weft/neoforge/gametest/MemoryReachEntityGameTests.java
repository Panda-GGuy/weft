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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC-0006 <b>hazard 25</b>: {@code Brain}-based mobs must never be handed to a
 * region worker.
 *
 * <p>Found by the live soak (TESTING-0001 §2.3), fifteen minutes in, on the run
 * that first combined real mods with Brain mobs and chunk churn:
 *
 * <pre>
 *   requires chunk [-65, 22] ... and no generated FULL view exists either
 *     at Level.getBlockState
 *     at SleepInBed.checkExtraStartConditions
 *     at Brain.startEachNonRunningBehavior
 *     at RegionizedTicking.tickEntitySection
 * </pre>
 *
 * <p>A villager testing whether it can sleep reads the block at its
 * <em>remembered</em> bed, which is wherever it last slept — arbitrarily far, in
 * a chunk that may since have unloaded. Hazard 24's readiness gate cannot help:
 * that gate proves a radius-1 neighbourhood is live, which is the right bound for
 * a block entity's neighbour-signal path and no bound at all on a memory lookup.
 *
 * <p>So the guard is categorical rather than spatial, and this gate pins the
 * category. It asserts the <em>mechanism</em> (memory-reach types are counted as
 * deferred, non-memory-reach types are not) rather than trying to stage an
 * unloaded remembered bed, because the mechanism is what a future refactor would
 * break and the staging would be fragile.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class MemoryReachEntityGameTests {

    private static final int SETTLE_TICKS = 10;
    private static final int RUN_TICKS = 60;
    private static final int ISLAND_GAP_CHUNKS = 40;
    private static final int VILLAGERS = 12;
    private static final int ZOMBIES = 12;

    @GameTest(template = "empty", batch = "p2memoryreach", timeoutTicks = 1200)
    public void brainMobsNeverReachAWorker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        // Two regions so the entity section actually fans out; without that the
        // gate is untested because everything runs on the server thread anyway.
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

        List<Mob> mobs = new ArrayList<>();
        long[] baseline = new long[2];

        helper.runAfterDelay(1, () -> {
            // Zombies in region A: goal-based AI, no position memories, must bucket.
            spawn(level, baseA, EntityType.ZOMBIE, ZOMBIES, mobs);
            // Villagers in region B: Brain with HOME/JOB_SITE, must NOT bucket.
            spawn(level, baseB, EntityType.VILLAGER, VILLAGERS, mobs);
            baseline[0] = RegionizedTicking.unreadyUnits();
            baseline[1] = RegionizedTicking.unmappedUnits();
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setParallel(true);
        });

        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS, () -> {
            long deferred = RegionizedTicking.unreadyUnits() - baseline[0];
            long unmapped = RegionizedTicking.unmappedUnits() - baseline[1];
            long[] partition = RegionizedTicking.lastEntityPartition();
            long regionA = regionIdAt(level, baseA);
            long regionB = regionIdAt(level, baseB);
            int alive = (int) mobs.stream().filter(m -> !m.isRemoved()).count();
            tearDown(level, columnA, columnB, mobs);

            if (regionA == regionB || regionA < 0 || regionB < 0) {
                helper.fail("Islands did not resolve to two regions (A=" + regionA + " B="
                        + regionB + ") - the section would not fan out, so this proves nothing");
                return;
            }
            if (partition.length < 2) {
                helper.fail("Entity section ran " + partition.length + " bucket(s); with mobs in "
                        + "both islands it must fan out or the gate is untested");
                return;
            }
            if (alive < mobs.size()) {
                helper.fail((mobs.size() - alive) + " of " + mobs.size() + " mobs died - the run "
                        + "did not tick what it claims to have ticked");
                return;
            }
            // The assertion: villagers were deferred, every tick, both islands
            // ticking. RUN_TICKS sections x VILLAGERS is the floor, less slack for
            // the ticks around activation.
            long floor = (long) VILLAGERS * (RUN_TICKS - 20);
            if (deferred < floor) {
                helper.fail("Only " + deferred + " units deferred, expected at least " + floor
                        + " (" + VILLAGERS + " villagers x ~" + (RUN_TICKS - 20) + " sections). "
                        + "Brain mobs are reaching region workers, which is hazard 25: a "
                        + "villager's SleepInBed reads its REMEMBERED bed at arbitrary range, "
                        + "so no radius check can make it safe on a worker");
                return;
            }
            if (unmapped != 0) {
                helper.fail("unmapped units moved by " + unmapped + " - hazard-25 deferrals must "
                        + "be counted as unready, never as unmapped (that invariant must stay 0)");
                return;
            }
            helper.succeed();
        });
    }

    /** Hazard 21: a real villager/door navigation invalidation must defer and drain on main. */
    @GameTest(template = "empty", batch = "p2navdefer", timeoutTicks = 1200)
    public void villagerDoorNavigationUpdateDefersAfterParallelJoin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
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

        List<Mob> mobs = new ArrayList<>();
        long[] baseline = new long[4];
        BlockPos lowerDoor = baseB.offset(8, 1, 8);
        helper.runAfterDelay(1, () -> {
            spawn(level, baseA, EntityType.ZOMBIE, ZOMBIES, mobs);
            spawn(level, baseB, EntityType.VILLAGER, VILLAGERS, mobs);
            for (Mob mob : mobs) {
                PathNavigation navigation = mob.getNavigation();
                navigation.moveTo(lowerDoor.getX() + 0.5, lowerDoor.getY(),
                        lowerDoor.getZ() + 0.5, 1.0);
            }
            level.setBlock(lowerDoor.below(), Blocks.SMOOTH_STONE.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            level.setBlock(lowerDoor, Blocks.OAK_DOOR.defaultBlockState(),
                    Block.UPDATE_CLIENTS);
            baseline[0] = RegionizedTicking.deferredNavigationUpdates();
            baseline[1] = RegionizedTicking.completedNavigationUpdates();
            baseline[2] = RegionizedTicking.misplacedNavigationUpdates();
            baseline[3] = RegionizedTicking.unreadyUnits();
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setParallel(true);
        });

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            if (!RegionizedTicking.runNavigationUpdateProbe(level, () ->
                    level.sendBlockUpdated(lowerDoor, Blocks.OAK_DOOR.defaultBlockState(),
                            Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))) {
                helper.fail("Navigation deferral probe could not engage parallel mode");
                return;
            }
        });

        helper.runAfterDelay(SETTLE_TICKS + 8, () -> {
            long deferred = RegionizedTicking.deferredNavigationUpdates() - baseline[0];
            long completed = RegionizedTicking.completedNavigationUpdates() - baseline[1];
            long misplaced = RegionizedTicking.misplacedNavigationUpdates() - baseline[2];
            long serialVillagerTicks = RegionizedTicking.unreadyUnits() - baseline[3];
            String[] threads = RegionizedTicking.lastEntityPartitionThreads();
            String serverThread = level.getServer().getRunningThread().getName();
            long regionA = regionIdAt(level, baseA);
            long regionB = regionIdAt(level, baseB);
            tearDown(level, columnA, columnB, mobs);

            if (regionA < 0 || regionB < 0 || regionA == regionB || threads.length < 2
                    || java.util.Arrays.stream(threads)
                    .anyMatch(thread -> thread == null || thread.equals(serverThread))) {
                helper.fail("Navigation deferral gate never engaged two-region fan-out: A="
                        + regionA + " B=" + regionB + " threads="
                        + java.util.Arrays.toString(threads));
                return;
            }
            if (deferred < 1 || completed != deferred) {
                helper.fail("Door update did not traverse section-end deferral: deferred="
                        + deferred + " completed=" + completed);
                return;
            }
            if (misplaced != 0) {
                helper.fail(misplaced + " deferred navigation updates ran off server thread");
                return;
            }
            if (serialVillagerTicks < VILLAGERS) {
                helper.fail("Villager/Brain serial tail did not engage while worker fan-out ran: "
                        + serialVillagerTicks);
                return;
            }
            helper.succeed();
        });
    }

    private static void spawn(ServerLevel level, BlockPos base, EntityType<? extends Mob> type,
                             int count, List<Mob> out) {
        for (int i = 0; i < count; i++) {
            Mob mob = type.create(level);
            if (mob == null) {
                throw new IllegalStateException("could not create " + type);
            }
            mob.moveTo(base.getX() + 4 + (i % 6), base.getY() + 1, base.getZ() + 4 + (i / 6),
                    0.0f, 0.0f);
            mob.setPersistenceRequired();
            if (!level.addFreshEntity(mob)) {
                throw new IllegalStateException("level rejected " + type);
            }
            out.add(mob);
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

    private static void tearDown(ServerLevel level, BlockPos columnA, BlockPos columnB,
                                 List<Mob> mobs) {
        RegionizedTicking.setActive(false);
        mobs.forEach(Entity::discard);
        WeftBenchGameTests.forceChunks(level, columnA, false);
        WeftBenchGameTests.forceChunks(level, columnB, false);
        WeftModules.resolve();
    }
}
