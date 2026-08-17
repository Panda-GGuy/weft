package dev.weft.neoforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
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
        spawnBand(level, origin, rng, PASSIVE_TYPES, 150, 8, 24, true, passive);
        spawnBand(level, origin, rng, PASSIVE_TYPES, 350, 40, 60, true, passive);
        spawnBand(level, origin, rng, PASSIVE_TYPES, 1500, 80, MAX_RADIUS, true, passive);
        spawnBand(level, origin, rng, HOSTILE_TYPES, HOSTILE_COUNT, 90, MAX_RADIUS, true, hostile);
        return new Population(passive, hostile);
    }

    /**
     * A cap-countable passive population for the spawn-density benchmarks:
     * NON-persistent, so {@code NaturalSpawner.createState} counts every one
     * of them instead of skipping on the persistence-exempt fast path (the
     * WS-1 population is persistence-required and therefore near-invisible
     * to the scan's real per-entity cost). Animals never despawn, so the
     * population is still run-stable. Same fixed seed layout.
     */
    public static List<Mob> spawnCountablePassive(ServerLevel level, BlockPos origin, int count) {
        SplittableRandom rng = new SplittableRandom(POPULATION_SEED ^ 0xC0DECAFEL);
        List<Mob> out = new ArrayList<>(count);
        spawnBand(level, origin, rng, PASSIVE_TYPES, count, 8, MAX_RADIUS, false, out);
        return out;
    }

    /**
     * The WS-2 acceptance horde (RFC-0002: "300-zombie stress world"):
     * persistence-required zombies in a ring outside the maze's inner
     * keep, all within a zombie's 35-block follow range of the center so
     * a target at the center stays valid. Positions are fixed-seed; the
     * returned list preserves spawn order so a test can teleport everyone
     * back for a clean phase reset.
     */
    public static List<Mob> spawnZombieHorde(ServerLevel level, BlockPos origin, int count) {
        SplittableRandom rng = new SplittableRandom(POPULATION_SEED ^ 0x300DEADL);
        List<Mob> out = new ArrayList<>(count);
        spawnBand(level, origin, rng, List.of(EntityType.ZOMBIE), count, 14, 30, true, out);
        return out;
    }

    private static void spawnBand(ServerLevel level, BlockPos origin, SplittableRandom rng,
                                  List<EntityType<? extends Mob>> types, int count,
                                  int minRadius, int maxRadius, boolean persistent, List<Mob> out) {
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
            if (persistent) {
                mob.setPersistenceRequired();
            }
            if (!level.addFreshEntity(mob)) {
                throw new IllegalStateException("Level rejected benchmark mob " + mob);
            }
            out.add(mob);
        }
    }

    // --- WS-2 stress maze -------------------------------------------------

    /** Maze wall rings: {radius, gapCount}. The inner keep (gapCount 0) is sealed. */
    private static final int[][] MAZE_RINGS = {{10, 0}, {18, 2}, {26, 4}};
    private static final int MAZE_WALL_HEIGHT = 4;

    /**
     * Builds the WS-2 stress maze: three square wall rings around
     * {@code origin}. The inner ring is sealed, so a target at the center is
     * A*-unreachable from outside — every horde repath runs the search to
     * its visited-node budget, the worst case async pathfinding exists to
     * take off the server thread. Outer rings have staggered gaps so the
     * horde keeps milling and repathing rather than piling on one wall.
     * Returns the placed wall positions so {@link #clearMaze} can restore
     * the world for later batches.
     */
    public static List<BlockPos> buildMaze(ServerLevel level, BlockPos origin) {
        List<BlockPos> placed = new ArrayList<>();
        for (int ringIndex = 0; ringIndex < MAZE_RINGS.length; ringIndex++) {
            int radius = MAZE_RINGS[ringIndex][0];
            int gaps = MAZE_RINGS[ringIndex][1];
            int side = radius * 2 + 1;
            // Edge order: north, south, west, east. The first `gaps` edges get
            // one 3-wide gap each, at a deterministic position staggered by
            // ring and edge so consecutive rings never line their gaps up.
            for (int edge = 0; edge < 4; edge++) {
                boolean hasGap = edge < gaps;
                int gapCenter = 2 + ((ringIndex * 7 + edge * 11) % (side - 4));
                for (int t = 0; t < side; t++) {
                    if (hasGap && Math.abs(t - gapCenter) <= 1) {
                        continue;
                    }
                    int dx = switch (edge) {
                        case 0, 1 -> t - radius;
                        case 2 -> -radius;
                        default -> radius;
                    };
                    int dz = switch (edge) {
                        case 0 -> -radius;
                        case 1 -> radius;
                        default -> t - radius;
                    };
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    for (int dy = 0; dy < MAZE_WALL_HEIGHT; dy++) {
                        BlockPos pos = new BlockPos(x, ground + dy, z);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.STONE_BRICKS.defaultBlockState(), 3);
                            placed.add(pos);
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** Removes every maze block {@link #buildMaze} placed. */
    public static void clearMaze(ServerLevel level, List<BlockPos> placed) {
        for (BlockPos pos : placed) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}
