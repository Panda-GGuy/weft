package dev.weft.neoforge.parity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The semantic world digest behind the vanilla-parity suite (RFC-0001 §10,
 * RFC-0005): a sorted map of human-readable entries covering entity state
 * (type, name, bit-exact position/rotation/motion, health, age), block-entity
 * state (canonicalized full NBT), and per-chunk block-state hashes over a
 * bounding box. Two runs are semantically equivalent at the bit-identical
 * level iff their digests are equal; {@link #diff} renders the first
 * mismatches readably, because a bare hash mismatch is useless for debugging.
 *
 * <p>Deliberately excluded (RFC-0005 §2): entity ids and UUIDs (fresh each
 * run; ids are pinned separately because they leak into behavior), absolute
 * game-time values (runs start at different gameTimes, so entities are
 * described by explicit fields rather than full NBT), and light levels
 * (computed asynchronously by vanilla, observed by nothing in the suite's
 * scenarios). Doubles and floats are rendered via hex strings so equality is
 * bit-exact, never rounded.
 *
 * <p>Comparisons are only meaningful within one JVM run (block-state ids and
 * NBT canonicalization are registry-order dependent); the parity suite always
 * compares runs from the same server instance.
 */
public final class WorldDigest {

    private WorldDigest() {}

    /**
     * Capture a digest of everything inside {@code [blockMin, blockMax]}
     * (blocks, block entities) and {@code entityBounds} (entities, players
     * excluded). Keys are relative to {@code blockMin} so captures at the
     * same arena are directly comparable.
     */
    public static SortedMap<String, String> capture(ServerLevel level, BlockPos blockMin,
                                                    BlockPos blockMax, AABB entityBounds) {
        TreeMap<String, String> out = new TreeMap<>();
        captureBlocksAndBlockEntities(level, blockMin, blockMax, out);
        captureEntities(level, blockMin, entityBounds, out);
        return out;
    }

    /**
     * The snapshot half of an RFC-0005 class <b>E2</b> capture: quantities
     * that must be <em>conserved</em> even when within-tick ordering is
     * allowed to differ (WS-10 sharding, RFC-0004 §2.5) — the population by
     * entity type, and every item in the arena counted by item type across
     * both container block entities and loose item entities.
     *
     * <p>Deliberately <em>not</em> a state digest: no positions, no NBT, no
     * per-entity identity. Two runs that interleave differently will
     * legitimately disagree on {@link #capture}'s entries while agreeing
     * here, and that difference is exactly what E2 is for. Every value is an
     * integer total, so the comparison is order-independent by construction.
     *
     * <p>Item counting walks container block entities via their own slot
     * accessors, so a mod container that stores items outside a vanilla
     * {@code Container} contributes nothing — an honest floor, not a lie: it
     * can under-count what it cannot see, but it never invents conservation
     * it did not verify. The suite's scenarios use vanilla containers.
     */
    public static SortedMap<String, String> captureConservation(ServerLevel level, BlockPos blockMin,
                                                                BlockPos blockMax, AABB entityBounds) {
        TreeMap<String, String> out = new TreeMap<>();
        Map<String, Integer> entityCounts = new HashMap<>();
        Map<String, Integer> itemCounts = new HashMap<>();

        for (Entity entity : level.getEntities((Entity) null, entityBounds, e -> !(e instanceof Player))) {
            entityCounts.merge(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())),
                    1, Integer::sum);
            if (entity instanceof ItemEntity item) {
                countStack(itemCounts, item.getItem());
            }
        }

        for (int cx = blockMin.getX() >> 4; cx <= blockMax.getX() >> 4; cx++) {
            for (int cz = blockMin.getZ() >> 4; cz <= blockMax.getZ() >> 4; cz++) {
                for (Map.Entry<BlockPos, BlockEntity> e : level.getChunk(cx, cz).getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    if (pos.getX() < blockMin.getX() || pos.getX() > blockMax.getX()
                            || pos.getY() < blockMin.getY() || pos.getY() > blockMax.getY()
                            || pos.getZ() < blockMin.getZ() || pos.getZ() > blockMax.getZ()) {
                        continue;
                    }
                    if (e.getValue() instanceof Container container) {
                        for (int slot = 0; slot < container.getContainerSize(); slot++) {
                            countStack(itemCounts, container.getItem(slot));
                        }
                    }
                }
            }
        }

        int population = 0;
        for (Map.Entry<String, Integer> e : entityCounts.entrySet()) {
            out.put("cons entities " + e.getKey(), Integer.toString(e.getValue()));
            population += e.getValue();
        }
        out.put("cons entities total", Integer.toString(population));
        int items = 0;
        for (Map.Entry<String, Integer> e : itemCounts.entrySet()) {
            out.put("cons items " + e.getKey(), Integer.toString(e.getValue()));
            items += e.getValue();
        }
        out.put("cons items total", Integer.toString(items));
        return out;
    }

    private static void countStack(Map<String, Integer> counts, ItemStack stack) {
        if (!stack.isEmpty()) {
            counts.merge(String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                    stack.getCount(), Integer::sum);
        }
    }

    /** Human-readable first differences between two digests, at most {@code limit} entries. */
    public static List<String> diff(SortedMap<String, String> a, SortedMap<String, String> b,
                                    int limit) {
        List<String> lines = new ArrayList<>();
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            String va = a.get(key);
            String vb = b.get(key);
            if (Objects.equals(va, vb)) {
                continue;
            }
            lines.add(key + "\n    A: " + truncate(va) + "\n    B: " + truncate(vb));
            if (lines.size() >= limit) {
                lines.add("... (further differences truncated)");
                break;
            }
        }
        return lines;
    }

    private static void captureBlocksAndBlockEntities(ServerLevel level, BlockPos min,
                                                      BlockPos max, TreeMap<String, String> out) {
        // One rolling hash per chunk column so a mismatch localizes.
        Map<Long, long[]> chunkHashes = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                long chunkKey = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
                long[] hash = chunkHashes.computeIfAbsent(chunkKey, k -> new long[]{0xCBF29CE484222325L});
                for (int y = min.getY(); y <= max.getY(); y++) {
                    cursor.set(x, y, z);
                    int stateId = Block.getId(level.getBlockState(cursor));
                    hash[0] = (hash[0] ^ (stateId * 31L + (long) (y - min.getY()))) * 0x100000001B3L;
                    hash[0] ^= ((long) (x - min.getX()) << 20) ^ ((long) (z - min.getZ()) << 40);
                }
            }
        }
        for (Map.Entry<Long, long[]> e : chunkHashes.entrySet()) {
            int cx = (int) (e.getKey() >> 32);
            int cz = (int) (long) e.getKey();
            out.put(String.format("blocks chunk (%d,%d)", cx - (min.getX() >> 4), cz - (min.getZ() >> 4)),
                    Long.toHexString(e.getValue()[0]));
        }

        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = e.getKey();
                    if (pos.getX() < min.getX() || pos.getX() > max.getX()
                            || pos.getY() < min.getY() || pos.getY() > max.getY()
                            || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) {
                        continue;
                    }
                    BlockEntity be = e.getValue();
                    String type = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()));
                    out.put(String.format("be (%d,%d,%d)", pos.getX() - min.getX(),
                                    pos.getY() - min.getY(), pos.getZ() - min.getZ()),
                            type + " " + canonical(be.saveWithoutMetadata(level.registryAccess())));
                }
            }
        }
    }

    private static void captureEntities(ServerLevel level, BlockPos origin, AABB bounds,
                                        TreeMap<String, String> out) {
        List<Entity> entities = level.getEntities((Entity) null, bounds, e -> !(e instanceof Player));
        Map<String, Integer> occurrences = new HashMap<>();
        for (Entity entity : entities) {
            occurrences.merge(describe(entity, origin), 1, Integer::sum);
        }
        out.put("entity total", Integer.toString(entities.size()));
        for (Map.Entry<String, Integer> e : occurrences.entrySet()) {
            out.put("entity " + e.getKey(), "x" + e.getValue());
        }
    }

    private static String describe(Entity e, BlockPos origin) {
        StringBuilder sb = new StringBuilder();
        sb.append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        if (e.getCustomName() != null) {
            sb.append(" '").append(e.getCustomName().getString()).append('\'');
        }
        sb.append(" pos=").append(Double.toHexString(e.getX() - origin.getX()))
                .append(',').append(Double.toHexString(e.getY() - origin.getY()))
                .append(',').append(Double.toHexString(e.getZ() - origin.getZ()));
        sb.append(" rot=").append(Float.toHexString(e.getYRot()))
                .append('/').append(Float.toHexString(e.getXRot()));
        Vec3 vel = e.getDeltaMovement();
        sb.append(" vel=").append(Double.toHexString(vel.x))
                .append(',').append(Double.toHexString(vel.y))
                .append(',').append(Double.toHexString(vel.z));
        sb.append(" age=").append(e.tickCount);
        sb.append(" ground=").append(e.onGround());
        if (e instanceof LivingEntity living) {
            sb.append(" hp=").append(Float.toHexString(living.getHealth()));
        }
        if (e instanceof ItemEntity item) {
            sb.append(" item=").append(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()))
                    .append('x').append(item.getItem().getCount());
        }
        if (e instanceof FallingBlockEntity falling) {
            sb.append(" block=").append(falling.getBlockState());
        }
        if (e.isVehicle()) {
            sb.append(" passengers=").append(e.getPassengers().size());
        }
        return sb.toString();
    }

    /**
     * Deterministic NBT rendering: compound keys sorted, list order preserved
     * (list order is semantic — inventory slots, path nodes). CompoundTag's
     * own toString iterates a HashMap, which is stable within one JVM but not
     * a contract; this is.
     */
    static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (String key : new TreeSet<>(compound.getAllKeys())) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(key).append(':').append(canonical(compound.get(key)));
            }
            return sb.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(canonical(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return String.valueOf(tag);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "<absent>";
        }
        return value.length() <= 400 ? value : value.substring(0, 400) + "...";
    }
}
