package dev.weft.api.graph;

/**
 * The compute step of a registered graph (RFC-0001 §5.2).
 *
 * <p>Called once per pipeline tick, in parallel with region ticking and other
 * graphs. Implementations may read only:
 * <ul>
 *   <li>their own internal graph state (they own it), and</li>
 *   <li>the {@link WorldSnapshot} handed in (the settled pre-tick world).</li>
 * </ul>
 * All world mutation goes through the {@link CommitLog}; it is applied in the
 * commit phase by the owning regions, in deterministic graph-priority order.
 */
@FunctionalInterface
public interface GraphTicker {
    void tick(WorldSnapshot snapshot, CommitLog commits);
}
