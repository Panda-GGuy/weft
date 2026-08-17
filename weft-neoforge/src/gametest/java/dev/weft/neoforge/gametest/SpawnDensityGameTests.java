package dev.weft.neoforge.gametest;

import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.service.SpawnDensityHooks;
import dev.weft.neoforge.service.SpawnDensityMode;
import dev.weft.neoforge.service.WeftServices;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Locale;

/**
 * P1 spawn-density graduation gate (RFC-0001 §11): with the service
 * AUTHORITATIVE, vanilla's mob-spawning machinery runs on the async result
 * and the world stays correct. Three phases:
 *
 * <ol>
 *   <li><b>Settle</b> — after force-loading the arena, wait until the spawn
 *       state's creature count is stable: vanilla's chunk gate (visible
 *       holder + completed full future) converges over many ticks after a
 *       mass force-load, and while that frontier moves, our
 *       one-tick-stale result legitimately trails it. Real servers live in
 *       the converged state.</li>
 *   <li><b>Static parity (hard gate)</b> — fixed non-persistent population,
 *       {@code doMobSpawning} off, world converged. Verify ticks diff our
 *       result against a real vanilla scan; with no entity churn and no
 *       gate movement, parity must be exactly 100%.</li>
 *   <li><b>Live spawning (hard gate)</b> — {@code doMobSpawning} on at
 *       midnight, so vanilla's spawn attempts consume the constructed
 *       SpawnState ({@code canSpawnForCategory}/{@code afterSpawn} mutate
 *       our counts, potential, and local caps). The gate is zero service
 *       failures, zero fallback latch, and the authoritative path still
 *       serving.</li>
 * </ol>
 */
@GameTestHolder("weft")
@PrefixGameTestTemplate(false)
public class SpawnDensityGameTests {

    private static final int SETTLE_TIMEOUT_TICKS = 900;
    private static final int SETTLE_STABLE_TICKS = 60;
    private static final int STATIC_TICKS = 200;
    private static final int SPAWNING_TICKS = 150;
    private static final int VERIFY_INTERVAL = 10;
    private static final int POPULATION = 400;

    @GameTest(template = "empty", batch = "spawnauth", timeoutTicks = 1600)
    public void spawnDensityAuthoritativeParity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = WeftBenchGameTests.groundOrigin(helper);
        BenchmarkWorld.configure(level);
        WeftBenchGameTests.forceChunks(level, origin, true);
        List<Mob> population = BenchmarkWorld.spawnCountablePassive(level, origin, POPULATION);
        LoadBot bot = LoadBot.join(level, origin, "WeftSpawnAuthBot");

        if (!SpawnDensityHooks.hooksApplied()) {
            helper.fail("spawn-density tickChunks mixin did not apply to ServerChunkCache");
        }

        SpawnDensityMode prevMode = WeftConfig.SPAWN_DENSITY_MODE;
        int prevVerify = WeftConfig.SPAWN_DENSITY_VERIFY_INTERVAL_TICKS;
        WeftConfig.SPAWN_DENSITY_MODE = SpawnDensityMode.AUTHORITATIVE;
        WeftConfig.SPAWN_DENSITY_VERIFY_INTERVAL_TICKS = VERIFY_INTERVAL;
        SpawnDensityHooks.setActive(true);

