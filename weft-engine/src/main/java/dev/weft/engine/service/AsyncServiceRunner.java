package dev.weft.engine.service;

import dev.weft.api.service.AsyncService;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives one {@link AsyncService}: coalescing single-flight refresh with
 * atomic publish (RFC-0001 §11 P1).
 *
 * <p>Threading: {@link #refresh} may be called from any thread (typically
 * the owner thread at tick end) and never blocks beyond a CAS. At most one
 * compute runs at a time; inputs arriving mid-compute overwrite the pending
 * slot so only the latest is computed next — the tick can never build a
 * backlog behind a slow service. {@link #latest} is a volatile read.
 *
 * <p>Failures: an exception from {@link AsyncService#compute} keeps the
 * previous published result, increments {@link #failureCount}, and stores
 * the throwable in {@link #lastFailure} for the caller to log (the engine
 * stays logging-framework-free).
 */
public final class AsyncServiceRunner<I, R> implements AutoCloseable {

    /** A published result and the tick whose snapshot produced it. */
    public record Published<R>(long tick, R result, long computeNanos) {}

    private record Pending<I>(long tick, I input) {}

    private final AsyncService<I, R> service;
    private final Executor executor;
    private final ExecutorService ownedExecutor; // non-null only if we created it

    private final AtomicReference<Pending<I>> pending = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicReference<Published<R>> published = new AtomicReference<>();
    private final AtomicLong failureCount = new AtomicLong();
    private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();
    private volatile boolean closed;

    /** Run computes on a caller-supplied executor (shared service pool). */
    public AsyncServiceRunner(AsyncService<I, R> service, Executor executor) {
        this.service = service;
        this.executor = executor;
        this.ownedExecutor = null;
    }

    /** Run computes on a dedicated single daemon worker named after the service. */
    public static <I, R> AsyncServiceRunner<I, R> withDedicatedWorker(AsyncService<I, R> service) {
        ExecutorService owned = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "weft-service-" + service.serviceId());
            t.setDaemon(true);
            return t;
        });
        return new AsyncServiceRunner<>(service, owned);
    }

    private AsyncServiceRunner(AsyncService<I, R> service, ExecutorService owned) {
        this.service = service;
        this.executor = owned;
        this.ownedExecutor = owned;
    }

    /**
     * Offer a new input snapshot. Never blocks. If a compute is in flight,
     * the input replaces any not-yet-started one (latest wins).
     */
    public void refresh(long tick, I input) {
        if (closed) {
            return;
        }
        pending.set(new Pending<>(tick, input));
        tryLaunch();
    }

    /** Most recent successfully published result, if any compute finished yet. */
    public Optional<Published<R>> latest() {
        return Optional.ofNullable(published.get());
    }

    public long failureCount() {
        return failureCount.get();
    }

    public Optional<Throwable> lastFailure() {
        return Optional.ofNullable(lastFailure.get());
    }

    public String serviceId() {
        return service.serviceId();
    }

    private void tryLaunch() {
        if (running.compareAndSet(false, true)) {
            try {
                executor.execute(this::drainLoop);
            } catch (RuntimeException e) {
                running.set(false); // executor rejected (shutdown); stay consistent
                throw e;
            }
        }
    }

    private void drainLoop() {
        try {
            Pending<I> work;
            while ((work = pending.getAndSet(null)) != null) {
                computeOne(work);
            }
        } finally {
            running.set(false);
        }
        // Race close: an input may have landed after our last drain but
        // before we released the flag — relaunch rather than strand it.
        if (pending.get() != null && !closed) {
            tryLaunch();
        }
    }

    private void computeOne(Pending<I> work) {
        long t0 = System.nanoTime();
        try {
            R result = service.compute(work.input());
            published.set(new Published<>(work.tick(), result, System.nanoTime() - t0));
        } catch (Throwable t) {
            failureCount.incrementAndGet();
            lastFailure.set(t);
        }
    }

    @Override
    public void close() {
        closed = true;
        pending.set(null);
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
            try {
                if (!ownedExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ownedExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ownedExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
