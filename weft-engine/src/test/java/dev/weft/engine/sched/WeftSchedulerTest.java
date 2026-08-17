package dev.weft.engine.sched;

import dev.weft.api.graph.CommitLog;
import dev.weft.api.graph.GraphDefinition;
import dev.weft.api.graph.GraphTicker;
import dev.weft.api.graph.WorldSnapshot;
import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.engine.guard.WeftGuards;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class WeftSchedulerTest {

    private static final WorldSnapshot EMPTY_SNAPSHOT = new WorldSnapshot() {
        @Override public long tick() { return 0; }
        @Override public OptionalLong blockStateHandle(int x, int y, int z) { return OptionalLong.empty(); }
        @Override public boolean isLoaded(int x, int y, int z) { return false; }
    };

    private static GraphDefinition graph(String id, Set<Long> chunks, GraphTicker ticker) {
        return new GraphDefinition() {
            @Override public String graphId() { return id; }
            @Override public Set<Long> interestChunks() { return chunks; }
            @Override public GraphTicker ticker() { return ticker; }
        };
    }

    @Test
    void phasesRunInOrderAndAllHooksFire() throws Exception {
        RegionManager rm = new RegionManager(1, 7L);
        rm.addChunk(0, 0);
        rm.addChunk(50, 50);

        ConcurrentLinkedQueue<String> order = new ConcurrentLinkedQueue<>();
        rm.all().forEach(r -> r.addTickable((region, t) -> order.add("REGION")));

        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        WeftScheduler.Hooks hooks = new WeftScheduler.Hooks() {
            @Override public void runLegacy(long tick) { order.add("LEGACY"); }
            @Override public void runGlobal(long tick) { order.add("GLOBAL"); }
            @Override public void runEgress(long tick) { order.add("EGRESS"); }
        };

        try (WeftScheduler sched = new WeftScheduler(4, rm, gs, hooks)) {
            sched.tick();
        }

        List<String> seq = List.copyOf(order);
        // Both regions tick before any serialized phase.
        int lastRegion = seq.lastIndexOf("REGION");
        assertEquals(2, seq.stream().filter("REGION"::equals).count());
        assertTrue(lastRegion < seq.indexOf("LEGACY"), "regions settle before legacy lane");
        assertTrue(seq.indexOf("LEGACY") < seq.indexOf("GLOBAL"));
        assertTrue(seq.indexOf("GLOBAL") < seq.indexOf("EGRESS"));
    }

    @Test
    void independentRegionsTickOnParallelWorkers() throws Exception {
        RegionManager rm = new RegionManager(1, 7L);
        int regionCount = 8;
        for (int i = 0; i < regionCount; i++) {
            rm.addChunk(i * 100, 0); // far apart -> independent regions
        }
        assertEquals(regionCount, rm.all().size());

        Set<String> threadsUsed = new CopyOnWriteArraySet<>();
        rm.all().forEach(r -> r.addTickable((region, t) -> {
            threadsUsed.add(Thread.currentThread().getName());
            busyWaitNanos(2_000_000); // 2ms of "work" so workers overlap
        }));

        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        try (WeftScheduler sched = new WeftScheduler(4, rm, gs, new WeftScheduler.Hooks() {})) {
            sched.tick();
        }
        assertTrue(threadsUsed.size() > 1,
                "independent regions must spread across workers, used: " + threadsUsed);
    }

    @Test
    void graphCommitsApplyDeterministicallyAfterCompute() throws Exception {
        RegionManager rm = new RegionManager(1, 7L);
        rm.addChunk(0, 0);

        StringBuilder applied = new StringBuilder(); // commit phase: per-chunk serialized
        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        // Register out of id order; commits must still apply in id order.
        gs.register(graph("z-net", Set.of(0L), (snap, log) -> log.setBlock(1, 64, 1, 99)));
        gs.register(graph("a-net", Set.of(0L), (snap, log) -> log.setBlock(2, 64, 2, 98)));

        List<GraphScheduler.CommitOp> ops;
        try (var pool = java.util.concurrent.Executors.newWorkStealingPool(4)) {
            ops = gs.computeAll(pool, 1);
        }
        assertEquals(2, ops.size());
        assertEquals("a-net", ops.get(0).graphId(), "deterministic graph-priority order");
        assertEquals("z-net", ops.get(1).graphId());
    }

    @Test
    void guardTripsWhenGraphTouchesRegionState() throws Exception {
        WeftGuards.setMode(WeftGuards.Mode.DEV);
        RegionManager rm = new RegionManager(1, 7L);
        Region region = rm.addChunk(0, 0);

        AtomicLong caught = new AtomicLong();
        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        gs.register(graph("rogue", Set.of(0L), (snap, log) -> {
            try {
                WeftGuards.checkRegionMutation(region.id()); // illegal: graphs commit, not mutate
            } catch (WeftGuards.WrongOwnerException e) {
                caught.incrementAndGet();
            }
        }));

        try (var pool = java.util.concurrent.Executors.newWorkStealingPool(2)) {
            gs.computeAll(pool, 1);
        }
        assertEquals(1, caught.get(), "graph mutating region state must trip the guard");
    }

    @Test
    void runOnOwnerDeliversToOwningRegionNextTick() throws Exception {
        RegionManager rm = new RegionManager(1, 7L);
        rm.addChunk(0, 0);

        AtomicLong ran = new AtomicLong();
        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        try (WeftScheduler sched = new WeftScheduler(2, rm, gs, new WeftScheduler.Hooks() {})) {
            sched.runOnOwner(5, 5, ran::incrementAndGet); // block (5,5) -> chunk (0,0)
            assertEquals(0, ran.get(), "task must not run before its owner's mail phase");
            sched.tick();
            assertEquals(1, ran.get(), "task runs during owner's mail drain");
        }
    }

    @Test
    void runOwnedSerialEstablishesRegionContextOnCallingThread() throws Exception {
        WeftGuards.setMode(WeftGuards.Mode.DEV);
        RegionManager rm = new RegionManager(1, 7L);
        long ownerId = rm.reserveRegionId();

        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY_SNAPSHOT);
        try (WeftScheduler sched = new WeftScheduler(2, rm, gs, new WeftScheduler.Hooks() {})) {
            assertEquals(0, sched.ownedSerialSections());
            Thread caller = Thread.currentThread();
            sched.runOwnedSerial(ownerId, () -> {
                assertSame(caller, Thread.currentThread(),
                        "increment 1 is serial: the section runs on the calling thread");
                assertEquals(ThreadContext.Kind.REGION, ThreadContext.current().kind());
                assertEquals(ownerId, ThreadContext.current().ownerId());
                WeftGuards.checkRegionMutation(ownerId); // we are the owner: must not trip
            });
            assertEquals(ThreadContext.Kind.NONE, ThreadContext.current().kind(),
                    "context restored after the owned section");
            assertEquals(1, sched.ownedSerialSections());

            // A throwing section must still restore the context and be counted.
            assertThrows(IllegalStateException.class,
                    () -> sched.runOwnedSerial(ownerId, () -> {
                        throw new IllegalStateException("boom");
                    }));
            assertEquals(ThreadContext.Kind.NONE, ThreadContext.current().kind());
            assertEquals(2, sched.ownedSerialSections());
        }
    }

    private static void busyWaitNanos(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            Thread.onSpinWait();
        }
    }
}
