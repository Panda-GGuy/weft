package dev.weft.api.path;

/**
 * One path request (WS-2, RFC-0002): from a start cell to a target cell,
 * accepting any end cell within {@code acceptRadius} of the target.
 *
 * @param startX          start cell
 * @param startY          start cell
 * @param startZ          start cell
 * @param targetX         target cell
 * @param targetY         target cell
 * @param targetZ         target cell
 * @param acceptRadius    done when within this euclidean distance of the
 *                        target (0 = exact cell)
 * @param maxVisitedNodes search budget; hitting it yields a PARTIAL path to
 *                        the closest approach (vanilla-shaped give-up rule)
 * @param maxRange        cells farther than this from the start are never
 *                        expanded (bounds memory on unreachable targets)
 */
public record PathQuery(int startX, int startY, int startZ,
                        int targetX, int targetY, int targetZ,
                        double acceptRadius, int maxVisitedNodes, double maxRange) {

    public PathQuery {
        if (acceptRadius < 0 || maxVisitedNodes < 1 || maxRange <= 0) {
            throw new IllegalArgumentException(
                    "Require acceptRadius >= 0, maxVisitedNodes >= 1, maxRange > 0");
        }
    }

    public double distStartToTarget() {
        double dx = targetX - startX, dy = targetY - startY, dz = targetZ - startZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
