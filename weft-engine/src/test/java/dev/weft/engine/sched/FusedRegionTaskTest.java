package dev.weft.engine.sched;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.engine.region.RegionManager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RFC-0007 §4: the fused single-join task. Each region's
 * [mail drain → entity → BE] stages run as one uninterrupted unit under one
 * REGION context; the caller joins once for the whole set; the serial path
 * is the same shape without threads (canonical caller order preserved).
 */
class FusedRegionTaskTest {

    private static final dev.weft.api.graph.WorldSnapshot EMPTY_SNAPSHOT =
            new dev.weft.api.graph.WorldSnapshot() {
                @Override public long tick() { return 0; }
                @Override public OptionalLong blockStateHandle(int x, int y, int z) { return OptionalLong.empty(); }
                @Override public boolean isLoaded(int x, int y, int z) { return false; }
            };

    private static WeftScheduler scheduler(int workers) {
        RegionManager rm = new RegionManager(1, 7L);
        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        return new WeftScheduler(workers, rm, gs, new WeftScheduler.Hooks() {});
    }

    @Test
    void stagesRunInOrderUnderOneContextSerial() throws Exception {
        try (WeftScheduler sched = scheduler(2)) {
            ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
            Thread caller = Thread.currentThread();
            Runnable probe = () -> {
                assertSame(caller, Thread.currentThread(), "serial path stays on the caller");
                assertEquals(ThreadContext.Kind.REGION, ThreadContext.current().kind());
                assertEquals(11L, ThreadContext.current().ownerId());
            };
            sched.runOwnedFused(List.of(new WeftScheduler.FusedRegionTask(11L, List.of(
                    () -> { probe.run(); order.add("mail"); },
                    () -> { probe.run(); order.add("entity"); },
                    () -> { probe.run(); order.add("be"); }))), false);
            assertEquals(List.of("mail", "entity", "be"), List.copyOf(order),
                    "stages run in list order: mail drain, entity bucket, BE bucket");
            assertEquals(ThreadContext.Kind.NONE, ThreadContext.current().kind(),
                    "context restored after the fused task");
            assertEquals(1, sched.fusedTasks());
        }
    }

    @Test
    void serialPathPreservesCanonicalTaskOrder() throws Exception {
        try (WeftScheduler sched = scheduler(2)) {
            ConcurrentLinkedQueue<Long> order = new ConcurrentLinkedQueue<>();
            List<WeftScheduler.FusedRegionTask> tasks = List.of(
                    new WeftScheduler.FusedRegionTask(1L, List.of(() -> order.add(1L))),
                    new WeftScheduler.FusedRegionTask(2L, List.of(() -> order.add(2L))),
                    new WeftScheduler.FusedRegionTask(3L, List.of(() -> order.add(3L))));
            sched.runOwnedFused(tasks, false);
            assertEquals(List.of(1L, 2L, 3L), List.copyOf(order),
                    "serial fusion runs tasks in the caller's canonical order");
            assertEquals(3, sched.fusedTasks());
        }
    }

    @Test
    void regionsRunFreeOfEachOtherWithinTheJoin() throws Exception {
        // The increment-7 claim made concrete (RFC-0007 §4 gate): region A's
        // last stage can complete while region B's first stage has not - no
        // cross-region barrier between stages. B's entity stage blocks until
        // A's BE stage has finished; only a stage-level barrier would deadlock.
        try (WeftScheduler sched = scheduler(4)) {
            CountDownLatch aFinishedBe = new CountDownLatch(1);
            AtomicBoolean bSawAComplete = new AtomicBoolean();

            WeftScheduler.FusedRegionTask taskA = new WeftScheduler.FusedRegionTask(1L, List.of(
                    () -> {},                       // mail
                    () -> {},                       // entity
                    aFinishedBe::countDown));       // BE - completes the whole region
            WeftScheduler.FusedRegionTask taskB = new WeftScheduler.FusedRegionTask(2L, List.of(
                    () -> {},                       // mail
                    () -> {                         // entity - waits for A to be fully done
                        try {
                            bSawAComplete.set(aFinishedBe.await(10, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    },
                    () -> {}));                     // BE

            sched.runOwnedFused(List.of(taskA, taskB), true);
            assertTrue(bSawAComplete.get(),
                    "region A's BE stage must be able to complete while region B's entity stage runs");
        }
    }

    @Test
    void parallelFusionStampsEachTasksOwnContext() throws Exception {
        try (WeftScheduler sched = scheduler(4)) {
            ConcurrentHashMap<Long, Long> seen = new ConcurrentHashMap<>();
            List<WeftScheduler.FusedRegionTask> tasks = List.of(
                    new WeftScheduler.FusedRegionTask(21L, List.of(() ->
                            seen.put(21L, ThreadContext.current().ownerId()))),
                    new WeftScheduler.FusedRegionTask(22L, List.of(() ->
                            seen.put(22L, ThreadContext.current().ownerId()))));
            sched.runOwnedFused(tasks, true);
            assertEquals(21L, seen.get(21L));
            assertEquals(22L, seen.get(22L));
            assertEquals(2, sched.fusedTasks());
        }
    }

    @Test
    void singleTaskUnderParallelTakesSerialPath() throws Exception {
        try (WeftScheduler sched = scheduler(2)) {
            Thread caller = Thread.currentThread();
            AtomicBoolean onCaller = new AtomicBoolean();
            sched.runOwnedFused(List.of(new WeftScheduler.FusedRegionTask(5L, List.of(
                    () -> onCaller.set(Thread.currentThread() == caller)))), true);
            assertTrue(onCaller.get(),
                    "one region: fan-out is pure overhead, the task runs on the caller");
        }
    }

    @Test
    void failingStageFailsLoudAndRestoresContext() throws Exception {
        try (WeftScheduler sched = scheduler(2)) {
            assertThrows(IllegalStateException.class, () ->
                    sched.runOwnedFused(List.of(new WeftScheduler.FusedRegionTask(9L, List.of(
                            () -> { throw new IllegalStateException("boom"); }))), false));
            assertEquals(ThreadContext.Kind.NONE, ThreadContext.current().kind(),
                    "a crashing stage must not leak its REGION context");
        }
    }
}