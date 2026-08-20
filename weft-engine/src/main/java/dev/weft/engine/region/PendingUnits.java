package dev.weft.engine.region;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Per-region pending tick-unit container (RFC-0007 §4 requirement 1: the
 * single-join tick needs "per-region BE pending containers" — RFC-0001 §4.2's
 * "each region carries its own … block-entity tick list" made literal).
 *
 * <p>Replicates vanilla's {@code blockEntityTickers} /
 * {@code pendingBlockEntityTickers} semantics, generically and per owner
 * (verified against the 1.21.1 decompile, {@code Level.tickBlockEntities}):
 *
 * <ul>
 *   <li>Units added while the container is <em>not</em> mid-tick land in the
 *       live list and run on the next {@link #tick} — which is how an
 *       entity-stage addition (sculk growth, falling-block placement) ticks
 *       the <em>same</em> tick under fusion: the fused region task runs its
 *       entity stage first (adds land live), then calls {@link #tick}.</li>
 *   <li>Units added while mid-tick (a ticking unit adds another) land in the
 *       pending list and are merged at the head of the <em>next</em>
 *       {@link #tick} — vanilla's exact deferral.</li>
 *   <li>The iteration set is fixed at {@link #tick} entry; removed units are
 *       pruned during iteration, exactly like vanilla's iterator.</li>
 * </ul>
 *
 * <p><b>Threading.</b> One container is owned by one region. {@link #add}
 * may be called from any thread (chunk load on the server thread, the owning
 * region's worker mid-stage) and synchronizes. {@link #tick} and
 * {@link #drainAll} are owner-only operations: {@code tick} runs on whichever
 * thread holds the region's context, {@code drainAll} is a topology-time
 * operation (server thread, between sections). Both fail loud on re-entry —
 * a recursive tick or a mid-tick drain is an ownership bug, not a case to
 * absorb silently (fail-loud ownership, laws §5).
 */
public final class PendingUnits<T> {

    private final List<T> units = new ArrayList<>();
    private final List<T> pending = new ArrayList<>();
    private boolean ticking;

    /**
     * Add a unit. Mid-tick additions are deferred to the next {@link #tick};
     * all other additions join the live list (and so run on the next tick
     * call, including a fused task's own BE stage later the same tick).
     */
    public synchronized void add(T unit) {
        (ticking ? pending : units).add(unit);
    }

    /**
     * Tick every live unit: merge pending, fix the iteration set, then for
     * each unit prune it when {@code removed} says so, else run
     * {@code ticker}. Owner-only; throws on re-entry.
     */
    public void tick(Predicate<T> removed, Consumer<T> ticker) {
        synchronized (this) {
            if (ticking) {
                throw new IllegalStateException(
                        "PendingUnits.tick re-entered: a ticking unit must never tick the container");
            }
            if (!pending.isEmpty()) {
                units.addAll(pending);
                pending.clear();
            }
            if (units.isEmpty()) {
                return;
            }
            ticking = true;
        }
        try {
            // Unsynchronized iteration is safe: `ticking` is true, so every
            // concurrent add() lands in `pending` (checked under the same
            // lock that set the flag); only the owner touches `units`.
            Iterator<T> iterator = units.iterator();
            while (iterator.hasNext()) {
                T unit = iterator.next();
                if (removed.test(unit)) {
                    iterator.remove();
                } else {
                    ticker.accept(unit);
                }
            }
        } finally {
            synchronized (this) {
                ticking = false;
            }
        }
    }

    /**
     * Remove and return everything (live + pending, in add order) — the
     * topology-mutation path: when chunks move between regions their units
     * must follow (the mail-model treatment of RFC-0007 §3.3, applied to tick
     * units). Owner/topology-time only; throws mid-tick.
     */
    public synchronized List<T> drainAll() {
        if (ticking) {
            throw new IllegalStateException(
                    "PendingUnits.drainAll mid-tick: topology mutations must not race the owner's tick");
        }
        List<T> out = new ArrayList<>(units.size() + pending.size());
        out.addAll(units);
        out.addAll(pending);
        units.clear();
        pending.clear();
        return out;
    }

    public synchronized boolean isEmpty() {
        return units.isEmpty() && pending.isEmpty();
    }

    /** Live + pending count (status/telemetry; approximate under concurrency). */
    public synchronized int size() {
        return units.size() + pending.size();
    }
}