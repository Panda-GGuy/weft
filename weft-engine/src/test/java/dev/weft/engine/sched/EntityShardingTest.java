package dev.weft.engine.sched;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.guard.ThreadContext;
import dev.weft.engine.guard.WeftGuards;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.region.ShardKey;
import dev.weft.engine.shard.EntityEffects;
import dev.weft.engine.shard.ShardContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-10 (RFC-0004) engine-level acceptance in miniature: exact-once ticking,
 * conservation parity across shard counts, RNG reproducibility, guard
 * enforcement, and deterministic claim arbitration.
 */
class EntityShardingTest {

    /** Tallying applier shared by the parity scenarios. */
    private static final class Tally implements EntityEffects.Applier {
        final Map<Long, Double> damageByTarget = new ConcurrentHashMap<>();
        final Map<Long, Long> itemWinners = new ConcurrentHashMap<>();
        final List<String> rejections = new ArrayList<>();

        @Override
        public void damage(long target, float amount, long source) {
            damageByTarget.merge(target, (double) amount, Double::sum);
        }

        @Override
        public boolean claimItem(long item, long claimant) {
            itemWinners.put(item, claimant);
            return true;
        }

        @Override
        public void claimRejected(long writeId, long claimant) {
            rejections.add(claimant + "#" + writeId);
        }
    }

    private static WeftScheduler scheduler(RegionManager rm, int parallelism) {
        return new WeftScheduler(parallelism, rm,
                new GraphScheduler((g, t) -> null), new WeftScheduler.Hooks() {});
    }

    private static Region singleRegion(RegionManager rm) {
        return rm.addChunk(0, 0);
    }

    @Test
    void everyTickableTicksExactlyOncePerTick() throws Exception {
        RegionManager rm = new RegionManager(2, 42L);
        Region region = singleRegion(rm);
        int count = 500;
        AtomicInteger[] ticks = new AtomicInteger[count];
        for (int i = 0; i < count; i++) {
            AtomicInteger counter = ticks[i] = new AtomicInteger();
            region.addTickable(new Region.Tickable() {
                @Override
                public void tick(Region r, long tickNumber) {
                    throw new AssertionError("serial path must not be used when sharded");
                }

                @Override
                public void tick(Region r, long tickNumber, ShardContext shard) {
                    counter.incrementAndGet();
                }
            });
        }
        try (WeftScheduler sched = scheduler(rm, 4)) {
            sched.setEntitySharding(true, 50); // 500/50 -> capped at 4 shards
            sched.tick();
            sched.tick();
            sched.tick();
        }
        for (AtomicInteger counter : ticks) {
            assertEquals(3, counter.get());
        }
    }

    @Test
    void disabledShardingIsExactlyTheSerialPath() throws Exception {
        RegionManager rm = new RegionManager(2, 42L);
        Region region = singleRegion(rm);
        AtomicInteger serialCalls = new AtomicInteger();
        for (int i = 0; i < 200; i++) {
            region.addTickable((r, t) -> serialCalls.incrementAndGet());
        }
        try (WeftScheduler sched = scheduler(rm, 4)) {
            sched.tick();
            assertEquals(0, sched.lastShardedRegions());
            assertEquals(1, sched.lastMaxShards());
        }
        assertEquals(200, serialCalls.get());
    }

    /**
     * Conservation parity (RFC-0004 §4): identical damage totals and
     * identical claim winners at every shard count, because resolution order
     * is (source, seq), not thread or shard order.
     */
    @Test
    void outcomesIdenticalAcrossShardCounts() throws Exception {
        Tally two = runCombatScenario(2);
        Tally four = runCombatScenario(4);
        Tally eight = runCombatScenario(8);

        assertEquals(two.damageByTarget, four.damageByTarget);
        assertEquals(two.damageByTarget, eight.damageByTarget);
        assertEquals(two.itemWinners, four.itemWinners);
        assertEquals(two.itemWinners, eight.itemWinners);
        assertEquals(two.rejections, four.rejections);
        assertEquals(two.rejections, eight.rejections);

        // Ground truth: item i is contested by every claimant with
        // handle % 5 == i; the lowest handle wins under (source, seq) order.
        for (long item = 0; item < 5; item++) {
            assertEquals(item, two.itemWinners.get(item),
                    "lowest-handle claimant must win item " + item);
        }
    }

