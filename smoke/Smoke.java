import dev.weft.api.graph.*;
import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.WeftGuards;
import dev.weft.engine.mail.Mailbox;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;

import java.util.*;
import java.util.concurrent.*;
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

        System.out.println("\nSMOKE PASSED: " + passed + " checks");
    }
}
