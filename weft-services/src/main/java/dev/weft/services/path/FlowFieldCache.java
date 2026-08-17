package dev.weft.services.path;

import dev.weft.api.path.NavView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares {@link FlowField}s across requesters (WS-2, RFC-0002): the raid /
 * farm / horde case where hundreds of mobs path to the same place. Fields
 * are keyed by target cell and expire after {@code ttlTicks}, which bounds
 * both staleness (world changes re-flood within a couple of seconds) and
 * memory (plus a hard entry cap, evicting the oldest).
 *
 * <p>Thread-safe; typical use is compute-on-miss from pathfinding workers.
 */
public final class FlowFieldCache {

    private record CachedField(FlowField field, long computedTick) {}

    private final int ttlTicks;
    private final int maxEntries;
    private final Map<Long, CachedField> byTarget = new ConcurrentHashMap<>();

    public FlowFieldCache(int ttlTicks, int maxEntries) {
        if (ttlTicks < 1 || maxEntries < 1) {
            throw new IllegalArgumentException("Require ttlTicks >= 1, maxEntries >= 1");
        }
        this.ttlTicks = ttlTicks;
        this.maxEntries = maxEntries;
    }

    /**
     * The cached field for this target, recomputed via {@link FlowField#compute}
     * when absent or older than the TTL.
     */
    public FlowField get(int targetX, int targetY, int targetZ, long currentTick,
                         NavView view, int radius, int maxCells) {
        long key = GridPathfinder.pack(targetX, targetY, targetZ);
        CachedField cached = byTarget.get(key);
        if (cached != null && currentTick - cached.computedTick() < ttlTicks) {
            return cached.field();
        }
        FlowField fresh = FlowField.compute(targetX, targetY, targetZ, view, radius, maxCells);
        byTarget.put(key, new CachedField(fresh, currentTick));
        evictIfOver(currentTick);
        return fresh;
    }

    public int size() {
        return byTarget.size();
    }

    private void evictIfOver(long currentTick) {
        if (byTarget.size() <= maxEntries) {
            return;
        }
        // Drop expired entries first, then oldest-computed until under cap.
        byTarget.values().removeIf(c -> currentTick - c.computedTick() >= ttlTicks);
        while (byTarget.size() > maxEntries) {
            Long oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Long, CachedField> e : byTarget.entrySet()) {
                if (e.getValue().computedTick() < oldest) {
                    oldest = e.getValue().computedTick();
                    oldestKey = e.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            byTarget.remove(oldestKey);
        }
    }
}
