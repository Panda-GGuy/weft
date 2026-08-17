package dev.weft.services.path;

import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.PathQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PathServiceTest {

    /** Owner-post stand-in: collects tasks like a mailbox; the test thread
     *  drains it like the INGEST phase would. */
    private final ConcurrentLinkedQueue<Runnable> ownerMailbox = new ConcurrentLinkedQueue<>();
    private PathService service;

    private PathService service(int threads) {
        service = new PathService("test-path", threads, (key, task) -> ownerMailbox.add(task));
        return service;
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    private void drainOwner() {
        Runnable task;
        while ((task = ownerMailbox.poll()) != null) {
            task.run();
        }
    }

    @Test
    void deliversResultThroughOwnerPostExactlyOnce() throws Exception {
        PathService s = service(2);
        AtomicInteger delivered = new AtomicInteger();
        List<ComputedPath> results = new ArrayList<>();

        s.submit(1, new PathQuery(0, 64, 0, 5, 64, 0, 0.5, 10_000, 128),
                (x, y, z) -> y == 64 ? 0 : -1,
                result -> {
                    delivered.incrementAndGet();
                    results.add(result);
                });
        // Wait for the worker to post, then drain like a tick boundary would.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ownerMailbox.isEmpty() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(ownerMailbox.isEmpty(), "worker must post the delivery");
        assertEquals(0, delivered.get(), "nothing delivered before the owner drains");
        drainOwner();
        assertEquals(1, delivered.get(), "delivered exactly once");
        assertEquals(ComputedPath.Status.FOUND, results.get(0).status());
        assertEquals(1, s.computedCount());
    }

    @Test
    void rapidResubmitsCoalesceToLatest() throws Exception {
        // One worker blocked on a slow first job forces later submissions for
        // the same key to queue up and coalesce.
        CountDownLatch releaseSlow = new CountDownLatch(1);
        PathService s = service(1);
        s.submitTask(7, () -> {
            try {
                releaseSlow.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "slow";
        }, r -> {});

        AtomicInteger computes = new AtomicInteger();
        ConcurrentLinkedQueue<String> deliveredQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 5; i++) {
            final String value = "job-" + i;
            s.submitTask(9, () -> {
                computes.incrementAndGet();
                return value;
            }, deliveredQueue::add);
        }
        releaseSlow.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (s.computedCount() < 2 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        drainOwner();
        assertEquals(1, computes.get(), "only the latest job for key 9 computes");
        assertEquals(List.of("job-4"), List.copyOf(deliveredQueue), "latest wins");
        assertEquals(4, s.coalescedCount());
    }

    @Test
    void cancelDropsQueuedRequest() throws Exception {
        CountDownLatch releaseSlow = new CountDownLatch(1);
        PathService s = service(1);
        s.submitTask(1, () -> {
            try {
                releaseSlow.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "slow";
        }, r -> {});
        AtomicInteger computed = new AtomicInteger();
        s.submitTask(2, () -> {
            computed.incrementAndGet();
            return "queued";
        }, r -> {});

        assertTrue(s.cancel(2), "queued request cancellable");
        assertFalse(s.cancel(99), "unknown key");
        releaseSlow.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (s.computedCount() < 1 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        Thread.sleep(50); // give the worker a chance to (wrongly) run job 2
        assertEquals(0, computed.get(), "cancelled job never computes");
    }

    @Test
    void computeFailureIsCountedNotDelivered() throws Exception {
        PathService s = service(1);
        AtomicInteger delivered = new AtomicInteger();
        s.submitTask(3, () -> {
            throw new IllegalStateException("boom");
        }, r -> delivered.incrementAndGet());

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (s.failedCount() < 1 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(1, s.failedCount());
        drainOwner();
        assertEquals(0, delivered.get(), "failed compute delivers nothing");
    }

    @Test
    void distinctKeysDoNotCoalesce() throws Exception {
        PathService s = service(2);
        int jobs = 20;
        for (int i = 0; i < jobs; i++) {
            final int value = i;
            s.submitTask(100 + i, () -> value, r -> {});
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (s.computedCount() < jobs && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(jobs, s.computedCount(), "every distinct key computes");
        assertEquals(0, s.coalescedCount());
    }
}
