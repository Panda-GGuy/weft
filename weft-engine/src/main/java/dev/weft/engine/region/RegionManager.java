package dev.weft.engine.region;

import dev.weft.engine.mail.Message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

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

    /** No regions absorbed: the overwhelmingly common chunk load. */
    private static final long[] EMPTY_IDS = new long[0];

    private final int mergeDistance;
    private final long worldSeed;
    private final AtomicLong nextRegionId = new AtomicLong(1);
    private final Map<Long, Region> chunkToRegion = new HashMap<>();
    private final Set<Region> regions = new HashSet<>();
    /**
     * Regions a removal may have disconnected. {@link #recomputeSplits} only
     * BFS-walks these: a removal whose local neighborhood stays connected
     * provably cannot split its region (see {@link #splitPossibleAfterRemoval}),
     * and under sustained load/unload churn (pregen) that is nearly every
     * removal — whole-map BFS every recompute was the dominant churn cost.
     */
    private final Set<Region> maybeSplit = new HashSet<>();

    /**
     * Where a region's queued mail goes when its mailbox can no longer be
     * trusted to reach the right owner (RFC-0007 §3.3): the region split — a
     * message routed for a position now in the split-off region would drain
     * under the parent's context, racing the new owner — or the region
     * emptied and is being dropped. Merges do NOT come here: {@link #absorb}
     * reposts the victim's mail into the absorber, which owns the victim's
     * chunks from that point on. The loader wires this to the scheduler's
     * global inbox (always-safe delivery, one tick late, rare by the churn
     * fix's split economics); the default no-op matches today's behavior for
     * managers that never carry mail (the scheduler's own id-reservation
     * manager).
     */
    private Consumer<Message> strandedMailSink = m -> {};

    /**
     * Observer of topology mutations (WS-7 / RFC-0009 §2). Merges and splits
     * were happening uncounted; the exporter needs both a rate and the discrete
     * event. {@code LongAdder} rather than a plain counter because mutation is
     * server-thread-only while the scrape reads from its own thread.
     */
    private final LongAdder merges = new LongAdder();
    private final LongAdder splits = new LongAdder();

    /**
     * Notified of each merge and split so the loader can emit the RFC-0009 §5
     * {@code region_merge}/{@code region_split} events, which carry the level
     * label this class has no way to know.
     *
     * <p>Default no-op, and the loader clears it back to no-op when the
     * observability module goes inactive: R6 wants zero residue, and a listener
     * left installed is residue.
     */
    private TopologyListener topologyListener = TopologyListener.NO_OP;

    /** Receives topology mutations; see {@link #setTopologyListener}. */
    public interface TopologyListener {

        TopologyListener NO_OP = new TopologyListener() {
            @Override
            public void onMerge(long resultId, long[] absorbedIds, int chunksAfter) {
                // Nothing observes topology by default.
            }

            @Override
            public void onSplit(long sourceId, long[] resultIds, int chunksAfter) {
                // Nothing observes topology by default.
            }
        };

        /** One chunk load bridged {@code absorbedIds} into {@code resultId}. */
        void onMerge(long resultId, long[] absorbedIds, int chunksAfter);

        /** {@code sourceId} was no longer connected and shed {@code resultIds}. */
        void onSplit(long sourceId, long[] resultIds, int chunksAfter);
    }

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
        long[] absorbed = EMPTY_IDS;
        if (neighbors.isEmpty()) {
            home = new Region(nextRegionId.getAndIncrement(), worldSeed);
            regions.add(home);
        } else {
            // Merge all neighbors into the largest (cheapest re-mapping).
            home = neighbors.stream()
                    .max((a, b) -> Integer.compare(a.chunks().size(), b.chunks().size()))
                    .orElseThrow();
            int absorbedCount = 0;
            long[] ids = new long[neighbors.size() - 1];
            for (Region other : neighbors) {
                if (other != home) {
                    ids[absorbedCount++] = other.id();
                    absorb(home, other);
                }
            }
            absorbed = absorbedCount == ids.length ? ids : Arrays.copyOf(ids, absorbedCount);
        }
        home.chunks().add(key);
        chunkToRegion.put(key, home);
        home.reseed();
        if (absorbed.length > 0) {
            // One event per bridging load, not per absorbed region: what an
            // operator wants to see is "these N became one", once.
            merges.add(absorbed.length);
            topologyListener.onMerge(home.id(), absorbed, home.chunks().size());
        }
        return home;
    }

    /**
     * Reserve a region id without creating a region: the owner identity for
     * P2 increment 1's whole-world serial region, which deliberately never
     * enters the chunk→region mapping (owner routing stays on the global
     * inbox until a later increment assigns real chunks). Reserving from the
     * same counter keeps owner ids unique across both uses.
     */
    public long reserveRegionId() {
        return nextRegionId.getAndIncrement();
    }

    /** RFC-0007 §3.3: receives queued mail from split or dropped regions. */
    public void setStrandedMailSink(Consumer<Message> sink) {
        this.strandedMailSink = sink;
    }

    /** WS-7: observe topology mutations. Pass {@code null} to detach (R6). */
    public void setTopologyListener(TopologyListener listener) {
        this.topologyListener = listener == null ? TopologyListener.NO_OP : listener;
    }

    /** Regions absorbed into another by a bridging chunk load, since construction. */
    public long merges() {
        return merges.sum();
    }

    /** Regions shed by a disconnection, since construction. */
    public long splits() {
        return splits.sum();
    }

    /** Unload a chunk. Caller should run {@link #recomputeSplits()} afterwards. */
    public void removeChunk(int chunkX, int chunkZ) {
        long key = ChunkKey.pack(chunkX, chunkZ);
        Region r = chunkToRegion.remove(key);
        if (r != null) {
            r.chunks().remove(key);
            if (r.chunks().isEmpty()) {
                regions.remove(r);
                maybeSplit.remove(r);
                r.mailbox().drain().forEach(strandedMailSink);
            } else {
                r.reseed();
                if (!maybeSplit.contains(r) && splitPossibleAfterRemoval(chunkX, chunkZ)) {
                    maybeSplit.add(r);
                }
            }
        }
    }

    /**
     * Split any region whose chunks are no longer a single connected
     * component under the mergeDistance adjacency. Runs between ticks.
     * Only regions flagged by a possibly-disconnecting removal are walked;
     * everything else is provably still connected.
     */
    public void recomputeSplits() {
        if (maybeSplit.isEmpty()) {
            return;
        }
        List<Region> snapshot = new ArrayList<>(maybeSplit);
        maybeSplit.clear();
        for (Region r : snapshot) {
            List<Set<Long>> components = connectedComponents(r.chunks());
            if (components.size() <= 1) {
                continue;
            }
            // Queued mail can no longer be matched to the component that owns
            // its target position (Message.Task is opaque), so all of it goes
            // to the stranded sink — conservative, always-safe, and rare
            // (RFC-0007 §3.3 hazard 2). The split regions start with the
            // empty mailboxes their constructor gives them.
            r.mailbox().drain().forEach(strandedMailSink);
            // Keep the largest component in place; move the rest out.
            components.sort((a, b) -> Integer.compare(b.size(), a.size()));
            long[] resultIds = new long[components.size() - 1];
            for (int i = 1; i < components.size(); i++) {
                Region split = new Region(nextRegionId.getAndIncrement(), worldSeed);
                resultIds[i - 1] = split.id();
                regions.add(split);
                for (long key : components.get(i)) {
                    r.chunks().remove(key);
                    split.chunks().add(key);
                    chunkToRegion.put(key, split);
                }
                split.reseed();
            }
            r.reseed();
            splits.add(resultIds.length);
            topologyListener.onSplit(r.id(), resultIds, r.chunks().size());
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

    /** Loaded chunks currently mapped to a region. */
    public int chunkCount() {
        return chunkToRegion.size();
    }

    /** Regions the next {@link #recomputeSplits} will walk (test hook). */
    int pendingSplitChecks() {
        return maybeSplit.size();
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
        // A possibly-disconnected victim stays possibly-disconnected inside
        // its absorber (the merge chunk bridges to one component, not all).
        if (maybeSplit.remove(victim)) {
            maybeSplit.add(into);
        }
    }

    /**
     * Local no-split proof for {@link #removeChunk}: every adjacency edge the
     * removed chunk carried ran to chunks within {@code mergeDistance} of it.
     * If those chunks are still connected to each other through chunks inside
     * that same window, every path that used the removed chunk reroutes, so
     * the region cannot have split. The window flood fill uses unit steps
     * (8-neighbor) — stricter than mergeDistance adjacency, so a {@code true}
     * here may be a false alarm (recomputeSplits then finds one component and
     * keeps the region), but {@code false} is always safe.
     */
    private boolean splitPossibleAfterRemoval(int chunkX, int chunkZ) {
        int size = 2 * mergeDistance + 1;
        boolean[] occupied = new boolean[size * size];
        int found = 0;
        int first = -1;
        for (int dx = -mergeDistance; dx <= mergeDistance; dx++) {
            for (int dz = -mergeDistance; dz <= mergeDistance; dz++) {
                if (chunkToRegion.containsKey(ChunkKey.pack(chunkX + dx, chunkZ + dz))) {
                    int idx = (dx + mergeDistance) * size + (dz + mergeDistance);
                    occupied[idx] = true;
                    if (first < 0) {
                        first = idx;
                    }
                    found++;
                }
            }
        }
        if (found <= 1) {
            return false; // No pair of neighbors could have lost a path.
        }
        int[] stack = new int[found];
        int top = 0;
        stack[top++] = first;
        occupied[first] = false;
        int reached = 1;
        while (top > 0) {
            int cur = stack[--top];
            int cx = cur / size;
            int cz = cur % size;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int nx = cx + dx;
                    int nz = cz + dz;
                    if (nx < 0 || nx >= size || nz < 0 || nz >= size) {
                        continue;
                    }
                    int n = nx * size + nz;
                    if (occupied[n]) {
                        occupied[n] = false;
                        stack[top++] = n;
                        reached++;
                    }
                }
            }
        }
        return reached < found;
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
