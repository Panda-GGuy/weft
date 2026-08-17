import dev.weft.api.graph.*;
import dev.weft.api.path.ComputedPath;
import dev.weft.api.path.NavView;
import dev.weft.api.path.PathQuery;
import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.WeftGuards;
import dev.weft.engine.mail.Mailbox;
import dev.weft.engine.mail.Message;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.services.activation.ActivationScheduler;
import dev.weft.services.path.GridPathfinder;
import dev.weft.services.path.PathService;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sandbox-local verification mirroring the JUnit suite (which needs Maven
 * Central and therefore runs on CI). Exits nonzero on any failure.
 */
public final class Smoke {
    static int passed = 0;

    static void check(boolean cond, String name) {
        if (!cond) {
            System.err.println("FAIL: " + name);
            System.exit(1);
        }
        passed++;
        System.out.println("ok: " + name);
    }

    static final WorldSnapshot EMPTY = new WorldSnapshot() {
        public long tick() { return 0; }
        public OptionalLong blockStateHandle(int x, int y, int z) { return OptionalLong.empty(); }
        public boolean isLoaded(int x, int y, int z) { return false; }
    };

    static GraphDefinition graph(String id, GraphTicker t) {
        return new GraphDefinition() {
            public String graphId() { return id; }
            public Set<Long> interestChunks() { return Set.of(0L); }
            public GraphTicker ticker() { return t; }
        };
    }

