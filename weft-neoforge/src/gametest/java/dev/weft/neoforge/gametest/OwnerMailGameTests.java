package dev.weft.neoforge.gametest;

import dev.weft.engine.guard.ThreadContext;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.path.PathfindingHooks;
import dev.weft.neoforge.regiontick.OwnerMail;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicReference;

/**
 * P2 increment-6 gate (RFC-0007 §3): owner-mail rerouting. Two islands far
 * enough apart to be distinct topology regions; positional tasks posted for
 * island A must be delivered through island A's own region mailbox and
 * executed by island A's bucket — under its REGION context, off the server
 * thread when fan-out is engaged — never by island B, never at global
 * INGEST. Unmapped targets must take the inline fallback; deactivation must
 * flush queued mail instead of stranding it.
 *
 * <p>This is the engagement gate the parity anchor cannot be (the parity
 * arena has no positional mail traffic, so the flag engages vacuously
 * there); here every delivery path is driven and counted.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class OwnerMailGameTests {

    private static final int SETTLE_TICKS = 10;
    /** Ticks per probe window: enough for several sections after a post. */
    private static final int WINDOW = 20;
    private static final int SIZE = 8;
    /** Island separation in chunks — far beyond mergeDistance (8). */
    private static final int ISLAND_GAP_CHUNKS = 40;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** What a probe task observed at execution time. */
    private record Execution(String thread, ThreadContext.Kind kind, long ownerId) {}

    @GameTest(template = "empty", batch = "p2mail", timeoutTicks = 1600)
    public void ownerMailRoutesToOwningRegionBucket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        BlockPos columnA = new BlockPos(ground.getX() - 64, 0, ground.getZ() + 64);
        BlockPos columnB = new BlockPos(columnA.getX(), 0,
                columnA.getZ() + ISLAND_GAP_CHUNKS * 16);
        WeftBenchGameTests.forceChunks(level, columnA, true);
        WeftBenchGameTests.forceChunks(level, columnB, true);
        BlockPos baseA = surfaceBase(level, columnA);
        BlockPos baseB = surfaceBase(level, columnB);

        // Isolate routing as the only variable (RFC-0005 §3 discipline).
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);

        AtomicReference<Execution> parallelProbe = new AtomicReference<>();
        AtomicReference<Execution> serialProbe = new AtomicReference<>();
        AtomicReference<Execution> fallbackProbe = new AtomicReference<>();
        AtomicReference<Execution> flushProbe = new AtomicReference<>();
        long[] baselines = new long[4];
        long[] regionIds = new long[2];

        // Build both islands; the rigs keep both sections non-empty so both
        // regions produce a bucket every tick.
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            buildIsland(level, baseA);
            buildIsland(level, baseB);
        });

        // Activate the full stack (parallel fan-out) and post the first probe:
        // a positional task for island A, posted exactly the way a WS-2 result
        // arrives — from INGEST-equivalent server-thread code outside any
        // section (gametest callbacks run in the server tick, not in a
        // section).
        helper.runAfterDelay(SETTLE_TICKS + WINDOW, () -> {
            regionIds[0] = regionIdAt(level, baseA);
            regionIds[1] = regionIdAt(level, baseB);
            if (regionIds[0] < 0 || regionIds[1] < 0 || regionIds[0] == regionIds[1]) {
                tearDown(level, baseA, baseB);
                helper.fail("Island regions unusable for the mail gate: A=" + regionIds[0]
                        + " B=" + regionIds[1] + " (need two distinct mapped regions)");
            }
            baselines[0] = OwnerMail.routedToRegion();
            baselines[1] = OwnerMail.drainedTasks();
            baselines[2] = OwnerMail.inlineFallback();
            baselines[3] = OwnerMail.flushedTasks();
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setParallel(true);
            RegionizedTicking.setMailRouting(true);
            OwnerMail.runOwned(level, furnacePos(baseA),
                    () -> parallelProbe.set(capture()));
        });

        // Parallel probe must have run by now. Switch to partitioned-serial
        // and post a probe for island B; also drive the unmapped fallback.
        helper.runAfterDelay(SETTLE_TICKS + 2 * WINDOW, () -> {
            RegionizedTicking.setParallel(false);
            OwnerMail.runOwned(level, furnacePos(baseB),
                    () -> serialProbe.set(capture()));
            // A position no chunk maps: far outside both forced islands.
            OwnerMail.runOwned(level, new BlockPos(100_000, 64, 100_000),
                    () -> fallbackProbe.set(capture()));
        });

        // Serial probe delivered. Queue one more for island A, then flip
        // routing OFF in the same callback: the transition must flush it
        // inline, immediately, on this thread (RFC-0007 §3.3 hazard 5).
        helper.runAfterDelay(SETTLE_TICKS + 3 * WINDOW, () -> {
            OwnerMail.runOwned(level, furnacePos(baseA),
                    () -> flushProbe.set(capture()));
            RegionizedTicking.setMailRouting(false);
        });

        // Verdicts.
        helper.runAfterDelay(SETTLE_TICKS + 4 * WINDOW, () -> {
            long routed = OwnerMail.routedToRegion() - baselines[0];
            long drained = OwnerMail.drainedTasks() - baselines[1];
            long fallback = OwnerMail.inlineFallback() - baselines[2];
            long flushed = OwnerMail.flushedTasks() - baselines[3];
            String serverThread = level.getServer().getRunningThread().getName();
            Execution parallel = parallelProbe.get();
            Execution serial = serialProbe.get();
            Execution unmapped = fallbackProbe.get();
            Execution flush = flushProbe.get();
            tearDown(level, baseA, baseB);

            if (parallel == null || serial == null || unmapped == null || flush == null) {
                helper.fail("A probe task never executed: parallel=" + parallel + " serial="
                        + serial + " fallback=" + unmapped + " flush=" + flush
                        + " - mail was stranded (RFC-0007 §3.3)");
            }
            // Delivery through the owner's bucket: REGION context, owner id A,
            // and off the server thread while fan-out was engaged.
            if (parallel.kind() != ThreadContext.Kind.REGION
                    || parallel.ownerId() != regionIds[0]) {
                helper.fail("Parallel-mode delivery ran outside island A's REGION context: "
                        + parallel + " (expected owner " + regionIds[0] + ")");
            }
            if (parallel.thread().equals(serverThread)) {
                helper.fail("Parallel-mode delivery ran on the server thread - mail was not "
                        + "drained by the region's own bucket: " + parallel);
            }
            if (serial.kind() != ThreadContext.Kind.REGION
                    || serial.ownerId() != regionIds[1]) {
                helper.fail("Serial-mode delivery ran outside island B's REGION context: "
                        + serial + " (expected owner " + regionIds[1] + ")");
            }
            if (!serial.thread().equals(serverThread)) {
                helper.fail("Partitioned-serial delivery left the server thread: " + serial);
            }
            // The unmapped target falls back inline at post time: the posting
            // callback runs on the server thread with no owner context.
            if (!unmapped.thread().equals(serverThread)
                    || unmapped.kind() == ThreadContext.Kind.REGION) {
                helper.fail("Unmapped-target fallback did not run inline on the server thread: "
                        + unmapped);
            }
            // The deactivation flush also runs inline on the server thread.
            if (!flush.thread().equals(serverThread)) {
                helper.fail("Deactivation flush left the server thread: " + flush);
            }
            if (routed != 3) {
                helper.fail("Expected exactly 3 tasks routed to region mailboxes, counted "
                        + routed);
            }
            if (drained != 2) {
                helper.fail("Expected exactly 2 tasks executed by bucket-head drains, counted "
                        + drained + " (the third must go to the flush, not a drain)");
            }
            if (fallback != 1) {
                helper.fail("Expected exactly 1 inline fallback (unmapped target), counted "
                        + fallback);
            }
            if (flushed != 1) {
                helper.fail("Expected exactly 1 task recovered by the deactivation flush, "
                        + "counted " + flushed);
            }
            helper.succeed();
        });
    }

    private static Execution capture() {
        ThreadContext ctx = ThreadContext.current();
        return new Execution(Thread.currentThread().getName(), ctx.kind(), ctx.ownerId());
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

    /** Same deterministic rig as the p2partition gate: platform, furnace, stand. */
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
        RegionizedTicking.setActive(false); // also clears partitioned + routing
        demolishIsland(level, baseA);
        demolishIsland(level, baseB);
        WeftBenchGameTests.forceChunks(level, baseA, false);
        WeftBenchGameTests.forceChunks(level, baseB, false);
        // Later batches should see shipping/config-resolved module states.
        WeftModules.resolve();
    }
}
