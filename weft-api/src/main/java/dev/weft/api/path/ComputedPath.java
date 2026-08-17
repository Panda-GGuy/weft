package dev.weft.api.path;

/**
 * The immutable result of one {@link PathQuery} (WS-2, RFC-0002).
 *
 * @param status       how the search ended
 * @param nodes        path cells as packed (x, y, z) triples, start first,
 *                     end last; empty for UNREACHABLE
 * @param visitedNodes how many cells the search expanded (telemetry)
 * @param computeNanos wall time of the compute (telemetry)
 */
public record ComputedPath(Status status, int[] nodes, int visitedNodes, long computeNanos) {

    public enum Status {
        /** Reached within the query's accept radius. */
        FOUND,
        /** Budget exhausted; path leads to the closest approach found. */
        PARTIAL,
        /** Search space exhausted without reaching the target. */
        UNREACHABLE
    }

    public ComputedPath {
        if (nodes.length % 3 != 0) {
            throw new IllegalArgumentException("nodes must be (x,y,z) triples");
        }
    }

    public int nodeCount() {
        return nodes.length / 3;
    }

    public int x(int node) {
        return nodes[node * 3];
    }

    public int y(int node) {
        return nodes[node * 3 + 1];
    }

    public int z(int node) {
        return nodes[node * 3 + 2];
    }
}
