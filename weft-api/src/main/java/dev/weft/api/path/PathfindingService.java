package dev.weft.api.path;

import java.util.function.Consumer;

/**
 * Off-thread pathfinding (WS-2, RFC-0002; the RFC-0001 §11 P1 service).
 *
 * <p>Execution contract — the service-side mirror of the graph layer's
 * snapshot → compute → commit, same as {@link dev.weft.api.service.AsyncService}:
 * <ul>
 * <li><b>Submit:</b> any thread may submit; the call never blocks beyond a
 *     queue insert. Submissions are single-flight per {@code requesterKey}
 *     (one in-flight compute per requester): a newer submission supersedes a
 *     queued older one, which is dropped without delivery — a requester must
 *     treat every submission as a complete request, never a delta.</li>
 * <li><b>Compute:</b> runs on a pathfinding worker, reading only the
 *     {@link NavView} it was given.</li>
 * <li><b>Deliver:</b> the callback is invoked exactly once per computed
 *     request, on the requester's owning thread, at a tick boundary — the
 *     engine routes it through the ownership-respecting mailbox path
 *     (RFC-0001 §4.1), never a cross-thread call into simulation state.
 *     An engine failure during compute is counted and not delivered, so
 *     callers must tolerate a submission that never calls back (coalescing
 *     already implies that).</li>
 * </ul>
 */
public interface PathfindingService {

    /** Stable identity, for telemetry and the R5 status report. */
    String serviceId();

    /**
     * Request a path. {@code requesterKey} identifies the requester (e.g.
     * an entity id) for single-flight coalescing and owner routing.
     */
    void submit(long requesterKey, PathQuery query, NavView view, Consumer<ComputedPath> deliver);

    /** Drop any queued (not yet computing) request for this requester. */
    boolean cancel(long requesterKey);
}
