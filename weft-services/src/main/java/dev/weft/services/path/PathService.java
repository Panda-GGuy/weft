package dev.weft.services.path;

import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.NavView;
import dev.weft.api.path.PathQuery;
import dev.weft.api.path.PathfindingService;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The WS-2 pathfinding runtime (RFC-0002): a small dedicated worker pool
 * that computes path requests off the tick path and hands each result back
 * through an owner-post channel so it is applied on the owning thread at a
 * tick boundary — the RFC-0001 §4.1 mailbox discipline; this class never
 * calls a deliver callback on a worker thread.
 *
 * <p>Two entry points share the pool, the per-requester single-flight
 * coalescing, and the stats:
 * <ul>
 * <li>{@link #submit}: the pure {@link PathfindingService} form — the
 *     engine's {@link HierarchicalPathfinder} over a caller {@link NavView}.
 *     This is the P2-native path (and what tests/benchmarks exercise).</li>
 * <li>{@link #submitTask}: a raw compute form for the loader adapter, which
 *     runs vanilla's own evaluator off-thread for exact behavior parity
 *     with modded {@code NodeEvaluator} overrides (WS-2 compat posture).</li>
 * </ul>
 */
public final class PathService implements PathfindingService, AutoCloseable {

    /** Routes a runnable to the requester's owning thread (mailbox post). */
    @FunctionalInterface
    public interface OwnerPost {
        void post(long requesterKey, Runnable task);
    }

    private static final class Job {
        final long key;
        final Supplier<Runnable> computeThenDeliver;
        volatile boolean superseded;

        Job(long key, Supplier<Runnable> computeThenDeliver) {
            this.key = key;
            this.computeThenDeliver = computeThenDeliver;
        }
    }

    private final String serviceId;
    private final OwnerPost ownerPost;
    private final HierarchicalPathfinder pathfinder = new HierarchicalPathfinder();
    private final LinkedBlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Long, Job> latestByKey = new ConcurrentHashMap<>();
    private final Thread[] workers;
    private volatile boolean closed;

    private final LongAdder submitted = new LongAdder();
    private final LongAdder coalesced = new LongAdder();
    private final LongAdder computed = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private volatile long lastComputeNanos;

    public PathService(String serviceId, int threads, OwnerPost ownerPost) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1");
        }
        this.serviceId = serviceId;
        this.ownerPost = ownerPost;
        this.workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(this::workLoop, "weft-path-" + i);
            workers[i].setDaemon(true);
            workers[i].start();
        }
    }

    @Override
    public String serviceId() {
        return serviceId;
    }

    @Override
    public void submit(long requesterKey, PathQuery query, NavView view,
                       Consumer<ComputedPath> deliver) {
        enqueue(requesterKey, () -> {
            ComputedPath result = pathfinder.findPath(query, view);
            return () -> deliver.accept(result);
        });
    }

    /**
     * Raw form: {@code compute} runs on a path worker; {@code deliver}
     * receives its result on the requester's owner at a tick boundary.
     * Same single-flight coalescing per key as {@link #submit}.
     */
    public <T> void submitTask(long requesterKey, Supplier<T> compute, Consumer<T> deliver) {
        enqueue(requesterKey, () -> {
            T result = compute.get();
            return () -> deliver.accept(result);
        });
    }

    @Override
    public boolean cancel(long requesterKey) {
        Job old = latestByKey.remove(requesterKey);
        if (old != null) {
            old.superseded = true;
            return true;
        }
        return false;
    }

    private void enqueue(long key, Supplier<Runnable> computeThenDeliver) {
        if (closed) {
            return;
        }
        Job job = new Job(key, computeThenDeliver);
        Job old = latestByKey.put(key, job);
        if (old != null) {
            old.superseded = true; // still queued: worker will skip it
            coalesced.increment();
        }
        submitted.increment();
        queue.add(job);
    }

    private void workLoop() {
        while (!closed) {
            Job job;
            try {
                job = queue.take();
            } catch (InterruptedException e) {
                return;
            }
            if (job.superseded) {
                continue;
            }
            latestByKey.remove(job.key, job);
            long t0 = System.nanoTime();
            try {
                Runnable deliver = job.computeThenDeliver.get();
                lastComputeNanos = System.nanoTime() - t0;
                computed.increment();
                ownerPost.post(job.key, deliver);
            } catch (Throwable t) {
                // Engine failure: counted, not delivered (documented contract);
                // the requester's next submission recovers it.
                failed.increment();
            }
        }
    }

    // --- telemetry for /weft status (R5) ---

    public long submittedCount() {
        return submitted.sum();
    }

    public long coalescedCount() {
        return coalesced.sum();
    }

    public long computedCount() {
        return computed.sum();
    }

    public long failedCount() {
        return failed.sum();
    }

    public int queueDepth() {
        return queue.size();
    }

    public long lastComputeNanos() {
        return lastComputeNanos;
    }

    @Override
    public void close() {
        closed = true;
        for (Thread w : workers) {
            w.interrupt();
        }
        for (Thread w : workers) {
            try {
                w.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        queue.clear();
        latestByKey.clear();
    }
}
