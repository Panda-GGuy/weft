package dev.weft.engine.region;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the chunk→region mapping. All mutation of the mapping happens between
 * ticks on the coordinator thread (never mid-tick), so plain collections are
 * correct here by construction.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Every loaded chunk belongs to exactly one region.</li>
 *   <li>Two chunks within {@code mergeDistance} (Chebyshev) are always in the
 *       same region (merge-on-proximity).</li>
 *   <li>After {@link #recomputeSplits}, every region is a single connected
 *       component under the mergeDistance adjacency relation.</li>
 * </ul>
 */
public final class RegionManager {

    private final int mergeDistance;
    private final long worldSeed;
    private final AtomicLong nextRegionId = new AtomicLong(1);
    private final Map<Long, Region> chunkToRegion = new HashMap<>();
    private final Set<Region> regions = new HashSet<>();

    public RegionManager(int mergeDistance, long worldSeed) {
        if (mergeDistance < 1) {
            throw new IllegalArgumentException("mergeDistance must be >= 1");
        }
        this.mergeDistance = mergeDistance;
        this.worldSeed = worldSeed;
    }

    /** Load a chunk into the world, merging any regions it now bridges. */
    public Region addChunk(int chunkX, int chunkZ) {
        long key = ChunkKey.pack(chunkX, chunkZ);
        Region existing = chunkToRegion.get(key);
        if (existing != null) {
            return existing;
        }

        // Find all distinct regions within merge distance.
        Set<Region> neighbors = new HashSet<>();
        for (int dx = -mergeDistance; dx <= mergeDistance; dx++) {
            for (int dz = -mergeDistance; dz <= mergeDistance; dz++) {
                Region r = chunkToRegion.get(ChunkKey.pack(chunkX + dx, chunkZ + dz));
                if (r != null) {
                    neighbors.add(r);
                }
            }
        }

        Region home;
        if (neighbors.isEmpty()) {
            home = new Region(nextRegionId.getAndIncrement(), worldSeed);
            regions.add(home);
        } else {
            // Merge all neighbors into the largest (cheapest re-mapping).
            home = neighbors.stream()
                    .max((a, b) -> Integer.compare(a.chunks().size(), b.chunks().size()))
                    .orElseThrow();
            for (Region other : neighbors) {
                if (other != home) {
                    absorb(home, other);
                }
            }
        }
        home.chunks().add(key);
        chunkToRegion.put(key, home);
        home.reseed();
        return home;
    }

    /** Unload a chunk. Caller should run {@link #recomputeSplits()} afterwards. */
    public void removeChunk(int chunkX, int chunkZ) {
        long key = ChunkKey.pack(chunkX, chunkZ);
        Region r = chunkToRegion.remove(key);
        if (r != null) {
            r.chunks().remove(key);
            if (r.chunks().isEmpty()) {
                regions.remove(r);
            } else {
                r.reseed();
            }
        }
    }

    /**
     * Split any region whose chunks are no longer a single connected
     * component under the mergeDistance adjacency. Runs between ticks.
     */
    public void recomputeSplits() {
        List<Region> snapshot = new ArrayList<>(regions);
        for (Region r : snapshot) {
            List<Set<Long>> components = connectedComponents(r.chunks());
            if (components.size() <= 1) {
                continue;
            }
            // Keep the largest component in place; move the rest out.
            components.sort((a, b) -> Integer.compare(b.size(), a.size()));
            for (int i = 1; i < components.size(); i++) {
                Region split = new Region(nextRegionId.getAndIncrement(), worldSeed);
                regions.add(split);
                for (long key : components.get(i)) {
                    r.chunks().remove(key);
                    split.chunks().add(key);
                    chunkToRegion.put(key, split);
                }
                split.reseed();
            }
            r.reseed();
        }
    }

    public Region regionAt(int chunkX, int chunkZ) {
        return chunkToRegion.get(ChunkKey.pack(chunkX, chunkZ));
    }

    public Region regionAtBlock(int blockX, int blockZ) {
        return chunkToRegion.get(ChunkKey.fromBlock(blockX, blockZ));
    }

    public Collection<Region> all() {
        return regions;
    }

    private void absorb(Region into, Region victim) {
        for (long key : victim.chunks()) {
            chunkToRegion.put(key, into);
        }
        into.chunks().addAll(victim.chunks());
        // Mail and tickables follow the chunks.
        victim.mailbox().drain().forEach(into.mailbox()::post);
        victim.tickables().forEach(into::addTickable);
        regions.remove(victim);
    }

    private List<Set<Long>> connectedComponents(Set<Long> chunks) {
        List<Set<Long>> components = new ArrayList<>();
        Set<Long> unvisited = new HashSet<>(chunks);
        while (!unvisited.isEmpty()) {
            long start = unvisited.iterator().next();
            Set<Long> component = new HashSet<>();
            Deque<Long> frontier = new ArrayDeque<>();
            frontier.add(start);
            unvisited.remove(start);
            while (!frontier.isEmpty()) {
                long cur = frontier.poll();
                component.add(cur);
                int cx = ChunkKey.x(cur);
                int cz = ChunkKey.z(cur);
                for (int dx = -mergeDistance; dx <= mergeDistance; dx++) {
                    for (int dz = -mergeDistance; dz <= mergeDistance; dz++) {
                        long n = ChunkKey.pack(cx + dx, cz + dz);
                        if (unvisited.remove(n)) {
                            frontier.add(n);
                        }
                    }
                }
            }
            components.add(component);
        }
        return components;
    }
}