        Runnable tearDown = () -> {
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
            WeftConfig.SPAWN_DENSITY_MODE = prevMode;
            WeftConfig.SPAWN_DENSITY_VERIFY_INTERVAL_TICKS = prevVerify;
            WeftModules.resolve(); // restore the config-resolved active state
            // Sweep everything non-persistent we spawned or that spawned
            // during the live phase, so later batches see a clean world.
            // Snapshot first: discarding while iterating getAllEntities
            // mutates the entity sections under the iterator.
            java.util.List<Mob> sweep = new java.util.ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && !mob.isPersistenceRequired()
                        && mob.blockPosition().distSqr(origin) < 400 * 400) {
                    sweep.add(mob);
                }
            }
            sweep.forEach(Entity::discard);
            population.forEach(Entity::discard);
            bot.leave();
            WeftBenchGameTests.forceChunks(level, origin, false);
        };

        WeftServices.SpawnStats[] atStart = new WeftServices.SpawnStats[1];
        WeftServices.SpawnStats[] atSpawnPhase = new WeftServices.SpawnStats[1];
        int[] lastCount = {Integer.MIN_VALUE};
        int[] stableStreak = {0};
        boolean[] settled = {false};

        // Phase 0: converge. The count read here is whatever state vanilla
        // is actually using (ours on async ticks, its own on verify ticks) —
        // both sides share the chunk gate, so stability means the
        // force-load's visibility frontier has stopped moving.
        helper.onEachTick(() -> {
            if (settled[0]) {
                return;
            }
            var state = level.getChunkSource().getLastSpawnState();
            int count = state == null ? Integer.MIN_VALUE
                    : state.getMobCategoryCounts().getInt(MobCategory.CREATURE);
            stableStreak[0] = count != Integer.MIN_VALUE && count == lastCount[0]
                    ? stableStreak[0] + 1 : 0;
            lastCount[0] = count;
            if (stableStreak[0] < SETTLE_STABLE_TICKS) {
                return;
            }
            settled[0] = true;
            atStart[0] = WeftMod.servicesOrNull().spawnStats(level);

            // Phase 1 end: static-world assertions.
            helper.runAfterDelay(STATIC_TICKS, () -> {
                WeftServices.SpawnStats now = WeftMod.servicesOrNull().spawnStats(level);
                long served = now.authoritativeTicks() - atStart[0].authoritativeTicks();
                long verified = now.parityTicks() - atStart[0].parityTicks();
                long mismatched = now.parityMismatchTicks() - atStart[0].parityMismatchTicks();
                if (served < STATIC_TICKS * 3 / 4) {
                    tearDown.run();
                    helper.fail(String.format(Locale.ROOT,
                            "authoritative path served only %d of %d ticks (fallbacks %d) - "
                                    + "the async result should be fresh nearly every tick",
                            served, STATIC_TICKS,
                            now.fallbackTicks() - atStart[0].fallbackTicks()));
                }
                if (verified < STATIC_TICKS / VERIFY_INTERVAL - 2) {
                    tearDown.run();
                    helper.fail("verify ticks did not run: " + verified);
                }
                if (mismatched != 0) {
                    tearDown.run();
                    helper.fail(String.format(Locale.ROOT,
                            "parity mismatch on a static converged population (%d of %d "
                                    + "verify ticks, last delta: %s): one-tick staleness cannot "
                                    + "explain deltas when nothing spawns, despawns, or crosses "
                                    + "the chunk-visibility frontier - this is a real bug",
                            mismatched, verified, now.lastMismatch()));
                }
                if (now.serviceFailures() > 0 || now.latchedOff()) {
                    tearDown.run();
                    helper.fail("service failures=" + now.serviceFailures()
                            + " latchedOff=" + now.latchedOff());
                }
                // Phase 2: let vanilla spawn THROUGH the constructed state.
                atSpawnPhase[0] = now;
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING)
                        .set(true, level.getServer());
            });

            helper.runAfterDelay(STATIC_TICKS + SPAWNING_TICKS, () -> {
                WeftServices.SpawnStats now = WeftMod.servicesOrNull().spawnStats(level);
                long served = now.authoritativeTicks() - atSpawnPhase[0].authoritativeTicks();
                long monsters = 0;
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof net.minecraft.world.entity.monster.Monster) {
                        monsters++;
                    }
                }
                tearDown.run();
                if (now.serviceFailures() > 0 || now.latchedOff()) {
                    helper.fail("live-spawning phase broke the service: failures="
                            + now.serviceFailures() + " latchedOff=" + now.latchedOff());
                }
                if (served < SPAWNING_TICKS / 2) {
                    helper.fail("authoritative path stopped serving once spawning started: "
                            + served + " of " + SPAWNING_TICKS + " ticks");
                }
                // Informational: at NORMAL difficulty, midnight, dark flat
                // terrain, monsters spawning at all proves canSpawnForCategory /
                // afterSpawn consumed the constructed state end to end. Not a
                // hard gate (spawn attempts are random), but recorded.
                BenchRecorder.record(level.getServer(), "spawn_density_live_spawns", "mobs",
                        monsters, String.format(Locale.ROOT,
                                "monsters spawned through the authoritative SpawnState in %d ticks "
                                        + "(doMobSpawning on, midnight); %d ticks served async",
                                SPAWNING_TICKS, served));
                helper.succeed();
            });
        });

        // Settle watchdog: a world that never converges is an infrastructure
        // problem worth failing loudly on, not hanging until timeout.
        helper.runAfterDelay(SETTLE_TIMEOUT_TICKS, () -> {
            if (!settled[0]) {
                tearDown.run();
                helper.fail(String.format(Locale.ROOT,
                        "spawn state never converged after force-load: creature count still "
                                + "moving at tick %d (last %d, stable streak %d)",
                        SETTLE_TIMEOUT_TICKS, lastCount[0], stableStreak[0]));
            }
        });
    }
}
