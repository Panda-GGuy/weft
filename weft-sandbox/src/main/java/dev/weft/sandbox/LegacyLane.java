package dev.weft.sandbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Phase-4 executor for Tier-2 (legacy) mod work (RFC-0001 §7.2).
 *
 * <p>Guarantee to the code running here: it executes on a single thread while
 * every other simulation worker is parked, in deterministic registration
 * order, against a fully settled world. I.e., unverified mods experience a
 * single-threaded Minecraft.
 *
 * <p>Cost accounting per source is the point: the profiler surfaces "your
 * tick is 61% mod X" so verification effort goes where it pays (RFC §7.2).
 */
public final class LegacyLane {

    public record Entry(String sourceModId, long registrationOrder, Runnable work) {}
    public record Cost(String sourceModId, long nanos) {}

    private final List<Entry> entries = new ArrayList<>();
    private long nextOrder;

    /** Registration happens at load/attach time, before ticking starts. */
    public synchronized void register(String sourceModId, Runnable work) {
        entries.add(new Entry(sourceModId, nextOrder++, work));
    }

    /** Runs all legacy work serialized; returns per-mod cost attribution. */
    public List<Cost> runAll() {
        List<Entry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparingLong(Entry::registrationOrder));
        List<Cost> costs = new ArrayList<>(ordered.size());
        for (Entry e : ordered) {
            long start = System.nanoTime();
            e.work().run();
            costs.add(new Cost(e.sourceModId(), System.nanoTime() - start));
        }
        return costs;
    }

    public int size() {
        return entries.size();
    }
}
