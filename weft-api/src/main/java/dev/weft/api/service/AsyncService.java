package dev.weft.api.service;

/**
 * A read-mostly query system computed off the critical tick path (RFC-0001
 * §2 "Pufferfish/Petal" row, §11 P1): spawn-density scanning, pathfinding,
 * visibility/tracker maps — anything that derives data from world state
 * without mutating it.
 *
 * <p>Execution contract (the service-side mirror of the graph layer's
 * snapshot → compute → commit):
 * <ul>
 * <li><b>Snapshot:</b> the owner thread captures an immutable {@code I} at a
 *     safe point (typically tick end). {@link #compute} must touch only that
 *     input — never live world state.</li>
 * <li><b>Compute:</b> runs on a service worker, possibly overlapping the
 *     next tick.</li>
 * <li><b>Publish:</b> the result becomes visible atomically to later readers.
 *     Consumers accept one tick (or more, under load) of staleness — the
 *     same latency vanilla already exhibits for most cross-chunk
 *     observation.</li>
 * </ul>
 *
 * Refreshes coalesce: if a compute is still running when new inputs arrive,
 * intermediate inputs are dropped and only the latest is computed. A service
 * must therefore treat each input as a complete world view, not a delta.
 *
 * @param <I> immutable input snapshot
 * @param <R> immutable computed result
 */
public interface AsyncService<I, R> {

    /** Stable identity, for telemetry and scheduling. */
    String serviceId();

    /**
     * Derive the result from the input snapshot. Runs off-thread; must be a
     * pure function of {@code input}. A thrown exception keeps the previous
     * published result (callers can inspect failure counts via the runner).
     */
    R compute(I input);
}
