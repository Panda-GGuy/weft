package dev.weft.neoforge.gametest;

import dev.weft.neoforge.mixin.EntityCounterAccessor;
import dev.weft.neoforge.parity.WorldDigest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.SortedMap;
import java.util.SplittableRandom;

/**
 * The RFC-0005 parity scenario: a fixed arena of vanilla machinery and mobs,
 * rebuilt bit-identically for every run so the {@link WorldDigest} of two
 * runs is comparable. Content is chosen to cover the tick paths P2 owns —
 * entity ticking (mobs with AI, item entities, falling blocks) and
 * block-entity ticking (hoppers, furnaces, comparators) — plus the block
 * updates they cause (redstone clocks, pistons, observers, flowing water).
 *
 * <p>Determinism controls (RFC-0005 §3): the level RNG is reseeded and the
 * global entity-id counter pinned before every build; every mob gets its own
 * seeded RNG after spawning; mobs are persistent, named, and penned; the
 * GameTest server already pins mob spawning, weather, random ticks, and fire
 * off. The scenario must prove its own determinism (two vanilla runs, equal
 * digests) before it is allowed to judge Weft — the control phase of
 * {@code VanillaParityGameTests} enforces exactly that.
 */
final class ParityScenario {

    private ParityScenario() {}

    /** Arena footprint (blocks); the box spans y-1..HEIGHT relative to the base. */
    static final int SIZE = 40;
    static final int HEIGHT = 16;

    private static final long LEVEL_RNG_SEED = 0x5EED_2001L;
    private static final long MOB_POS_SEED = 0x5EED_2002L;
    private static final long MOB_RNG_SEED_BASE = 0x5EED_2003L;
    /** Far above anything another batch can have allocated (ids grow from 0). */
    private static final int ENTITY_ID_BASE = 5_000_000;

    private static final int DEMOLISH_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Digest of the arena, comparable across runs at the same base. */
    static SortedMap<String, String> capture(ServerLevel level, BlockPos base) {
        return WorldDigest.capture(level, base.offset(0, -1, 0),
                base.offset(SIZE - 1, HEIGHT, SIZE - 1), entityBounds(base));
    }

    /** Entity capture bounds: the arena plus slack for machine-flung items. */
    static AABB entityBounds(BlockPos base) {
        return new AABB(base.getX() - 8, base.getY() - 8, base.getZ() - 8,
                base.getX() + SIZE + 8, base.getY() + HEIGHT + 8, base.getZ() + SIZE + 8);
    }

