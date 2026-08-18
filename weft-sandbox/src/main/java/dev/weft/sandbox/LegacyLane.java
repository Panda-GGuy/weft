package dev.weft.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/**
 * Phase-4 executor for Tier-2 (legacy) mod work (RFC-0001 §7.2).
 *
 * <p>Guarantee to the code running here: it executes on a single thread while
 * every other simulation worker is parked, in deterministic order, against a
 * fully settled world. I.e., unverified mods experience a single-threaded
 * Minecraft.
 *
 * <p>Two kinds of work flow through the lane:
 * <ul>
 *   <li><b>Registered</b> entries — recurring per-tick work attached at
 *       load/attach time (RFC §7.1 scheduled work). Run every tick in
 *       registration order.</li>
 *   <li><b>Submitted</b> one-shots — tick work extracted from the vanilla
 *       sections this tick (a legacy block entity's or entity's tick),
 *       drained FIFO, i.e. in vanilla's own iteration order. Work submitted
 *       while the lane is draining runs in the <em>next</em> drain, so a
 *       self-submitting unit cannot wedge the phase.</li>
 * </ul>
 *
 * <p>Ordering is deterministic today because submissions come from exactly
 * one thread (the tick sections run serially on the server thread). The
 * parallel-regions increment must replace FIFO with a (regionId,
 * submissionIndex) sort before region workers become submitters.
 *
 * <p>Cost accounting per source is the point: the profiler surfaces "your
 * tick is 61% mod X" so verification effort goes where it pays (RFC §7.2).
 * A throwing unit still gets its cost attributed before the throw propagates
 * (vanilla semantics: a crashing tick crashes the server, with the mod named
 * in the lane's accounting).
 */
public final class LegacyLane {

    private record Entry(String sourceModId, long regionOrder, long seq, Runnable work) {}

    private final List<Entry> registered = new ArrayList<>();
    private final ConcurrentLinkedQueue<Entry> submitted = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicLong submitSeq =
            new java.util.concurrent.atomic.AtomicLong();
    private final ConcurrentHashMap<String, LongAdder> nanosByMod = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> unitsByMod = new ConcurrentHashMap<>();
    private final LongAdder unitsRun = new LongAdder();
    private volatile long lastTickNanos;

    /** Recurring work, attached at load/attach time, before ticking starts. */
    public synchronized void register(String sourceModId, Runnable work) {
        registered.add(new Entry(sourceModId, 0, registered.size(), work));
    }

    /** One-shot tick work extracted from a vanilla section; runs next drain. */
    public void submit(String sourceModId, Runnable work) {
        submit(sourceModId, 0, work);
    }

    /**
     * One-shot with an explicit ordering group (P2 E1, RFC-0006 hazard 16):
     * parallel region buckets submit concurrently, so FIFO alone is
     * scheduling-dependent. The drain sorts by (regionOrder, submission
     * seq) — within a region the single bucket thread keeps vanilla order,
     * across regions the canonical region order rules, deterministically.
     */
    public void submit(String sourceModId, long regionOrder, Runnable work) {
        submitted.add(new Entry(sourceModId, regionOrder, submitSeq.getAndIncrement(), work));
    }

    /**
     * Runs one lane pass: registered entries in registration order, then the
     * one-shots queued before this call in submission order. Caller owns the
     * single-thread guarantee (the scheduler's LEGACY phase).
     *
     * @return units executed this pass
     */
    public int runTick() {
        long start = System.nanoTime();
        int units = 0;
        try {
            synchronized (this) {
                for (Entry e : registered) {
                    runAttributed(e);
                    units++;
                }
            }
            // Snapshot the count first: units submitted while draining belong
            // to the next tick's pass. Then order deterministically: within a
            // region, submission order (vanilla iteration order); across
            // regions, canonical region order (RFC-0006 hazard 16).
            int oneShots = submitted.size();
            List<Entry> pass = new ArrayList<>(oneShots);
            for (int i = 0; i < oneShots; i++) {
                Entry e = submitted.poll();
                if (e == null) {
                    break;
                }
                pass.add(e);
            }
            pass.sort(java.util.Comparator.comparingLong(Entry::regionOrder)
                    .thenComparingLong(Entry::seq));
            for (Entry e : pass) {
                runAttributed(e);
                units++;
            }
        } finally {
            unitsRun.add(units);
            lastTickNanos = System.nanoTime() - start;
        }
        return units;
    }

    private void runAttributed(Entry e) {
        long start = System.nanoTime();
        try {
            e.work().run();
        } finally {
            nanosByMod.computeIfAbsent(e.sourceModId(), k -> new LongAdder())
                    .add(System.nanoTime() - start);
            unitsByMod.computeIfAbsent(e.sourceModId(), k -> new LongAdder()).increment();
        }
    }

    /** One-shots queued and not yet drained. */
    public int pending() {
        return submitted.size();
    }

    /** Units executed since creation (registered runs + one-shots). */
    public long unitsRun() {
        return unitsRun.sum();
    }

    /** Wall time of the most recent {@link #runTick} pass. */
    public long lastTickNanos() {
        return lastTickNanos;
    }

    /** Lifetime cost per source mod, stable iteration order (status/report). */
    public Map<String, Long> costByModNanos() {
        Map<String, Long> out = new TreeMap<>();
        nanosByMod.forEach((mod, adder) -> out.put(mod, adder.sum()));
        return out;
    }

    /** Lifetime executed units per source mod. */
    public Map<String, Long> unitsByMod() {
        Map<String, Long> out = new TreeMap<>();
        unitsByMod.forEach((mod, adder) -> out.put(mod, adder.sum()));
        return out;
    }

    /** Server stop: drop queued work (its world is being torn down with it). */
    public synchronized void clear() {
        registered.clear();
        submitted.clear();
    }
}
