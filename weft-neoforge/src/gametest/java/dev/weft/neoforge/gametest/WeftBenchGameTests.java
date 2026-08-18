package dev.weft.neoforge.gametest;

import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.engine.telemetry.TickSample;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.activation.ActivationHooks;
import dev.weft.neoforge.path.PathfindingHooks;
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
 *       ({@code required = false}): same-run A/B measures 15-21.5% across
 *       runs (single-run noise is several points) with 92% of throttleable
 *       AI ticks skipped. Profiler sub-attribution (2026-08-16) explains the
 *       gap: the whole AI step ({@code serverAiStep}) is only ~19-20% of
 *       this world's entity phase — movement/physics is the rest — so no
 *       amount of AI-frequency gating clears 30% on this population. The
 *       bar waits on a different lever (WS-10 sharding compounding, or a
 *       cheaper entity base tick), not on more WS-1 widening. Until
 *       something clears it, {@code activationScheduling} keeps shipping
 *       default-off (RFC-0002 WS-1).</li>
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
            // The repath half of WS-1 can't prove engagement here (the flat
            // benchmark world has no block churn, so recomputePath traffic is
            // ~zero) - but a silently-unapplied fail-soft mixin must not pass
            // CI unnoticed either. Application is the checkable part.
            if (!ActivationHooks.repathHooksApplied()) {
                helper.fail("WS-1 repath-throttle mixin did not apply to PathNavigation");
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
        ActivationHooks.Counters[] countersAtActivation = new ActivationHooks.Counters[1];

        helper.onEachTick(() -> arena.bot().tickCircle(BOT_CIRCLE_RADIUS));

        // Phase A: vanilla AI cadence.
        helper.runAfterDelay(WARMUP_TICKS,
                () -> phaseStartTick[0] = WeftProfiler.get().tickCounter());

        // Phase B: activation scheduling on.
        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            baseline[0] = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            ActivationHooks.setActive(true);
            countersAtActivation[0] = ActivationHooks.counters();
            phaseStartTick[0] = WeftProfiler.get().tickCounter();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            EntityPhase activated = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            ActivationHooks.Counters engaged =
                    ActivationHooks.counters().minus(countersAtActivation[0]);
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
                                    + "%d passive + %d hostile mobs, %d measured ticks/phase; "
                                    + "%d AI skips, %d repaths deferred (WS-2 requests avoided)",
                            reduction, requiredReductionPct(),
                            BenchmarkWorld.PASSIVE_COUNT, BenchmarkWorld.HOSTILE_COUNT,
                            PHASE_TICKS, engaged.skips(), engaged.repathDeferrals()));

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
     * WS-2 nightly trend line (RFC-0002): A/B entity-phase measurement over
     * identical bot walks, synchronous vanilla pathfinding vs the async path
     * service — the same-run mirror of {@link #ws1EntityPhaseReduction}, so
     * WS-2's in-world effect stops being a cross-run comparison. WS-2's
     * acceptance is "main-thread reduction + behavior sanity" with no fixed
     * percentage bar, so the test records the numbers and only fails on a
     * vacuous run (the service never engaged — which would silently turn the
     * trend line into noise).
     *
     * <p>First same-run measurement (2026-08-16): ~0.2% with ~4k requests
     * routed off-thread — this flat world's paths are short and cheap, so
     * there is little synchronous A* cost to remove, and the earlier
     * "-18.3% WS-2 alone" cross-run reading was mostly run-to-run variance.
     * WS-2's value case is pathfinding-stressed worlds — proven by
     * {@link #ws2PathStressReduction} (2026-08-17: ~50-59% entity-phase
     * reduction on the 300-zombie maze), which is what flipped
     * {@code asyncPathfinding} default-on. This flat trend line stays as the
     * "does async cost anything when paths are cheap" watchdog (~0%).
     */
    @GameTest(template = "empty", batch = "ws2measure", timeoutTicks = 1200, required = false)
    public void ws2EntityPhaseReduction(GameTestHelper helper) {
        Arena arena = Arena.setUp(helper, "WeftWs2Bot");
        WeftConfig.PROFILING_ENABLED = true;
        WeftConfig.PROFILE_WINDOW_TICKS = PHASE_TICKS + 64;
        PathfindingHooks.setActive(false);

        long[] phaseStartTick = new long[1];
        EntityPhase[] baseline = new EntityPhase[1];
        long[] submittedAtActivation = new long[1];

        helper.onEachTick(() -> arena.bot().tickCircle(BOT_CIRCLE_RADIUS));

        // Phase A: synchronous vanilla pathfinding.
        helper.runAfterDelay(WARMUP_TICKS,
                () -> phaseStartTick[0] = WeftProfiler.get().tickCounter());

        // Phase B: async pathfinding on.
        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            baseline[0] = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            PathfindingHooks.setActive(true);
            submittedAtActivation[0] = PathfindingHooks.submittedCount();
            phaseStartTick[0] = WeftProfiler.get().tickCounter();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            EntityPhase activated = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            long requests = PathfindingHooks.submittedCount() - submittedAtActivation[0];
            WeftConfig.PROFILE_WINDOW_TICKS = 100;
            arena.tearDown();

            double reduction = 100.0 * (1.0 - activated.totalMs() / baseline[0].totalMs());
            BenchRecorder.record(helper.getLevel().getServer(),
                    "ws2_entity_phase_sync_pathfinding", "ms/tick", baseline[0].totalMs(),
                    "async pathfinding OFF (baseline)");
            BenchRecorder.record(helper.getLevel().getServer(),
                    "ws2_entity_phase_async_pathfinding", "ms/tick", activated.totalMs(),
                    String.format(Locale.ROOT,
                            "%.1f%% entity-phase reduction; %d requests routed off-thread; "
                                    + "%d passive + %d hostile mobs, %d measured ticks/phase",
                            reduction, requests,
                            BenchmarkWorld.PASSIVE_COUNT, BenchmarkWorld.HOSTILE_COUNT,
                            PHASE_TICKS));

            if (requests == 0) {
                helper.fail("WS-2 never engaged: zero path requests routed off-thread - "
                        + "is the optimizations mixin applied? hooksApplied="
                        + PathfindingHooks.hooksApplied());
            }
            helper.succeed();
        });
    }

    /**
     * WS-2 acceptance (RFC-0002): the 300-zombie stress world. A sealed
     * inner keep makes the bot A*-unreachable from outside, so every horde
     * repath runs vanilla's search to its visited-node budget — the
     * pathfinding-bound workload WS-2 exists for, which the flat-field
     * {@link #ws2EntityPhaseReduction} demonstrably is not. Same-run A/B
     * with a horde position reset at the phase boundary so both phases
     * start from the identical layout. The acceptance call ("measurable
     * main-thread reduction on a 300-zombie stress world") is made from the
     * recorded number; the test itself only hard-fails on a vacuous run.
     */
    @GameTest(template = "empty", batch = "ws2stress", timeoutTicks = 1600, required = false)
    public void ws2PathStressReduction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = groundOrigin(helper);
        BenchmarkWorld.configure(level);
        forceChunks(level, origin, true);
        java.util.List<BlockPos> maze = BenchmarkWorld.buildMaze(level, origin);
        java.util.List<Mob> horde = BenchmarkWorld.spawnZombieHorde(level, origin, 300);
        java.util.List<net.minecraft.world.phys.Vec3> starts =
                horde.stream().map(net.minecraft.world.entity.Entity::position).toList();
        LoadBot bot = LoadBot.join(level, origin, "WeftWs2StressBot");

        WeftConfig.PROFILING_ENABLED = true;
        WeftConfig.PROFILE_WINDOW_TICKS = PHASE_TICKS + 64;
        PathfindingHooks.setActive(false);

        long[] phaseStartTick = new long[1];
        EntityPhase[] baseline = new EntityPhase[1];
        long[] submittedAtActivation = new long[1];
        int[] tick = new int[1];

        helper.onEachTick(() -> {
            bot.tickCircle(6.0);
            // Retarget insurance every 20 ticks: an unreachable target is
            // never dropped for range reasons, but a lost line-of-sight
            // acquisition must not idle half the horde.
            if (tick[0]++ % 20 == 0) {
                for (Mob zombie : horde) {
                    if (!zombie.isRemoved() && zombie.getTarget() == null) {
                        zombie.setTarget(bot.player());
                    }
                }
            }
        });

        // Phase A: synchronous vanilla pathfinding.
        helper.runAfterDelay(WARMUP_TICKS,
                () -> phaseStartTick[0] = WeftProfiler.get().tickCounter());

        // Phase B: async on, horde reset to the identical starting layout.
        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            baseline[0] = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            for (int i = 0; i < horde.size(); i++) {
                Mob zombie = horde.get(i);
                if (!zombie.isRemoved()) {
                    var start = starts.get(i);
                    zombie.teleportTo(start.x, start.y, start.z);
                    zombie.getNavigation().stop();
                    zombie.setTarget(bot.player());
                }
            }
            PathfindingHooks.setActive(true);
            submittedAtActivation[0] = PathfindingHooks.submittedCount();
            phaseStartTick[0] = WeftProfiler.get().tickCounter();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            EntityPhase activated = entityPhaseMsPerTick(helper, phaseStartTick[0]);
            long requests = PathfindingHooks.submittedCount() - submittedAtActivation[0];
            WeftConfig.PROFILE_WINDOW_TICKS = 100;
            PathfindingHooks.setActive(false);
            horde.forEach(net.minecraft.world.entity.Entity::discard);
            bot.leave();
            BenchmarkWorld.clearMaze(level, maze);
            forceChunks(level, origin, false);

            double reduction = 100.0 * (1.0 - activated.totalMs() / baseline[0].totalMs());
            BenchRecorder.record(level.getServer(),
                    "ws2_stress_entity_phase_sync_pathfinding", "ms/tick", baseline[0].totalMs(),
                    "300-zombie maze horde, async pathfinding OFF (baseline)");
            BenchRecorder.record(level.getServer(),
                    "ws2_stress_entity_phase_async_pathfinding", "ms/tick", activated.totalMs(),
                    String.format(Locale.ROOT,
                            "%.1f%% entity-phase reduction on the WS-2 acceptance world "
                                    + "(300 zombies, sealed-keep maze, every repath runs to "
                                    + "the A* node budget); %d requests routed off-thread, "
                                    + "%d measured ticks/phase",
                            reduction, requests, PHASE_TICKS));

            if (requests == 0) {
                helper.fail("WS-2 stress never engaged: zero path requests routed off-thread - "
                        + "is the optimizations mixin applied? hooksApplied="
                        + PathfindingHooks.hooksApplied());
            }
            helper.succeed();
        });
    }

    /**
     * The P1 exit criterion (RFC-0001 §11: "measurable TPS win on stock
     * packs"): end-to-end MSPT, not a phase slice — the same full-tick
     * number spark or {@code /tps} shows a server admin — over a stock-shaped
     * world: 2500 cap-countable (non-persistent) passive mobs, no mods, no
     * Weft modules in phase A; spawn-density AUTHORITATIVE (shipping
     * default, verify ticks included) plus the shipped {@code
     * asyncPathfinding} default in phase B. {@code doMobSpawning} stays off
     * so the population is run-stable; vanilla runs {@code createState}
     * every tick regardless, which is precisely the cost being taken off
     * the thread.
     */
    @GameTest(template = "empty", batch = "p1exit", timeoutTicks = 1600, required = false)
    public void p1EndToEndMspt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = groundOrigin(helper);
        BenchmarkWorld.configure(level);
        forceChunks(level, origin, true);
        java.util.List<Mob> population = BenchmarkWorld.spawnCountablePassive(level, origin, 2500);
        LoadBot bot = LoadBot.join(level, origin, "WeftP1ExitBot");

        WeftConfig.PROFILING_ENABLED = true;
        WeftConfig.PROFILE_WINDOW_TICKS = PHASE_TICKS + 64;
        dev.weft.neoforge.service.SpawnDensityHooks.setActive(false);
        PathfindingHooks.setActive(false);

        long[] phaseStartTick = new long[1];
        double[] baselineMspt = new double[2];
        long[] authTicksAtActivation = new long[1];

        helper.onEachTick(() -> bot.tickCircle(BOT_CIRCLE_RADIUS));

        // Phase A: vanilla — every P1 service off.
        helper.runAfterDelay(WARMUP_TICKS,
                () -> phaseStartTick[0] = WeftProfiler.get().tickCounter());

        // Phase B: P1 services at their shipping defaults.
        helper.runAfterDelay(WARMUP_TICKS + PHASE_TICKS, () -> {
            double[] mspt = msptMsPerTick(helper, phaseStartTick[0]);
            baselineMspt[0] = mspt[0];
            baselineMspt[1] = mspt[1];
            dev.weft.neoforge.service.SpawnDensityHooks.setActive(true);
            PathfindingHooks.setActive(WeftConfig.ASYNC_PATHFINDING);
            authTicksAtActivation[0] = dev.weft.neoforge.WeftMod.servicesOrNull()
                    .spawnStats(level).authoritativeTicks();
            phaseStartTick[0] = WeftProfiler.get().tickCounter();
        });

        helper.runAfterDelay(WARMUP_TICKS + 2 * PHASE_TICKS, () -> {
            double[] servicesMspt = msptMsPerTick(helper, phaseStartTick[0]);
            var stats = dev.weft.neoforge.WeftMod.servicesOrNull().spawnStats(level);
            long authTicks = stats.authoritativeTicks() - authTicksAtActivation[0];
            WeftConfig.PROFILE_WINDOW_TICKS = 100;
            PathfindingHooks.setActive(false);
            // Leave the spawn-density module in its config-resolved state
            // rather than force-off: later batches should see shipping defaults.
            dev.weft.neoforge.coexist.WeftModules.resolve();
            population.forEach(net.minecraft.world.entity.Entity::discard);
            bot.leave();
            forceChunks(level, origin, false);

            double reduction = 100.0 * (1.0 - servicesMspt[0] / baselineMspt[0]);
            BenchRecorder.record(level.getServer(),
                    "p1_end_to_end_mspt_vanilla", "ms/tick", baselineMspt[0],
                    String.format(Locale.ROOT,
                            "full-tick MSPT, all P1 services off; p95 %.3f ms; 2500 countable "
                                    + "passive mobs, %d measured ticks", baselineMspt[1], PHASE_TICKS));
            BenchRecorder.record(level.getServer(),
                    "p1_end_to_end_mspt_p1_services", "ms/tick", servicesMspt[0],
                    String.format(Locale.ROOT,
                            "%.1f%% full-tick MSPT reduction with P1 services at shipping "
                                    + "defaults (spawn-density AUTHORITATIVE incl. verify ticks, "
                                    + "asyncPathfinding=%s); p95 %.3f ms; %d/%d ticks served async",
                            reduction, WeftConfig.ASYNC_PATHFINDING, servicesMspt[1],
                            authTicks, PHASE_TICKS));

            if (authTicks == 0) {
                helper.fail("P1 end-to-end never engaged: zero ticks served by the "
                        + "authoritative spawn-density service - hooksApplied="
                        + dev.weft.neoforge.service.SpawnDensityHooks.hooksApplied());
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
            // R6: deactivation closes the path workers; a no-op when the test
            // never activated WS-2.
            PathfindingHooks.setActive(false);
            population.discard();
            bot.leave();
            forceChunks(level, origin, false);
        }
    }

    /** Ground-level world position at the center of the (air) test structure. */
    static BlockPos groundOrigin(GameTestHelper helper) {
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
    static void forceChunks(ServerLevel level, BlockPos origin, boolean forced) {
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
                // Repath half of the same criterion: inside the full-rate ring
                // a recompute one tick after the last one - deep inside
                // vanilla's own 20-tick window - must never be deferred by us
                // (vanilla's own delay logic stays authoritative there).
                long gameTime = arena.level().getGameTime();
                if (distSq <= FULL_RATE_DIST_SQ
                        && ActivationHooks.shouldDeferRepath(mob, gameTime, gameTime - 1)) {
                    helper.fail(String.format(Locale.ROOT,
                            "WS-1 parity violation: %s at %.1f blocks from the bot had its repath deferred",
                            mob.getType(), Math.sqrt(distSq)));
                }
            }
        }
    }

    /**
     * Full-tick MSPT — {@code [mean, p95]} in ms — over completed ticks after
     * {@code startTickExclusive}, from the profiler's per-tick wall timing
     * ({@code TickRecord.tickNanos}): the whole server tick, all levels and
     * phases, i.e. the number a TPS monitor reports.
     */
    // Package-private so the P2 sharding benchmark measures MSPT the exact
    // same way the P1 exit criterion does, rather than a near-copy that could
    // drift from it.
    static double[] msptMsPerTick(GameTestHelper helper, long startTickExclusive) {
        long endInclusive = WeftProfiler.get().tickCounter() - 1;
        java.util.List<Long> nanos = new java.util.ArrayList<>();
        for (TickProfiler.TickRecord record : WeftProfiler.get().snapshotWindow()) {
            if (record.tickNumber() > startTickExclusive && record.tickNumber() <= endInclusive) {
                nanos.add(record.tickNanos());
            }
        }
        if (nanos.size() < PHASE_TICKS - 16) {
            helper.fail("Profiler window only covered " + nanos.size() + " of " + PHASE_TICKS
                    + " phase ticks - is profiling enabled and the window sized to the phase?");
        }
        nanos.sort(null);
        double mean = nanos.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
        double p95 = nanos.get((int) Math.min(nanos.size() - 1, Math.ceil(nanos.size() * 0.95) - 1)) / 1e6;
        return new double[]{mean, p95};
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
