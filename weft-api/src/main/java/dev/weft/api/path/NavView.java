package dev.weft.api.path;

/**
 * Passability oracle a pathfinding compute reads the world through (WS-2,
 * RFC-0002). The single method answers: can something stand at this cell,
 * and at what extra cost?
 *
 * <p>Threading contract: the view is read from a pathfinding worker thread,
 * possibly overlapping the next server tick. Implementations must therefore
 * be either an immutable snapshot captured on the owning thread, or a view
 * that is documented stale-tolerant (a momentarily out-of-date answer may
 * produce a briefly suboptimal path, never a crash or a world mutation).
 * A {@code NavView} must never mutate anything.
 */
@FunctionalInterface
public interface NavView {

    /**
     * Cost malus for standing at (x, y, z): negative = impassable, 0 = free,
     * positive = avoid-if-possible extra cost added to the step that enters
     * the cell (same shape as vanilla's pathfinding malus so adapters can
     * translate 1:1).
     */
    float malus(int x, int y, int z);
}
