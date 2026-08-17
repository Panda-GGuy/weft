package dev.weft.neoforge.service;

import dev.weft.api.service.AsyncService;

import java.util.HashMap;
import java.util.Map;

/**
 * First {@link AsyncService} consumer (RFC-0001 §11 P1): recompute the mob
 * spawn-density state off-thread. Currently SHADOW MODE — vanilla's
 * {@code NaturalSpawner.createState} still runs and stays authoritative;
 * this service computes the same global/per-chunk category counts from a
 * tick-end snapshot so we can (a) prove the parity of a one-tick-stale
 * density map on real workloads and (b) measure what going authoritative
 * would save (the timing mixin on createState reports that number).
 *
 * <p>The input snapshot is plain primitives (category ordinals + packed
 * chunk keys) captured on the server thread; compute never touches live
 * world state, per the AsyncService contract.
 */
public final class SpawnDensityService
        implements AsyncService<SpawnDensityService.Snapshot, SpawnDensityService.Densities> {

    /**
     * Immutable tick-end capture. {@code categories[i]} is the mob-category
     * ordinal of entity i (cap-exempt and MISC entities are excluded at
     * capture, mirroring createState's skip rules); {@code chunkKeys[i]} is
     * its packed chunk position. Arrays may be oversized; {@code size} is
     * the live prefix length.
     */
    public record Snapshot(int categoryCount, int[] categories, long[] chunkKeys, int size) {}

    /** Global and per-chunk mob counts by category ordinal. */
    public record Densities(int[] globalByCategory, Map<Long, int[]> perChunk) {

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
        for (int i = 0; i < in.size(); i++) {
            int cat = in.categories()[i];
            global[cat]++;
            perChunk.computeIfAbsent(in.chunkKeys()[i], k -> new int[in.categoryCount()])[cat]++;
        }
        return new Densities(global, perChunk);
    }
}
