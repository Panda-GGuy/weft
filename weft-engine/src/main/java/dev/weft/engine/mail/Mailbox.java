package dev.weft.engine.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Multi-producer, single-consumer message queue attached to every owner
 * (region, graph, global lane). Producers may post from any phase; the owner
 * drains at its next phase boundary.
 *
 * <p>Ordering guarantee (RFC-0001 §8.2): FIFO per sender-receiver pair.
 * A {@link ConcurrentLinkedQueue} gives us global FIFO per receiver, which is
 * strictly stronger; if this queue ever becomes the bottleneck and is replaced
 * by per-sender lanes, the per-pair guarantee is the one tests enforce.
 */
public final class Mailbox<M> {
    private final Queue<M> queue = new ConcurrentLinkedQueue<>();

    /** Post from any thread. Never blocks. */
    public void post(M message) {
        queue.add(message);
    }

    /**
     * Drain all currently-visible messages. Must only be called by the
     * owner's current worker at a phase boundary.
     */
    public List<M> drain() {
        List<M> out = new ArrayList<>();
        M m;
        while ((m = queue.poll()) != null) {
            out.add(m);
        }
        return out;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