    private Tally runCombatScenario(int shardCount) throws Exception {
        RegionManager rm = new RegionManager(2, 42L);
        Region region = singleRegion(rm);
        final int population = 320; // fixed regardless of shard count
        for (int i = 0; i < population; i++) {
            final long handle = i;
            region.addTickable(new Region.Tickable() {
                @Override
                public void tick(Region r, long tickNumber) {
                    throw new AssertionError("must run sharded");
                }

                @Override
                public void tick(Region r, long tickNumber, ShardContext shard) {
                    var effects = shard.effects(handle);
                    effects.damage(handle % 10, 1.5f, handle);
                    effects.claimItem(handle % 5, handle);
                }
            });
        }
        Tally tally = new Tally();
        // Parallelism 8 so the pool never caps the requested shard count.
        try (WeftScheduler sched = scheduler(rm, 8)) {
            sched.setEntitySharding(true, population / shardCount);
            sched.setEntityEffectApplier(tally);
            sched.tick();
            assertEquals(shardCount, sched.lastMaxShards());
        }
        return tally;
    }

    /** RNG reproducibility (RFC-0004 §4): same seed + shard count -> same draws. */
    @Test
    void rngReproducibleAcrossRuns() throws Exception {
        List<Long> first = runRngScenario();
        List<Long> second = runRngScenario();
        assertEquals(first, second);
    }

    private List<Long> runRngScenario() throws Exception {
        RegionManager rm = new RegionManager(2, 42L);
        Region region = singleRegion(rm);
        int count = 256;
        AtomicLong[] draws = new AtomicLong[count];
        for (int i = 0; i < count; i++) {
            AtomicLong slot = draws[i] = new AtomicLong();
            region.addTickable(new Region.Tickable() {
                @Override
                public void tick(Region r, long tickNumber) {}

                @Override
                public void tick(Region r, long tickNumber, ShardContext shard) {
                    slot.addAndGet(shard.random().nextLong());
                }
            });
        }
        try (WeftScheduler sched = scheduler(rm, 4)) {
            sched.setEntitySharding(true, 64);
            sched.tick();
            sched.tick();
        }
        List<Long> out = new ArrayList<>(count);
        for (AtomicLong draw : draws) {
            out.add(draw.get());
        }
        return out;
    }

    /**
     * Guard rule (RFC-0004 §2.2): own-shard direct mutation passes; another
     * shard's state and region-shared state are both denied from SHARD
     * context. Zero trips on the compliant path is the §4 acceptance bar.
     */
    @Test
    void guardsEnforceShardOwnership() throws Exception {
        WeftGuards.setMode(WeftGuards.Mode.DEGRADE);
        try {
            RegionManager rm = new RegionManager(2, 42L);
            Region region = singleRegion(rm);
            long regionId = region.id();
            AtomicInteger ownAllowed = new AtomicInteger();
            AtomicInteger foreignAllowed = new AtomicInteger();
            AtomicInteger regionAllowed = new AtomicInteger();
            for (int i = 0; i < 128; i++) {
                region.addTickable(new Region.Tickable() {
                    @Override
                    public void tick(Region r, long tickNumber) {}

                    @Override
                    public void tick(Region r, long tickNumber, ShardContext shard) {
                        if (WeftGuards.checkShardMutation(shard.shardKey())) {
                            ownAllowed.incrementAndGet();
                        }
                        long foreign = ShardKey.pack(regionId, shard.shardIndex() + 1);
                        if (WeftGuards.checkShardMutation(foreign)) {
                            foreignAllowed.incrementAndGet();
                        }
                        if (WeftGuards.checkRegionMutation(regionId)) {
                            regionAllowed.incrementAndGet();
                        }
                    }
                });
            }
            long tripsBefore = WeftGuards.tripCount();
            try (WeftScheduler sched = scheduler(rm, 4)) {
                sched.setEntitySharding(true, 32);
                sched.tick();
            }
            assertEquals(128, ownAllowed.get());
            assertEquals(0, foreignAllowed.get());
            assertEquals(0, regionAllowed.get());
            assertEquals(256, WeftGuards.tripCount() - tripsBefore);
        } finally {
            WeftGuards.setMode(WeftGuards.Mode.DEV);
        }
    }

    /** Serial-path compatibility: REGION context may mutate its own shards' entities. */
    @Test
    void regionContextOwnsItsShardsOnSerialPath() {
        ThreadContext.enter(ThreadContext.Kind.REGION, 7);
        try {
            assertTrue(WeftGuards.checkShardMutation(ShardKey.pack(7, 0)));
            assertTrue(WeftGuards.checkShardMutation(ShardKey.pack(7, 3)));
        } finally {
            ThreadContext.exit();
        }
    }
}
