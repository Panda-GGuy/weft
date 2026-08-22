package dev.weft.neoforge.gametest;

import dev.weft.neoforge.activation.ActivationHooks;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * RFC-0006 <b>hazard 24</b> (issue #6): a worker block-entity tick must never
 * read an ABSENT neighbour chunk. {@code p2evictionchurn}, per
 * {@code .crew/memory/shared/BACKLOG.md} and {@code weft-parity/NOTES.md}.
 *
 * <h2>Why the four earlier designs failed</h2>
 *
 * <p>All four rejected designs (see {@code weft-parity/NOTES.md}, 2026-08-20)
 * put the read neighbour inside a {@link WeftBenchGameTests#forceChunks}
 * radius, released it, and observed it stay resident. The reason is structural
 * and applies to every one of them: {@code ChunkMap.prepareEntityTickingChunk}
 * generates a radius-2 {@code ChunkStatus.FULL} border around <em>any</em>
 * entity-ticking chunk (RFC-0006 hazard 22), and a forced grid's own radius-2
 * border regenerates itself from the grid's own ticket regardless of which
 * ticket you release — there is no "west neighbour" outside that guarantee
 * when the only ticket in the area is the grid's.
 *
 * <h2>What this design does differently</h2>
 *
 * <p>It uses <b>two ticket sources with independent lifetimes</b> instead of
 * one:
 * <ol>
 *   <li>An explicit {@code FORCED} (entity-ticking) ticket on exactly ONE
 *       chunk — the "east" chunk — holds the block entity's own chunk ticking
 *       for the whole test, unaffected by anything else.</li>
 *   <li>A {@link LoadBot} — a real fake-player join, ticketed the way an
 *       actual player is — sits in the "west" chunk (the read neighbour) just
 *       long enough to make it genuinely resident, satisfying the honest
 *       precondition every earlier design could not reach organically.</li>
 * </ol>
 *
 * <p>The bot then leaves. Its ticket on the west chunk is not renewed by
 * anything: the east chunk's own FORCED ticket radius-2 border reaches only as
 * far as chunks generated to {@code ChunkStatus.FULL} near it, which is a
 * <em>generation</em> guarantee (hazard 22's distinction) — it does not by
 * itself keep re-promoting a chunk to the visible/ticking map once nothing
 * requests that promotion. Placing the bot two chunks further west than the
 * boundary chest (rather than directly in the read chunk) additionally keeps
 * the read chunk from ever acquiring its own independent forced/player
 * ticket, so its only claim to residency was ever the bot's transient
 * presence.
 *
 * <p>A hopper+chest stack on the EAST chunk's westernmost block reads one
 * block west every time an item transfers ({@code setChanged} ->
 * {@code updateNeighbourForOutputSignal}), which is exactly the hazard-24
 * shape (a vault does the same read; a hopper is far easier to keep firing
 * every tick without waiting on a smelt/growth timer).
 *
 * <p><b>The test's real assertion is that nothing crashes.</b> Before hazard
 * 24's fix, this exact shape was a live-server crash
 * ({@code IllegalStateException}, RFC-0006 hazard 4). A GameTest that throws
 * fails the batch; one that completes with the region worker path engaged and
 * the hazard-24 counter having moved is the non-vacuous proof the counter
 * alone cannot be, because a counter that never fires and a gate that was
 * never reachable look identical from the counter's point of view.
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class EvictionChurnGameTests {

    private static final int SETTLE_TICKS = 10;
    /** Ticks the bot sits near both chunks before teleporting away — enough to fully promote both. */
    private static final int BOT_SETTLE_TICKS = 30;
    /** Ticks after the bot teleports away before engaging Weft — vanilla's own ticket
     *  release goes through an async throttler (DistanceManager.PlayerTicketTracker),
     *  so eviction is not synchronous with the teleport; give it real ticks to land. */
    private static final int UNLOAD_WAIT_TICKS = 40;
    private static final int RUN_TICKS = 100;
    /** Zombies in the far island, purely so the entity/BE sections fan out to >=2 buckets. */
    private static final int FAR_ISLAND_MOBS = 8;
    private static final int ISLAND_GAP_CHUNKS = 40;

    @GameTest(template = "empty", batch = "p2evictionchurn", timeoutTicks = 2000)
    public void boundaryBlockEntitySurvivesEvictedNeighbour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = WeftBenchGameTests.groundOrigin(helper);

        // The churn chunk (holds the boundary hopper/chest) — held ticking by
        // its OWN explicit FORCED ticket for the whole test, independent of
        // the bot.
        ChunkPos eastChunk = new ChunkPos(new BlockPos(ground.getX() - 64, 0, ground.getZ() - 64));
        // The read neighbour, one chunk west of the boundary block. Never
        // forced; its only claim to residency is the bot below.
        ChunkPos westChunk = new ChunkPos(eastChunk.x - 1, eastChunk.z);
        // The bot sits two chunks further west still, so its own load radius
        // does not spill a forced/entity-ticking ticket back onto the east
        // (churn) chunk and mask the very gap this test needs.
        ChunkPos botChunk = new ChunkPos(eastChunk.x - 3, eastChunk.z);

        // A second, distant island purely so the entity/BE sections have >=2
        // regions once Weft engages — otherwise parallel mode has nothing to
        // fan out and the readiness gate is never asked (see hazard 24's own
        // "gateReads = parallel" short-circuit).
        BlockPos farIsland = new BlockPos(eastChunk.getMinBlockX(), 0,
                eastChunk.getMinBlockZ() + ISLAND_GAP_CHUNKS * 16);

        level.setChunkForced(eastChunk.x, eastChunk.z, true);
        level.getChunk(eastChunk.x, eastChunk.z);
        WeftBenchGameTests.forceChunks(level, farIsland, true);

        ActivationHooks.setActive(false);
        PathfindingHooks.setActive(false);
        SpawnDensityHooks.setActive(false);
        LegacyRouting.setActive(false);

        // Boundary block-entity stack: chest above (source of items so
        // setChanged fires every transfer), hopper on the westernmost block
        // of the east chunk, chest below to receive. This is the hazard-24
        // shape from the live crash (a vault on a chunk's westernmost block),
        // simplified to a hopper so it fires every tick without a timer.
        int edgeX = eastChunk.getMinBlockX();
        int z = eastChunk.getMinBlockZ() + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, edgeX, z) + 1;
        level.setBlockAndUpdate(new BlockPos(edgeX, y - 1, z), Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(edgeX, y, z), Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(edgeX, y + 1, z), Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(new BlockPos(edgeX, y + 1, z)) instanceof ChestBlockEntity source) {
            source.setItem(0, new ItemStack(Items.STICK, 64));
        }

        List<Mob> farMobs = new ArrayList<>();
        long[] baseline = new long[2];
        LoadBot[] bot = new LoadBot[1];

        helper.runAfterDelay(1, () -> {
            for (int i = 0; i < FAR_ISLAND_MOBS; i++) {
                Mob mob = EntityType.ZOMBIE.create(level);
                if (mob == null) {
                    throw new IllegalStateException("could not create zombie");
                }
                BlockPos base = new BlockPos(farIsland.getX() + 4 + (i % 4), 0, farIsland.getZ() + 4);
                int my = level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
                mob.moveTo(base.getX() + 0.5, my, base.getZ() + 0.5, 0.0f, 0.0f);
                mob.setPersistenceRequired();
                if (!level.addFreshEntity(mob)) {
                    throw new IllegalStateException("level rejected zombie");
                }
                farMobs.add(mob);
            }
        });

        // Bot joins in the west chunk's neighbourhood (its own chunk, two
        // west of the churn boundary) — a real player-shaped ticket, not a
        // /forceload — and settles so the read-neighbour chunk becomes
        // genuinely, honestly resident before anything is asked to rely on it.
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BlockPos botPos = new BlockPos(botChunk.getMinBlockX() + 8, 0, botChunk.getMinBlockZ() + 8);
            bot[0] = LoadBot.join(level, botPos, "weft-eviction-churn-bot");
            bot[0].loadChunkAt(westChunk.x, westChunk.z);
        });

        // Confirm the honest precondition BEFORE touching any Weft flag:
        // the west (read) chunk must actually be loaded right now, or the
        // rest of this test proves nothing (same lesson as the four
        // rejected designs, applied to the setup side instead of the
        // teardown side).
        helper.runAfterDelay(SETTLE_TICKS + BOT_SETTLE_TICKS, () -> {
            if (level.getChunkSource().getChunkNow(westChunk.x, westChunk.z) == null) {
                helper.fail("west (read-neighbour) chunk was never resident even with the bot "
                        + "present - the setup itself is broken, not just the eviction");
                return;
            }
            // Bot leaves: its ticket on the west chunk is not renewed by
            // anything else in this arena, so the chunk has nothing left
            // keeping it in the visible/ticking chunk map.
            bot[0].leave();
        });

        // Give the server's own distance manager real ticks to actually
        // unload the chunk (this is a live gametest server; ticks advance
        // for real, no hand-stepped chunk source).
        helper.runAfterDelay(SETTLE_TICKS + BOT_SETTLE_TICKS + UNLOAD_WAIT_TICKS, () -> {
            boolean stillLoaded = level.getChunkSource().getChunkNow(westChunk.x, westChunk.z) != null;
            baseline[0] = RegionizedTicking.unreadyBlockEntityUnits();
            baseline[1] = RegionizedTicking.unmappedUnits();
            // Record whether eviction actually happened; asserted at the end
            // so a failure to evict is reported as its own honest failure
            // mode rather than masquerading as "the gate never fired".
            evictedHolder[0] = !stillLoaded;
            RegionizedTicking.setActive(true);
            RegionizedTicking.setPartitioned(true);
            RegionizedTicking.setParallel(true);
        });

        helper.runAfterDelay(SETTLE_TICKS + BOT_SETTLE_TICKS + UNLOAD_WAIT_TICKS + RUN_TICKS, () -> {
            long unreadyBe = RegionizedTicking.unreadyBlockEntityUnits() - baseline[0];
            long unmapped = RegionizedTicking.unmappedUnits() - baseline[1];
            long[] bePartition = RegionizedTicking.lastBlockEntityPartition();
            String[] beThreads = RegionizedTicking.lastBlockEntityPartitionThreads();
            long eastRegion = regionIdAt(level, new BlockPos(edgeX, 64, z));
            long farRegion = regionIdAt(level, farIsland);
            boolean evicted = evictedHolder[0];
            int aliveFar = (int) farMobs.stream().filter(m -> !m.isRemoved()).count();

            teardown(level, eastChunk, westChunk, botChunk, farIsland, farMobs, edgeX, z);

            if (!evicted) {
                helper.fail("west (read-neighbour) chunk was still resident after the bot left and "
                        + UNLOAD_WAIT_TICKS + " ticks - eviction did not actually happen, so this run "
                        + "proves nothing about hazard 24 (increase UNLOAD_WAIT_TICKS or investigate "
                        + "why the chunk is still held)");
                return;
            }
            if (eastRegion < 0 || farRegion < 0 || eastRegion == farRegion) {
                helper.fail("east/far chunks did not resolve to two distinct regions (east=" + eastRegion
                        + " far=" + farRegion + ") - the section would not fan out, so the readiness "
                        + "gate would never be consulted (gateReads = parallel short-circuits on <2 "
                        + "buckets)");
                return;
            }
            if (aliveFar < farMobs.size()) {
                helper.fail((farMobs.size() - aliveFar) + " of " + farMobs.size()
                        + " far-island zombies died - the run did not tick what it claims to have "
                        + "ticked");
                return;
            }
            if (unreadyBe < 1) {
                helper.fail("unreadyBlockEntityUnits did not move (delta=" + unreadyBe + ") - the "
                        + "boundary hopper's read into the evicted west chunk was not classified as "
                        + "unready, meaning hazard 24's readiness gate was not exercised by this run");
                return;
            }
            if (unmapped != 0) {
                helper.fail("unmappedUnits moved by " + unmapped + " - a hazard-24 deferral must be "
                        + "counted as unready, never as unmapped (that invariant must stay 0)");
                return;
            }
            // Fan-out sanity: the far island (whose neighbourhood is fully
            // live) should still reach a worker thread in at least one
            // section, proving the gate is selective rather than a
            // side effect that serialised everything.
            String serverThread = level.getServer().getRunningThread().getName();
            boolean farFannedOut = bePartition.length >= 2 && java.util.Arrays.stream(beThreads)
                    .anyMatch(t -> t != null && !t.equals(serverThread));
            if (!farFannedOut) {
                helper.fail("no block-entity bucket ran off the server thread (threads="
                        + java.util.Arrays.toString(beThreads) + ") - cannot tell whether the gate is "
                        + "selectively deferring the evicted unit or simply forcing everything serial");
                return;
            }
            // The overriding proof: reaching this line at all, with Weft
            // active and a genuinely evicted radius-1 neighbour in play,
            // means the mixin's fail-loud IllegalStateException (RFC-0006
            // hazard 4) never fired. Before hazard 24's fix this exact shape
            // crashed the batch.
            helper.succeed();
        });
    }

    private final boolean[] evictedHolder = new boolean[1];

    private static long regionIdAt(ServerLevel level, BlockPos pos) {
        var region = RegionTopology.managerFor(level).regionAtBlock(pos.getX(), pos.getZ());
        return region == null ? -1L : region.id();
    }

    private static void teardown(ServerLevel level, ChunkPos eastChunk, ChunkPos westChunk,
                                 ChunkPos botChunk, BlockPos farIsland, List<Mob> farMobs,
                                 int edgeX, int z) {
        RegionizedTicking.setActive(false);
        farMobs.forEach(m -> {
            if (!m.isRemoved()) {
                m.discard();
            }
        });
        level.setChunkForced(eastChunk.x, eastChunk.z, false);
        WeftBenchGameTests.forceChunks(level, farIsland, false);
    }
}
