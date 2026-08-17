package dev.weft.neoforge.service;

import dev.weft.api.service.AsyncService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PotentialCalculator;

import java.util.HashMap;
import java.util.Map;

/**
 * First {@link AsyncService} consumer (RFC-0001 §11 P1): recompute the mob
 * spawn-density state off-thread. In SHADOW mode vanilla's
 * {@code NaturalSpawner.createState} still runs and stays authoritative and
 * this service's output is only diffed against it; in AUTHORITATIVE mode the
 * output is handed to vanilla as a real {@code SpawnState}
 * ({@link SpawnDensityHooks}), so the result must carry everything
 * {@code createState} produces:
 *
 * <ul>
 *   <li>global per-category counts (all non-MISC entities),</li>
 *   <li>per-chunk per-category counts of {@code Mob}s only — vanilla feeds
 *       only {@code instanceof Mob} into the {@code LocalMobCapCalculator},</li>
 *   <li>the spawn-potential point charges ({@code MobSpawnCost} biomes:
 *       ghast/enderman-style charge fields).</li>
 * </ul>
 *
 * <p>The input snapshot is plain primitives (category ordinals, packed chunk
 * keys, packed block positions, charge values) captured on the server
 * thread; compute never touches live world state, per the AsyncService
 * contract. The {@link PotentialCalculator} built here is pure math over the
 * snapshot and is handed off exactly once (take-once in the consumer) since
 * vanilla mutates it during spawning ({@code afterSpawn}).
 */
public final class SpawnDensityService
        implements AsyncService<SpawnDensityService.Snapshot, SpawnDensityService.Densities> {

    /**
     * Immutable tick-end capture. {@code categories[i]} is the mob-category
     * ordinal of entity i (cap-exempt and MISC entities are excluded at
     * capture, mirroring createState's skip rules); {@code chunkKeys[i]} is
     * its packed chunk position; {@code mobs[i]} is whether the entity is a
     * {@code Mob} (local-cap relevance). Charges are a separate parallel
     * pair: packed block positions and charge values for the (usually few)
     * entities whose type has a {@code MobSpawnCost} in their biome. Arrays
     * may be oversized; {@code size}/{@code chargeCount} are the live prefix
     * lengths.
     */
    public record Snapshot(int categoryCount, int[] categories, long[] chunkKeys, boolean[] mobs,
                           int size, long[] chargePositions, double[] charges, int chargeCount) {}

    /**
     * Global and per-chunk mob counts by category ordinal, plus the spawn
     * potential. {@code perChunk} counts every captured entity (shadow-diff
     * view); {@code perChunkMobs} counts only {@code Mob}s (what vanilla
     * replays into the {@code LocalMobCapCalculator}).
     */
    public record Densities(int[] globalByCategory, Map<Long, int[]> perChunk,
                            Map<Long, int[]> perChunkMobs, PotentialCalculator potential) {

        public int global(int categoryOrdinal) {
            return globalByCategory[categoryOrdinal];
        }

        public int inChunk(long chunkKey, int categoryOrdinal) {
            int[] counts = perChunk.get(chunkKey);
            return counts == null ? 0 : counts[categoryOrdinal];
        }
    }

    private final String id;

    public SpawnDensityService(String levelId) {
        this.id = "weft:spawn_density/" + levelId;
    }

    @Override
    public String serviceId() {
        return id;
    }

    @Override
    public Densities compute(Snapshot in) {
        int[] global = new int[in.categoryCount()];
        Map<Long, int[]> perChunk = new HashMap<>();
        Map<Long, int[]> perChunkMobs = new HashMap<>();
        for (int i = 0; i < in.size(); i++) {
            int cat = in.categories()[i];
            global[cat]++;
            perChunk.computeIfAbsent(in.chunkKeys()[i], k -> new int[in.categoryCount()])[cat]++;
            if (in.mobs()[i]) {
                perChunkMobs.computeIfAbsent(in.chunkKeys()[i],
                        k -> new int[in.categoryCount()])[cat]++;
            }
        }
        PotentialCalculator potential = new PotentialCalculator();
        for (int i = 0; i < in.chargeCount(); i++) {
            potential.addCharge(BlockPos.of(in.chargePositions()[i]), in.charges()[i]);
        }
        return new Densities(global, perChunk, perChunkMobs, potential);
    }
}
