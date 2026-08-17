package dev.weft.neoforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * WS-8 (RFC-0002): the reproducible benchmark world. The GameTest server
 * already provides the reproducible terrain half (flat world preset, seed 0,
 * mob spawning / weather / random ticks / fire all off — see vanilla
 * {@code GameTestServer}); this class provides the population half: the WS-1
 * acceptance layout of 2000 passive + 500 hostile mobs placed at positions
 * drawn from a fixed-seed generator, so every run spawns the identical field.
 *
 * <p>Band layout is keyed to the WS-1 default tiers (full rate to 32 blocks,
 * 1/4 rate to 64, 1/20 beyond): a near ring inside the full-rate ring for the
 * behavior-parity check, a mid band in the reduced tier, and the bulk beyond
 * 64 blocks where throttling should pay. Hostiles stay past 90 blocks so no
 * mob acquires the bot as a target (targeting exempts a mob from throttling
 * and would blur the A/B).
 *
 * <p>Mobs are spawned raw ({@link EntityType#create}) rather than via
 * {@code finalizeSpawn}, skipping random equipment/baby rolls — one less
 * source of run-to-run variance. All are persistence-required so nothing
 * despawns mid-benchmark.
 */
public final class BenchmarkWorld {

    private BenchmarkWorld() {}

    /** Fixed population seed: positions are identical on every run. */
    public static final long POPULATION_SEED = 0x5EED_0002L;

    public static final int PASSIVE_COUNT = 2000;
    public static final int HOSTILE_COUNT = 500;

    /** Outermost band edge; chunk forcing must cover this plus wander slack. */
    public static final int MAX_RADIUS = 140;

    private static final List<EntityType<? extends Mob>> PASSIVE_TYPES = List.of(
            EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN);
    private static final List<EntityType<? extends Mob>> HOSTILE_TYPES = List.of(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER);

    /** Handle on the spawned field so a test can tear it down. */
    public record Population(List<Mob> passive, List<Mob> hostile) {
        public void discard() {
            passive.forEach(Entity::discard);
            hostile.forEach(Entity::discard);
        }
    }

    /**
     * One-time world prep: midnight forever, so the undead bands neither burn
     * nor get new company (the test server already disables mob spawning).
     */
    public static void configure(ServerLevel level) {
        level.setDayTime(18000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
    }

    /** Spawns the full WS-1 acceptance population around {@code origin}. */
    public static Population spawn(ServerLevel level, BlockPos origin) {
        SplittableRandom rng = new SplittableRandom(POPULATION_SEED);
        List<Mob> passive = new ArrayList<>(PASSIVE_COUNT);
        List<Mob> hostile = new ArrayList<>(HOSTILE_COUNT);
        // Passive: 150 in the full-rate ring (parity witnesses), 350 in the
        // reduced tier, 1500 in the far tier.
        spawnBand(level, origin, rng, PASSIVE_TYPES, 150, 8, 24, passive);
        spawnBand(level, origin, rng, PASSIVE_TYPES, 350, 40, 60, passive);
        spawnBand(level, origin, rng, PASSIVE_TYPES, 1500, 80, MAX_RADIUS, passive);
        spawnBand(level, origin, rng, HOSTILE_TYPES, HOSTILE_COUNT, 90, MAX_RADIUS, hostile);
        return new Population(passive, hostile);
    }

    private static void spawnBand(ServerLevel level, BlockPos origin, SplittableRandom rng,
                                  List<EntityType<? extends Mob>> types, int count,
                                  int minRadius, int maxRadius, List<Mob> out) {
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble(Math.PI * 2);
            double radius = minRadius + rng.nextDouble() * (maxRadius - minRadius);
            double x = origin.getX() + 0.5 + Math.cos(angle) * radius;
            double z = origin.getZ() + 0.5 + Math.sin(angle) * radius;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(x), (int) Math.floor(z));
            Mob mob = types.get(i % types.size()).create(level);
            if (mob == null) {
                throw new IllegalStateException("Could not create " + types.get(i % types.size()));
            }
            mob.moveTo(x, y, z, (float) (angle * 180.0 / Math.PI), 0.0f);
            mob.setPersistenceRequired();
            if (!level.addFreshEntity(mob)) {
                throw new IllegalStateException("Level rejected benchmark mob " + mob);
            }
            out.add(mob);
        }
    }
}
