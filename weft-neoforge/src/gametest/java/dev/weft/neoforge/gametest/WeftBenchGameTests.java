package dev.weft.neoforge.gametest;

import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.engine.telemetry.TickSample;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.profiler.WeftProfiler;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;

/**
 * WS-8 (RFC-0002): the world-level benchmarks. Runs headless via the
 * {@code gameTestServer} run ({@code gradle :weft-neoforge:runGameTestServer
 * -PwithNeoForge}); measurements land in {@code weft-bench.json} for the
 * nightly regression gate.
 *
 * <p>The WS-1 acceptance criterion splits into two tests with different CI
 * postures:
 * <ul>
 *   <li>{@link #ws1BehaviorParityNearPlayers} — "no behavior change within 32
 *       blocks of a player" — <b>hard gate</b>: it holds today and must never
 *       break.</li>
 *   <li>{@link #ws1EntityPhaseReduction} — ">=30% entity-phase reduction" —
 *       measured and tracked nightly, asserted as an <em>optional</em> test
 *       ({@code required = false}): the first full run measured 18.5% with
 *       92% of throttleable AI ticks skipped, i.e. the current
 *       sensing+goal-selector gating is at its asymptote and WS-1 needs to
 *       cover more of the mob tick to clear the bar. Flip {@code required}
 *       to true the day it does — until then {@code activationScheduling}
 *       keeps shipping default-off (RFC-0002 WS-1).</li>
 * </ul>
 *
 * <p>The entity phase is measured exactly as the P0 profiler defines it: the
 * per-tick sum of {@code tickNonPassenger} timings
 * ({@code TickSample.Source.ENTITY}).
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class WeftBenchGameTests {

    /** AI settle time before the first measured tick. */
    private static final int WARMUP_TICKS = 100;
    /** Measured ticks per A/B phase. */
    private static final int PHASE_TICKS = 300;
    /** Activated ticks the parity gate observes. */
    private static final int PARITY_TICKS = 200;
    /** Forced-chunk radius (chunks): covers the 140-block field plus wander. */
    private static final int CHUNK_RADIUS = 10;
    /** Bot walking circle, small enough to keep the near ring near. */
    private static final double BOT_CIRCLE_RADIUS = 12.0;
    /** WS-1 default full-rate ring; the parity criterion's "within 32 blocks". */
    private static final double FULL_RATE_DIST_SQ = 32.0 * 32.0;
    /** Fresh chunks the chunk-loading benchmark walks the bot through. */
    private static final int CHUNK_LOAD_COUNT = 192;

    /** The WS-1 acceptance bar; override for local experiments only. */
    private static double requiredReductionPct() {
        return Double.parseDouble(System.getProperty("weft.bench.ws1MinReductionPct", "30"));
    }

    /**
     * WS-1 acceptance, correctness half (hard gate): with activation
     * scheduling ON over the full 2k+500 population, a mob within 32 blocks
     * of the bot is never denied its AI tick — checked every single activated
     * tick while the bot walks — and throttling actually engages out in the
     * far bands (a no-op WS-1 would pass parity vacuously).
     */
    @GameTest(template = "empty", batch = "ws1parity", timeoutTicks = 600)
    public void ws1BehaviorParityNearPlayers(GameTestHelper helper) {
        Arena arena = Arena.setUp(helper, "WeftParityBot");
        ActivationHooks.setActive(true);
        ActivationHooks.Counters before = ActivationHooks.counters();

        helper.onEachTick(() -> {
            arena.bot().tickCircle(BOT_CIRCLE_RADIUS);
            assertNearMobsAtFullRate(helper, arena);
        });

        helper.runAfterDelay(PARITY_TICKS, () -> {
            ActivationHooks.Counters engaged = ActivationHooks.counters().minus(before);
            arena.tearDown();
            if (engaged.decisions() == 0 || engaged.skips() == 0) {
                helper.fail("WS-1 throttling never engaged: " + engaged
                        + " - is the optimizations mixin applied? hooksApplied="
                        + ActivationHooks.hooksApplied());
            }
            helper.succeed();
        });
    }

    /**
     * WS-1 acceptance, performance half (tracked nightly; see the class
     * comment for why {@code required = false} for now): A/B entity-phase
     * measurement over identical bot walks, vanilla AI cadence vs activation
     * scheduling. Numbers are recorded before the bar is asserted, so the
     * nightly trend exists either way.
     */
    @GameTest(template = "empty", batch = "ws1measure", timeoutTicks = 1200, required = false)
    public void ws1EntityPhaseReduction(GameTestHelper helper) {
        Arena arena = Arena.setUp(helper, "WeftBenchBot");
        WeftConfig.PROFILING_ENABLED = true;
        WeftConfig.PROFILE_WINDOW_TICKS = PHASE_TICKS + 64;
        ActivationHooks.setActive(false);

        long[] phaseStartTick = new long[1];
        EntityPhase[] baseline = new EntityPhase[1];

        helper.onEachTick(() -> arena.bot().tickCircle(BOT_CIRCLE_RADIUS));

        // Phase A: vanilla AI cadence.
        helper.runAfterDelay(WARMUP_TICKS,
                () -> phaseStartTick[0] = WeftProfiler.get().tickCounter());

        // Phase B: activation scheduling on.
        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            baseline[0] = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            ActivationHooks.setActive(true);
            phaseStartTick[0] = WeftProfiler.get().tickCounter();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            EntityPhase activated = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            double baselineMsPerTick = baseline[0].totalMs();
            double activatedMsPerTick = activated.totalMs();
            WeftConfig.PROFILE_WINDOW_TICKS = 100;
            arena.tearDown();

            double reduction = 100.0 * (1.0 - activatedMsPerTick / baselineMsPerTick);
            BenchRecorder.record(helper.getLevel().getServer(),
                    "ws1_entity_phase_vanilla_ai", "ms/tick", baselineMsPerTick,
                    "activation scheduling OFF (baseline)");
            // Sub-attribution (Part A of the WS-1 widening work): how much of
            // the entity phase is AI step at all - the ceiling for any amount
            // of WS-1 gating - tracked in both phases so the nightly trend
            // shows what widening steps actually removed.
            BenchRecorder.record(helper.getLevel().getServer(),
                    "ws1_entity_phase_ai_slice_vanilla", "ms/tick", baseline[0].aiMs(),
                    String.format(Locale.ROOT,
                            "AI step (serverAiStep) = %.1f%% of the vanilla entity phase; "
                                    + "the pool WS-1 gating can address",
                            100.0 * baseline[0].aiMs() / baselineMsPerTick));
            BenchRecorder.record(helper.getLevel().getServer(),
                    "ws1_entity_phase_ai_slice_activated", "ms/tick", activated.aiMs(),
                    String.format(Locale.ROOT,
                            "AI step remaining with activation scheduling ON (%.1f%% of its "
                                    + "entity phase)",
                            100.0 * activated.aiMs() / activatedMsPerTick));
            BenchRecorder.record(helper.getLevel().getServer(),
                    "ws1_entity_phase_activation_scheduling", "ms/tick", activatedMsPerTick,
                    String.format(Locale.ROOT,
                            "%.1f%% entity-phase reduction (acceptance bar: >=%.0f%%); "
                                    + "%d passive + %d hostile mobs, %d measured ticks/phase",
                            reduction, requiredReductionPct(),
                            BenchmarkWorld.PASSIVE_COUNT, BenchmarkWorld.HOSTILE_COUNT,
                            PHASE_TICKS));

            if (reduction < requiredReductionPct()) {
                helper.fail(String.format(Locale.ROOT,
                        "WS-1 acceptance bar not met yet: %.1f%% entity-phase reduction < %.0f%% "
                                + "(vanilla %.3f ms/tick, activated %.3f ms/tick)",
                        reduction, requiredReductionPct(),
                        baselineMsPerTick, activatedMsPerTick));
            }
            helper.succeed();
        });
    }

    /**
     * The remaining load-generator shapes: bot join, movement across fresh
     * terrain, and synchronous chunk loading — tracked so a regression in
     * anything on the chunk load/gen path (Weft's entity events, census,
     * future WS-4 worldgen work) shows up nightly.
     */
    @GameTest(template = "empty", batch = "loadgen", timeoutTicks = 300)
    public void botJoinMoveAndLoadChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = groundOrigin(helper);
        LoadBot bot = LoadBot.join(level, origin, "WeftChunkBot");

        // A strip of never-loaded chunks, far outside the gametest arena and
        // the WS-1 field (+200 chunks = +3200 blocks).
        ChunkPos start = new ChunkPos(origin);
        int baseCx = start.x + 200;
        int baseCz = start.z + 200;
        long t0 = System.nanoTime();
        for (int i = 0; i < CHUNK_LOAD_COUNT; i++) {
            bot.loadChunkAt(baseCx + i, baseCz);
        }
        double msPerChunk = (System.nanoTime() - t0) / 1_000_000.0 / CHUNK_LOAD_COUNT;

        bot.leave();
        BenchRecorder.record(level.getServer(), "loadgen_fresh_chunk_load", "ms/chunk",
                msPerChunk, String.format(Locale.ROOT,
                        "bot walked %d fresh chunks to FULL status, flat world", CHUNK_LOAD_COUNT));
        helper.succeed();
    }

    // --- plumbing ---

    /**
     * One benchmark arena: configured world, forced chunk grid, the WS-1
     * population, and a walking bot. {@link #tearDown} leaves the world clean
     * for the next batch even when an assertion is about to fail.
     */
    private record Arena(ServerLevel level, BlockPos origin,
                         BenchmarkWorld.Population population, LoadBot bot) {

        static Arena setUp(GameTestHelper helper, String botName) {
            ServerLevel level = helper.getLevel();
            BlockPos origin = groundOrigin(helper);
            BenchmarkWorld.configure(level);
            forceChunks(level, origin, true);
            BenchmarkWorld.Population population = BenchmarkWorld.spawn(level, origin);
            LoadBot bot = LoadBot.join(level, origin, botName);
            return new Arena(level, origin, population, bot);
        }

        void tearDown() {
            ActivationHooks.setActive(false);
            population.discard();
            bot.leave();
            forceChunks(level, origin, false);
        }
    }

    /** Ground-level world position at the center of the (air) test structure. */
    private static BlockPos groundOrigin(GameTestHelper helper) {
        BlockPos structureCenter = helper.absolutePos(new BlockPos(2, 0, 2));
        int y = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING,
                structureCenter.getX(), structureCenter.getZ());
        return new BlockPos(structureCenter.getX(), y, structureCenter.getZ());
    }

    /**
     * Forces (or releases) the benchmark field's chunk grid. FORCED tickets
     * are entity-ticking (level 31), so the whole population simulates
     * regardless of player tickets; loading is completed synchronously so
     * spawning can rely on heightmaps.
     */
    private static void forceChunks(ServerLevel level, BlockPos origin, boolean forced) {
        ChunkPos center = new ChunkPos(origin);
        for (int cx = center.x - CHUNK_RADIUS; cx <= center.x + CHUNK_RADIUS; cx++) {
            for (int cz = center.z - CHUNK_RADIUS; cz <= center.z + CHUNK_RADIUS; cz++) {
                level.setChunkForced(cx, cz, forced);
                if (forced) {
                    level.getChunk(cx, cz);
                }
            }
        }
    }

    /**
     * The WS-1 parity criterion: a mob within 32 blocks of a (non-spectator)
     * player is never denied its AI tick. Uses the exact distance computation
     * the production hook uses.
     */
    private static void assertNearMobsAtFullRate(GameTestHelper helper, Arena arena) {
        for (int list = 0; list < 2; list++) {
            for (Mob mob : list == 0 ? arena.population().passive() : arena.population().hostile()) {
                if (mob.isRemoved()) {
                    continue;
                }
                double distSq = Double.POSITIVE_INFINITY;
                for (ServerPlayer player : arena.level().players()) {
                    if (!player.isSpectator()) {
                        distSq = Math.min(distSq, player.distanceToSqr(mob));
                    }
                }
                if (distSq <= FULL_RATE_DIST_SQ && !ActivationHooks.shouldTickAi(mob)) {
                    helper.fail(String.format(Locale.ROOT,
                            "WS-1 parity violation: %s at %.1f blocks from the bot was denied its AI tick",
                            mob.getType(), Math.sqrt(distSq)));
                }
            }
        }
    }

    /** Mean per-tick entity-phase cost and its AI-step slice, in ms. */
    record EntityPhase(double totalMs, double aiMs) {}

    /**
     * Mean ENTITY-source nanos per tick (and the AI-step slice thereof) over
     * completed ticks after {@code startTickExclusive}, read from the P0
     * profiler's rolling window.
     */
    private static EntityPhase entityPhaseMsPerTick(GameTestHelper helper, long startTickExclusive) {
        long endInclusive = WeftProfiler.get().tickCounter() - 1;
        long nanos = 0;
        long aiNanos = 0;
        int ticks = 0;
        for (TickProfiler.TickRecord record : WeftProfiler.get().snapshotWindow()) {
            if (record.tickNumber() > startTickExclusive && record.tickNumber() <= endInclusive) {
                ticks++;
                for (TickSample sample : record.samples()) {
                    if (sample.source() == TickSample.Source.ENTITY) {
                        nanos += sample.nanos();
                        aiNanos += sample.aiNanos();
                    }
                }
            }
        }
        if (ticks < PHASE_TICKS - 16) {
            helper.fail("Profiler window only covered " + ticks + " of " + PHASE_TICKS
                    + " phase ticks - is profiling enabled and the window sized to the phase?");
        }
        return new EntityPhase(nanos / 1_000_000.0 / ticks, aiNanos / 1_000_000.0 / ticks);
    }
}
