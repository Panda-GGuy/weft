package dev.weft.engine.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Incremental entity census: category counts, globally and per chunk,
 * maintained from add/remove/move events instead of a per-tick full scan
 * (P1: the O(population) tick-end capture measured 16 ms during a 65k-item
 * stress test; event bookkeeping is O(changes)).
 *
 * <p>Threading: mutations and reads are owner-thread-only (the server
 * thread today, a region worker under P2 ownership); {@link #snapshot}
 * returns an immutable copy safe to hand to async consumers.
 *
 * <p>Drift and reconciliation: some membership changes fire no event in
 * the host game (e.g. a mob becoming persistence-exempt when tamed or
 * name-tagged), so an event-fed census drifts. {@link #reconcile} replaces
 * the census with an authoritative full-scan listing and reports how wrong
 * the incremental state was — callers run it periodically and use the
 * reported drift to decide whether the census can be trusted as an
 * authoritative input (correctness is never opt-in, RFC-0001 tenet 3).
 */
public final class EntityCensus {

    /** What one tracked entity contributes: its category and current chunk. */
    private record Membership(int category, long chunkKey) {}

    /** Immutable counts view. */
    public record Counts(int[] globalByCategory, Map<Long, int[]> perChunk) {
        public int global(int category) {
            return globalByCategory[category];
        }

        public int inChunk(long chunkKey, int category) {
            int[] counts = perChunk.get(chunkKey);
            return counts == null ? 0 : counts[category];
        }
    }

    /** Result of a reconciliation pass. */
    public record Drift(int missing, int stale, int moved) {
        public int total() {
            return missing + stale + moved;
        }
    }

    private final int categoryCount;
    private final Map<Integer, Membership> members = new HashMap<>();
    private final int[] global;
    private final Map<Long, int[]> perChunk = new HashMap<>();

    public EntityCensus(int categoryCount) {
        this.categoryCount = categoryCount;
        this.global = new int[categoryCount];
    }

    /** Entity began counting toward caps. Idempotent per id (re-add replaces). */
    public void add(int entityId, int category, long chunkKey) {
        remove(entityId);
        members.put(entityId, new Membership(category, chunkKey));
        global[category]++;
        perChunk.computeIfAbsent(chunkKey, k -> new int[categoryCount])[category]++;
    }

    /** Entity stopped counting (left the level, died, or was never tracked). */
    public void remove(int entityId) {
        Membership m = members.remove(entityId);
        if (m != null) {
            global[m.category()]--;
            int[] counts = perChunk.get(m.chunkKey());
            if (counts != null && --counts[m.category()] == 0 && allZero(counts)) {
                perChunk.remove(m.chunkKey());
            }
        }
    }

    /** Tracked entity crossed into another chunk. No-op for untracked ids. */
    public void move(int entityId, long newChunkKey) {
        Membership m = members.get(entityId);
        if (m == null || m.chunkKey() == newChunkKey) {
            return;
        }
        add(entityId, m.category(), newChunkKey);
    }

    public int trackedCount() {
        return members.size();
    }

    /** Immutable copy of the current counts. */
    public Counts snapshot() {
        Map<Long, int[]> chunkCopy = new HashMap<>(perChunk.size());
        perChunk.forEach((k, v) -> chunkCopy.put(k, v.clone()));
        return new Counts(global.clone(), Map.copyOf(chunkCopy));
    }

    /**
     * Replace the census with the authoritative listing (parallel arrays of
     * live entities: id, category, chunk; {@code size} is the live prefix)
     * and report how far the incremental state had drifted:
     * {@code missing} = in truth but not census, {@code stale} = in census
     * but not truth, {@code moved} = tracked but with wrong category/chunk.
     */
    public Drift reconcile(int[] ids, int[] categories, long[] chunkKeys, int size) {
        int missing = 0;
        int stale = 0;
        int moved = 0;

        Map<Integer, Membership> truth = new HashMap<>(size * 2);
        for (int i = 0; i < size; i++) {
            truth.put(ids[i], new Membership(categories[i], chunkKeys[i]));
        }
        for (Map.Entry<Integer, Membership> e : truth.entrySet()) {
            Membership tracked = members.get(e.getKey());
            if (tracked == null) {
                missing++;
            } else if (!tracked.equals(e.getValue())) {
                moved++;
            }
        }
        for (Integer id : members.keySet()) {
            if (!truth.containsKey(id)) {
                stale++;
            }
        }

        members.clear();
        java.util.Arrays.fill(global, 0);
        perChunk.clear();
        for (Map.Entry<Integer, Membership> e : truth.entrySet()) {
            Membership m = e.getValue();
            members.put(e.getKey(), m);
            global[m.category()]++;
            perChunk.computeIfAbsent(m.chunkKey(), k -> new int[categoryCount])[m.category()]++;
        }
        return new Drift(missing, stale, moved);
    }

    private static boolean allZero(int[] counts) {
        for (int c : counts) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }
}
