package dev.weft.engine.service;

import dev.weft.api.service.AsyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class AsyncServiceRunnerTest {

    /** Service that doubles its input and counts invocations. */
    private static final class Doubler implements AsyncService<Integer, Integer> {
        final AtomicInteger computes = new AtomicInteger();
        volatile CountDownLatch gate; // when set, compute blocks until opened

        @Override public String serviceId() { return "test:doubler"; }

        @Override public Integer compute(Integer input) {
            computes.incrementAndGet();
            CountDownLatch g = gate;
            if (g != null) {
                try {
                    if (!g.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("gate never opened");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return input * 2;
        }
    }

    private static void awaitLatest(AsyncServiceRunner<?, ?> runner, long tick) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (runner.latest().map(p -> p.tick() < tick).orElse(true)) {
            if (System.nanoTime() > deadline) {
                fail("no result published for tick " + tick);
            }
            Thread.sleep(1);
        }
    }

    @Test
    void publishesResultTaggedWithSnapshotTick() throws Exception {
        Doubler service = new Doubler();
        try (AsyncServiceRunner<Integer, Integer> runner =
                     AsyncServiceRunner.withDedicatedWorker(service)) {
            assertTrue(runner.latest().isEmpty(), "nothing published before first refresh");
            runner.refresh(7, 21);
            awaitLatest(runner, 7);
            var p = runner.latest().orElseThrow();
            assertEquals(7, p.tick());
            assertEquals(42, p.result());
            assertTrue(p.computeNanos() >= 0);
        }
    }

    @Test
    void coalescesRefreshesWhileComputeIsInFlight() throws Exception {
        Doubler service = new Doubler();
        service.gate = new CountDownLatch(1);
        try (AsyncServiceRunner<Integer, Integer> runner =
                     AsyncServiceRunner.withDedicatedWorker(service)) {
            runner.refresh(1, 10); // starts, blocks on the gate
            // Wait until the first compute is actually running.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (service.computes.get() < 1) {
                assertTrue(System.nanoTime() < deadline, "first compute never started");
                Thread.sleep(1);
            }
            // Pile up refreshes; all but the last must be dropped.
            for (int t = 2; t <= 6; t++) {
                runner.refresh(t, t * 10);
            }
            service.gate.countDown();
            service.gate = null;
            awaitLatest(runner, 6);
            var p = runner.latest().orElseThrow();
            assertEquals(6, p.tick(), "latest input wins");
            assertEquals(120, p.result());
            assertEquals(2, service.computes.get(),
                    "exactly the in-flight compute plus one for the coalesced latest");
        }
    }

    @Test
    void failureKeepsPreviousResultAndCounts() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AsyncService<Integer, Integer> flaky = new AsyncService<>() {
            @Override public String serviceId() { return "test:flaky"; }
            @Override public Integer compute(Integer input) {
                if (calls.incrementAndGet() == 2) {
                    throw new IllegalStateException("boom");
                }
                return input;
            }
        };
        try (AsyncServiceRunner<Integer, Integer> runner =
                     AsyncServiceRunner.withDedicatedWorker(flaky)) {
            runner.refresh(1, 111);
            awaitLatest(runner, 1);

            runner.refresh(2, 222); // this compute throws
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runner.failureCount() < 1) {
                assertTrue(System.nanoTime() < deadline, "failure never recorded");
                Thread.sleep(1);
            }
            var p = runner.latest().orElseThrow();
            assertEquals(1, p.tick(), "failed compute must not clobber the last good result");
            assertEquals(111, p.result());
            assertEquals("boom", runner.lastFailure().orElseThrow().getMessage());

            runner.refresh(3, 333); // recovers
            awaitLatest(runner, 3);
            assertEquals(333, runner.latest().orElseThrow().result());
        }
    }

    @Test
    void refreshAfterCloseIsIgnored() {
        Doubler service = new Doubler();
        AsyncServiceRunner<Integer, Integer> runner =
                AsyncServiceRunner.withDedicatedWorker(service);
        runner.close();
        runner.refresh(1, 10); // must not throw or launch
        assertTrue(runner.latest().isEmpty());
        assertEquals(0, service.computes.get());
    }

    @Test
    void manyThreadsRefreshingConvergeOnLastInput() throws Exception {
        Doubler service = new Doubler();
        try (AsyncServiceRunner<Integer, Integer> runner =
                     AsyncServiceRunner.withDedicatedWorker(service)) {
            int threads = 8, perThread = 500;
            Thread[] ts = new Thread[threads];
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < threads; i++) {
                final int base = i * perThread;
                ts[i] = new Thread(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    for (int j = 1; j <= perThread; j++) {
                        runner.refresh(base + j, base + j);
                    }
                });
                ts[i].start();
            }
            start.countDown();
            for (Thread t : ts) {
                t.join();
            }
            // Deterministic tail: one final refresh strictly after all others.
            runner.refresh(1_000_000, 999);
            awaitLatest(runner, 1_000_000);
            assertEquals(1998, runner.latest().orElseThrow().result());
            assertTrue(service.computes.get() <= threads * perThread + 1,
                    "coalescing must not amplify computes");
        }
    }
}