    /**
     * Return the arena to bare air: discard every non-player entity nearby,
     * demolish without updates or drops, then clear pending scheduled ticks
     * and block events so no run inherits work an earlier run scheduled.
     */
    static void reset(ServerLevel level, BlockPos base) {
        discardEntities(level, base);
        BlockPos min = base.offset(0, -1, 0);
        BlockPos max = base.offset(SIZE - 1, HEIGHT, SIZE - 1);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        level.setBlock(cursor, air, DEMOLISH_FLAGS);
                    }
                }
            }
        }
        discardEntities(level, base); // anything demolition popped off
        BoundingBox box = new BoundingBox(min.getX(), min.getY(), min.getZ(),
                max.getX(), max.getY(), max.getZ()).inflatedBy(16);
        level.getBlockTicks().clearArea(box);
        level.getFluidTicks().clearArea(box);
        level.clearBlockEvents(box);
    }

    /** A clean platform for whatever runs in the world after the suite. */
    static void restoreFloor(ServerLevel level, BlockPos base) {
        fill(level, base, 0, -1, 0, SIZE - 1, -1, SIZE - 1, Blocks.SMOOTH_STONE.defaultBlockState());
    }

    /**
     * Build the scenario from bare air. Must be preceded by {@link #reset};
     * every call produces the identical start state (RFC-0005 §3).
     */
    static void build(ServerLevel level, BlockPos base) {
        // A base at/near the world floor means the heightmap was read from an
        // unloaded column: the floor would silently fail to place below build
        // height and the population would drop into the void. Refuse loudly.
        if (base.getY() - 1 <= level.getMinBuildHeight() + 2) {
            throw new IllegalStateException("parity arena base " + base + " is at/below the world "
                    + "floor - heightmap read before its chunk was loaded?");
        }
        // Deterministic start line: pinned entity ids, reseeded level RNG,
        // frozen midnight (daylight cycle already off).
        EntityCounterAccessor.weft$entityCounter().set(ENTITY_ID_BASE);
        level.random.setSeed(LEVEL_RNG_SEED);
        level.setDayTime(18000);

        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        restoreFloor(level, base);

        // A. Fast hopper clock -> comparator -> lamp (pure BE + redstone tick).
        hopperPair(level, base, 2, 2, 1);
        set(level, base, 1, 0, 2, comparatorFacing(Direction.EAST));
        set(level, base, 0, 0, 2, Blocks.REDSTONE_LAMP.defaultBlockState());

        // B. Slow hopper clock -> comparator strongly powering a block ->
        // sticky piston pushing a block watched by an observer (moving
        // blocks, shape updates). The comparator drives a solid block, not a
        // wire: a wire line pointing east-west gives no power to a neighbor
        // on its south side.
        hopperPair(level, base, 8, 2, 4);
        set(level, base, 7, 0, 2, comparatorFacing(Direction.EAST));
        set(level, base, 6, 0, 2, stone); // power block: comparator output lands here
        set(level, base, 6, 0, 3, Blocks.STICKY_PISTON.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.SOUTH));
        set(level, base, 6, 0, 4, stone);
        set(level, base, 6, 0, 6, Blocks.OBSERVER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.NORTH));

        // C. Clocked dropper feeding a chest (the dispense/block-event path
        // on a clock edge). The dropper must NOT eject into the air: dispense
        // spread draws from level.random, whose stream cannot be reproduced
        // across runs (vanilla shuffles the global ticking-chunk list with it
        // every tick, and that set is not ours to pin — RFC-0005 §3). A
        // container target transfers the item with zero RNG.
        hopperPair(level, base, 14, 2, 2);
        set(level, base, 13, 0, 2, comparatorFacing(Direction.EAST));
        set(level, base, 12, 0, 2, Blocks.DROPPER.defaultBlockState()
                .setValue(DirectionalBlock.FACING, Direction.WEST));
        set(level, base, 11, 0, 2, Blocks.CHEST.defaultBlockState());
        container(level, base, 12, 0, 2).setItem(0, new ItemStack(Items.STICK, 64));

        // D. Water channel: flowing fluid pushing item entities into a hopper.
        fill(level, base, 19, 0, 9, 28, 0, 9, stone);
        fill(level, base, 19, 0, 11, 28, 0, 11, stone);
        set(level, base, 19, 0, 10, stone);
        set(level, base, 28, 0, 10, stone);
        set(level, base, 27, -1, 10, Blocks.HOPPER.defaultBlockState()); // facing down: a bin
        set(level, base, 20, 0, 10, Blocks.WATER.defaultBlockState());
        for (int i = 0; i < 5; i++) {
            ItemEntity item = new ItemEntity(level,
                    base.getX() + 21.5 + i, base.getY() + 0.9, base.getZ() + 10.5,
                    new ItemStack(Items.OAK_PLANKS), 0.0, 0.0, 0.0);
            level.addFreshEntity(item);
        }

        // E. Hopper chain draining a chest into another (steady BE work).
        set(level, base, 2, 2, 30, Blocks.CHEST.defaultBlockState());
        container(level, base, 2, 2, 30).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        set(level, base, 2, 1, 30, hopperFacing(Direction.EAST));
        set(level, base, 3, 1, 30, hopperFacing(Direction.EAST));
        set(level, base, 4, 1, 30, Blocks.HOPPER.defaultBlockState()); // facing down
        set(level, base, 4, 0, 30, Blocks.CHEST.defaultBlockState());

        // F. Furnaces mid-smelt: per-tick progress counters in BE NBT are the
        // highest-resolution canary in the digest.
        for (int i = 0; i < 4; i++) {
            set(level, base, 10 + i, 0, 30, Blocks.FURNACE.defaultBlockState());
            BaseContainerBlockEntity furnace = container(level, base, 10 + i, 0, 30);
            furnace.setItem(0, new ItemStack(Items.RAW_IRON, 24));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
        }

        // G. Floating gravel columns: placement updates make them fall
        // immediately (FallingBlockEntity physics, then block landings).
        for (int[] column : new int[][]{{30, 5}, {33, 8}, {30, 11}}) {
            for (int dy = 4; dy < 8; dy++) {
                set(level, base, column[0], dy, column[1], Blocks.GRAVEL.defaultBlockState());
            }
        }

        // H. Penned mobs with seeded RNGs: full AI ticking (goals, sensing,
        // wander pathfinding) without unbounded wandering.
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        fill(level, base, 24, 0, 18, 37, 0, 18, fence);
        fill(level, base, 24, 0, 31, 37, 0, 31, fence);
        fill(level, base, 24, 0, 19, 24, 0, 30, fence);
        fill(level, base, 37, 0, 19, 37, 0, 30, fence);
        SplittableRandom positions = new SplittableRandom(MOB_POS_SEED);
        for (int i = 0; i < 10; i++) {
            EntityType<? extends Mob> type = i % 2 == 0 ? EntityType.ZOMBIE : EntityType.SHEEP;
            Mob mob = type.create(level);
            if (mob == null) {
                throw new IllegalStateException("could not create parity mob " + type);
            }
            double x = base.getX() + 25.5 + positions.nextDouble() * 11.0;
            double z = base.getZ() + 19.5 + positions.nextDouble() * 11.0;
            mob.moveTo(x, base.getY(), z, i * 36.0f, 0.0f);
            mob.setPersistenceRequired();
            mob.setCustomName(Component.literal("p2-" + (i % 2 == 0 ? "zombie" : "sheep") + "-" + i));
            if (!level.addFreshEntity(mob)) {
                throw new IllegalStateException("level rejected parity mob " + mob);
            }
            mob.getRandom().setSeed(MOB_RNG_SEED_BASE + i * 7919L);
        }
    }

    // --- plumbing ---

    /** Two face-to-face hoppers at (x,0,z)/(x+1,0,z) holding {@code items}: a clock. */
    private static void hopperPair(ServerLevel level, BlockPos base, int x, int z, int items) {
        set(level, base, x, 0, z, hopperFacing(Direction.EAST));
        set(level, base, x + 1, 0, z, hopperFacing(Direction.WEST));
        container(level, base, x, 0, z).setItem(0, new ItemStack(Items.COBBLESTONE, items));
    }

    private static BlockState hopperFacing(Direction facing) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing);
    }

    /** Comparator whose input (the container it measures) sits toward {@code input}. */
    private static BlockState comparatorFacing(Direction input) {
        return Blocks.COMPARATOR.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, input);
    }

    private static void set(ServerLevel level, BlockPos base, int x, int y, int z, BlockState state) {
        level.setBlock(base.offset(x, y, z), state, Block.UPDATE_ALL);
    }

    private static void fill(ServerLevel level, BlockPos base, int x1, int y1, int z1,
                             int x2, int y2, int z2, BlockState state) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    set(level, base, x, y, z, state);
                }
            }
        }
    }

    private static BaseContainerBlockEntity container(ServerLevel level, BlockPos base,
                                                      int x, int y, int z) {
        BlockEntity be = level.getBlockEntity(base.offset(x, y, z));
        if (be instanceof BaseContainerBlockEntity containerBe) {
            return containerBe;
        }
        throw new IllegalStateException("no container block entity at (" + x + "," + y + "," + z + ")");
    }

    private static void discardEntities(ServerLevel level, BlockPos base) {
        List<Entity> entities = level.getEntities((Entity) null,
                entityBounds(base).inflate(16), e -> !(e instanceof Player));
        entities.forEach(Entity::discard);
    }
}