    public static void main(String[] args) throws Exception {
        // --- RegionManager ---
        RegionManager rm1 = new RegionManager(2, 42L);
        check(rm1.addChunk(0, 0) == rm1.addChunk(1, 1), "nearby chunks share a region");
        rm1.addChunk(100, 100);
        check(rm1.all().size() == 2, "distant chunks get separate regions");

        RegionManager rm2 = new RegionManager(1, 42L);
        for (int x = 0; x <= 4; x++) rm2.addChunk(x, 0);
        check(rm2.all().size() == 1, "chain of chunks merges to one region");
        rm2.removeChunk(2, 0);
        rm2.recomputeSplits();
        check(rm2.all().size() == 2, "cutting the chain splits into two regions");
        check(rm2.regionAt(0, 0) != rm2.regionAt(4, 0), "split halves are distinct");
        check(rm2.regionAt(0, 0) == rm2.regionAt(1, 0), "left half stays connected");

        // --- Mailbox FIFO under concurrency ---
        Mailbox<int[]> box = new Mailbox<>();
        int senders = 8, per = 5000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        for (int s = 0; s < senders; s++) {
            final int id = s;
            Thread t = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < per; i++) box.post(new int[]{id, i});
            });
            t.start(); ts.add(t);
        }
        start.countDown();
        for (Thread t : ts) t.join();
        int[] expect = new int[senders]; int total = 0; boolean fifo = true;
        for (int[] m : box.drain()) { if (expect[m[0]]++ != m[1]) fifo = false; total++; }
        check(fifo, "mailbox is FIFO per sender under 8-way concurrency");
        check(total == senders * per, "mailbox loses no messages (" + total + ")");

        // --- Scheduler pipeline: phase order + parallelism ---
        RegionManager rm3 = new RegionManager(1, 7L);
        for (int i = 0; i < 8; i++) rm3.addChunk(i * 100, 0);
        Set<String> threads = ConcurrentHashMap.newKeySet();
        Queue<String> order = new ConcurrentLinkedQueue<>();
        rm3.all().forEach(r -> r.addTickable((region, tick) -> {
            threads.add(Thread.currentThread().getName());
            order.add("REGION");
            long end = System.nanoTime() + 2_000_000;
            while (System.nanoTime() < end) Thread.onSpinWait();
        }));
        GraphScheduler gs = new GraphScheduler((g, t) -> EMPTY);
        gs.register(graph("z-net", (snap, log) -> log.setBlock(1, 64, 1, 99)));
        gs.register(graph("a-net", (snap, log) -> log.setBlock(2, 64, 2, 98)));

        AtomicLong ranOnOwner = new AtomicLong();
        WeftScheduler.Hooks hooks = new WeftScheduler.Hooks() {
            public void runLegacy(long t) { order.add("LEGACY"); }
            public void runGlobal(long t) { order.add("GLOBAL"); }
            public void runEgress(long t) { order.add("EGRESS"); }
        };
        try (WeftScheduler sched = new WeftScheduler(4, rm3, gs, hooks)) {
            sched.runOnOwner(5, 5, ranOnOwner::incrementAndGet);
            check(ranOnOwner.get() == 0, "runOnOwner task deferred until owner's mail phase");
            sched.tick();
            check(ranOnOwner.get() == 1, "runOnOwner task delivered during mail phase");
        }
        List<String> seq = new ArrayList<>(order);
        check(seq.stream().filter("REGION"::equals).count() == 8, "all 8 regions ticked");
        check(seq.lastIndexOf("REGION") < seq.indexOf("LEGACY"), "regions settle before legacy lane");
        check(seq.indexOf("LEGACY") < seq.indexOf("GLOBAL") && seq.indexOf("GLOBAL") < seq.indexOf("EGRESS"),
                "LEGACY -> GLOBAL -> EGRESS order");
        check(threads.size() > 1, "regions spread across " + threads.size() + " workers");

        // --- Graph determinism + guards ---
        try (var pool = Executors.newWorkStealingPool(4)) {
            var ops = gs.computeAll(pool, 2);
            check(ops.get(0).graphId().equals("a-net") && ops.get(1).graphId().equals("z-net"),
                    "commit ops sorted by graph id regardless of registration order");
        }
        WeftGuards.setMode(WeftGuards.Mode.DEV);
        RegionManager rm4 = new RegionManager(1, 7L);
        Region region = rm4.addChunk(0, 0);
        AtomicLong caught = new AtomicLong();
        GraphScheduler gs2 = new GraphScheduler((g, t) -> EMPTY);
        gs2.register(graph("rogue", (snap, log) -> {
            try { WeftGuards.checkRegionMutation(region.id()); }
            catch (WeftGuards.WrongOwnerException e) { caught.incrementAndGet(); }
        }));
        try (var pool = Executors.newWorkStealingPool(2)) {
            gs2.computeAll(pool, 1);
        }
        check(caught.get() == 1, "guard trips when a graph touches region state directly");

        // --- WS-1 activation policy invariants (RFC-0002) ---
        ActivationScheduler act = new ActivationScheduler(
                new ActivationScheduler.Tiers(32, 64, 4, 20),
                Set.of("mod:boss"), Map.of("mod:villager", 1, "mod:snail", 40));
        check(act.intervalFor("mod:cow", 20 * 20) == 1, "full-rate ring is inviolate");
        check(act.intervalFor("mod:boss", 500 * 500) == 1, "exempt type never throttled");
        check(act.intervalFor("mod:villager", 500 * 500) == 1, "override 1 = per-type opt-out");
        check(act.intervalFor("mod:snail", 50 * 50) == 40, "override replaces tier interval");
        check(act.intervalFor("mod:cow", 50 * 50) == 4
                        && act.intervalFor("mod:cow", 500 * 500) == 20, "tier intervals apply by distance");
        int[] runsPerTick = new int[20];
        for (long tick = 0; tick < 20; tick++) {
            for (int id = 0; id < 200; id++) {
                if (ActivationScheduler.shouldRunThisTick(tick, id, 20)) runsPerTick[(int) tick]++;
            }
        }
        boolean staggered = true;
        for (int perTick : runsPerTick) staggered &= perTick == 10;
        check(staggered, "throttled AI runs stagger evenly across the interval window");
        check(ActivationScheduler.shouldDeferRepath(20, 20, 4)
                        && ActivationScheduler.shouldDeferRepath(80, 20, 4)
                        && !ActivationScheduler.shouldDeferRepath(81, 20, 4),
                "repath deferral stretches the vanilla window by the AI interval");
        check(!ActivationScheduler.shouldDeferRepath(0, 20, 1)
                        && !ActivationScheduler.shouldDeferRepath(19, 20, 1),
                "full-rate mobs never have repaths deferred");

        // --- WS-2 async pathfinding: ownership discipline + exactly-once ---
        // Deliveries route through the scheduler's mailbox (Message.Task) and
        // must run only when the owner drains it (tick INGEST on the
        // coordinator thread) - never on a path worker thread.
        RegionManager rm5 = new RegionManager(1, 7L);
        GraphScheduler gs5 = new GraphScheduler((g, t) -> EMPTY);
        try (WeftScheduler sched = new WeftScheduler(2, rm5, gs5, new WeftScheduler.Hooks() {});
             PathService paths = new PathService("smoke-path", 2,
                     (key, task) -> sched.submit(new Message.Task(task)))) {
            Thread coordinator = Thread.currentThread();
            NavView flat = (x, y, z) -> y == 64 ? 0 : -1;
            AtomicInteger delivered = new AtomicInteger();
            Set<Thread> deliveryThreads = ConcurrentHashMap.newKeySet();
            List<ComputedPath> results = new CopyOnWriteArrayList<>();
            paths.submit(1, new PathQuery(0, 64, 0, 12, 64, 5, 0.5, 20_000, 256), flat,
                    r -> { delivered.incrementAndGet(); deliveryThreads.add(Thread.currentThread()); results.add(r); });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (paths.computedCount() < 1 && System.nanoTime() < deadline) Thread.onSpinWait();
            check(paths.computedCount() == 1, "path computed off-thread");
            check(delivered.get() == 0, "result NOT applied before the owner's tick boundary");
            sched.tick();
            check(delivered.get() == 1, "result applied exactly once at the tick boundary");
            sched.tick();
            check(delivered.get() == 1, "no double-apply on later ticks");
            check(deliveryThreads.equals(Set.of(coordinator)),
                    "delivery ran on the owning (coordinator) thread, not a path worker");
            check(results.get(0).status() == ComputedPath.Status.FOUND
                    && results.get(0).nodeCount() >= 13, "computed path is real and complete");

            // Coalescing: N rapid submits for one requester deliver once, latest wins.
            // Deterministic setup: park both workers on latch-blocked jobs so
            // the 5 rapid submits must meet in the queue. Without this a fast
            // worker legitimately races the submit loop and delivers more
            // than once - latest-wins bounds staleness, not delivery count.
            CountDownLatch release = new CountDownLatch(1);
            for (long sentinel = 100; sentinel <= 101; sentinel++) {
                paths.submitTask(sentinel, () -> {
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }, r -> {});
            }
            AtomicInteger coalescedDeliveries = new AtomicInteger();
            long computedBefore = paths.computedCount();
            long coalescedBefore = paths.coalescedCount();
            for (int i = 0; i < 5; i++) {
                paths.submit(2, new PathQuery(0, 64, 0, 3 + i, 64, 0, 0.5, 20_000, 256), flat,
                        r -> coalescedDeliveries.incrementAndGet());
            }
            release.countDown();
            // 2 sentinels + 1 surviving key-2 job; computedCount is bumped
            // after the owner-post, so reaching it means the posts are in.
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (paths.computedCount() < computedBefore + 3 && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            sched.tick();
            check(coalescedDeliveries.get() == 1,
                    "rapid resubmits coalesce (delivered " + coalescedDeliveries.get() + " of 5)");
            check(paths.coalescedCount() - coalescedBefore == 4,
                    "superseded submits are counted as coalesced");
        }

        // Pathfinder sanity: the engine A* respects walls (no through-wall paths
        // for the adapter to apply).
        NavView walled = (x, y, z) -> y != 64 ? -1 : (x == 5 && z != 6) ? -1 : 0;
        ComputedPath detour = new GridPathfinder().findPath(
                new PathQuery(0, 64, 0, 10, 64, 0, 0.5, 20_000, 256), walled);
        boolean crossesAtGap = detour.status() == ComputedPath.Status.FOUND;
        for (int i = 0; i < detour.nodeCount(); i++) {
            if (detour.x(i) == 5) crossesAtGap &= detour.z(i) == 6;
        }
        check(crossesAtGap, "engine pathfinder only crosses walls at real openings");

        System.out.println("\nSMOKE PASSED: " + passed + " checks");
    }
}
