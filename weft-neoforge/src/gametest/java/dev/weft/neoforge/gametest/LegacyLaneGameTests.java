package dev.weft.neoforge.gametest;

import dev.weft.api.CompatTier;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.legacy.LegacyRouting;
import dev.weft.neoforge.path.PathfindingHooks;
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

import java.util.concurrent.atomic.AtomicReference;

/**
 * P2 increment-3 gate (RFC-0001 §7.2): the legacy lane's load-bearing
 * contract, exercised end to end. The gametest environment ships no Tier-2
 * mods, so vanilla types are force-classified LEGACY for the run (test
 * override, cleared in teardown) — the extraction seams, the Phase-4
 * execution, and the accounting are all the real production paths.
 *
 * <p>What is asserted:
 * <ol>
 *   <li><b>Equivalence</b> — a furnace whose every tick ran through the lane
 *       reaches the bit-identical end state (block state + full NBT) of a
 *       control furnace ticked inline, once executed tick counts are equal.
 *       The lane shifts <em>where in the server tick</em> a unit runs (Phase
 *       4, between vanilla ticks), never how often or with what semantics.
 *       The tick-count bookkeeping: extraction is deactivated one tick before
 *       capture, so the last queued unit drains at the next tick head and the
 *       same tick's inline section makes up the difference — both runs have
 *       executed exactly RUN_TICKS+1 furnace ticks at capture.</li>
 *   <li><b>Engagement</b> (vacuous-run guard) — the deferral counters must
 *       show one extraction per tick per unit for both kinds (block entity
 *       and entity), so a silently-inert seam cannot pass.</li>
 *   <li><b>§7.2 execution context</b> — a probe submitted to the lane
 *       observes the LEGACY thread context on the server thread.</li>
 *   <li><b>Attribution</b> — the lane's per-mod ledger charged the source
 *       ("minecraft", the forced namespace) for the extracted units; the
 *       §9.1 "your tick is 61% mod X" number exists.</li>
 * </ol>
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class LegacyLaneGameTests {

    private static final int SETTLE_TICKS = 10;
    /** Ticks of lane-routed running; >200 so the furnace produces output. */
    private static final int RUN_TICKS = 210;
    /** Platform footprint. */
    private static final int SIZE = 8;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @GameTest(template = "empty", batch = "p2legacy", timeoutTicks = 1600)
    public void legacyLaneContract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = arenaColumn(helper);
        WeftBenchGameTests.forceChunks(level, column, true);
        BlockPos base = new BlockPos(column.getX(),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()),
                column.getZ());

        // Isolate the lane as the only variable (RFC-0005 §3 discipline).
        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        RegionizedTicking.setActive(false);
        LegacyRouting.setActive(false);

        BlockPos furnacePos = base.offset(2, 0, 2);
        AtomicReference<String> controlState = new AtomicReference<>();
        AtomicReference<String> probeContext = new AtomicReference<>();
        long[] baselines = new long[2];

        // Control window: build fresh, tick inline for RUN_TICKS+1.
        helper.runAfterDelay(SETTLE_TICKS, () -> build(level, base));

        // Control capture; then rebuild and route through the lane.
        helper.runAfterDelay(SETTLE_TICKS + RUN_TICKS + 1, () -> {
            controlState.set(furnaceDigest(level, furnacePos));
            demolish(level, base);
            build(level, base);
            LegacyRouting.forceTierForTest("minecraft:furnace", CompatTier.LEGACY);
            LegacyRouting.forceTierForTest("minecraft:armor_stand", CompatTier.LEGACY);
            baselines[0] = LegacyRouting.deferredBlockEntities();
            baselines[1] = LegacyRouting.deferredEntities();
            LegacyRouting.setActive(true);
        });

        // Stop extracting one tick before capture (the drain tick makes
        // executed-tick counts equal — see class note) and submit the
        // context probe; it runs in the next Phase 4, before the capture.
        helper.runAfterDelay(SETTLE_TICKS + 2 * RUN_TICKS + 1, () -> {
            LegacyRouting.setActive(false);
            LegacyRouting.lane().submit("weft-test-probe", () -> probeContext.set(
                    ThreadContext.current().kind() + "@"
                            + (Thread.currentThread() == level.getServer().getRunningThread()
                                    ? "server-thread" : Thread.currentThread().getName())));
        });

        // Lane capture and the verdicts.
        helper.runAfterDelay(SETTLE_TICKS + 2 * RUN_TICKS + 2, () -> {
            String laneState = furnaceDigest(level, furnacePos);
            long beDeferred = LegacyRouting.deferredBlockEntities() - baselines[0];
            long entityDeferred = LegacyRouting.deferredEntities() - baselines[1];
            Long minecraftUnits = LegacyRouting.lane().unitsByMod().get("minecraft");
            Long minecraftNanos = LegacyRouting.lane().costByModNanos().get("minecraft");
            tearDown(level, base);

            if (!LegacyRouting.hooksApplied()) {
                helper.fail("Legacy-lane extraction mixins did not apply - hooksApplied=false");
            }
            // Engagement: one extraction per tick per unit, both kinds.
            if (beDeferred < RUN_TICKS - 2 || beDeferred > RUN_TICKS + 10) {
                helper.fail("Block-entity extraction did not engage per-tick: " + beDeferred
                        + " deferrals across " + RUN_TICKS + " routed ticks");
            }
            if (entityDeferred < RUN_TICKS - 2 || entityDeferred > RUN_TICKS + 10) {
                helper.fail("Entity extraction did not engage per-tick: " + entityDeferred
                        + " deferrals across " + RUN_TICKS + " routed ticks");
            }
            // §7.2 execution context.
            if (probeContext.get() == null) {
                helper.fail("Lane probe never executed - Phase 4 is not draining the lane");
            }
            if (!"LEGACY@server-thread".equals(probeContext.get())) {
                helper.fail("Lane work ran outside the §7.2 contract: " + probeContext.get()
                        + " (expected LEGACY@server-thread)");
            }
            // Attribution.
            if (minecraftUnits == null || minecraftUnits < 2L * RUN_TICKS - 4
                    || minecraftNanos == null || minecraftNanos <= 0) {
                helper.fail("Per-mod attribution missing: minecraft units=" + minecraftUnits
                        + " nanos=" + minecraftNanos);
            }
            // Liveliness: the lane-run furnace actually produced.
            if (!laneState.contains("minecraft:iron_ingot")) {
                helper.fail("Lane-routed furnace produced no output in " + RUN_TICKS
                        + " ticks - the deferred units did not really run: " + laneState);
            }
            // Equivalence.
            if (!laneState.equals(controlState.get())) {
                helper.fail("LEGACY-LANE EQUIVALENCE FAILURE: lane-routed furnace diverged from "
                        + "the inline control at equal executed-tick counts.\ncontrol: "
                        + controlState.get() + "\nlane:    " + laneState);
            }
            helper.succeed();
        });
    }

    /** Platform, one fueled furnace, one armor stand — self-contained. */
    private static void build(ServerLevel level, BlockPos base) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                level.setBlock(base.offset(x, -1, z),
                        Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        BlockPos furnacePos = base.offset(2, 0, 2);
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

    /** Block state + full NBT: the exact-equality unit for the furnace. */
    private static String furnaceDigest(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return "<no block entity at " + pos + ">";
        }
        return level.getBlockState(pos) + " | "
                + be.saveWithoutMetadata(level.registryAccess());
    }

    private static void demolish(ServerLevel level, BlockPos base) {
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

    private static void tearDown(ServerLevel level, BlockPos base) {
        LegacyRouting.setActive(false);
        LegacyRouting.clearTestOverrides();
        demolish(level, base);
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                level.setBlock(base.offset(x, -1, z),
                        Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        WeftBenchGameTests.forceChunks(level, base, false);
        // Later batches should see shipping/config-resolved module states.
        WeftModules.resolve();
    }

    /** Own quadrant, away from the parity arena (+64/+64) and the plot. */
    private static BlockPos arenaColumn(GameTestHelper helper) {
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);
        return new BlockPos(ground.getX() + 64, 0, ground.getZ() - 64);
    }
}
