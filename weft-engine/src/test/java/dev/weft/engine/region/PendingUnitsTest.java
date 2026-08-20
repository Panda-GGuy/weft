package dev.weft.engine.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RFC-0007 §4 requirement 1: the fused region task needs a per-region pending
 * container whose semantics match vanilla's {@code tickBlockEntities} pair of
 * lists exactly (decompile-verified, see the class doc): pre-tick adds run
 * this tick, mid-tick adds run next tick, removal prunes during iteration,
 * and the entity-stage-then-BE-stage fusion ordering keeps entity-added units
 * ticking the same tick.
 */
class PendingUnitsTest {

    @Test
    void unitsAddedBeforeTickRunThisTick() {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("a");
        pu.add("b");
        List<String> ran = new ArrayList<>();
        pu.tick(u -> false, ran::add);
        assertEquals(List.of("a", "b"), ran, "pre-tick adds run this tick, in add order");
    }

    @Test
    void midTickAddsDeferToNextTickExactlyLikeVanillaPending() {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("a");
        List<String> firstTick = new ArrayList<>();
        pu.tick(u -> false, u -> {
            firstTick.add(u);
            if (u.equals("a")) {
                pu.add("added-mid-tick");
            }
        });
        assertEquals(List.of("a"), firstTick, "mid-tick add must not run this tick");

        List<String> secondTick = new ArrayList<>();
        pu.tick(u -> false, secondTick::add);
        assertEquals(List.of("a", "added-mid-tick"), secondTick,
                "pending merges at the head of the next tick, after existing units");
    }

    @Test
    void entityStageAddsTickSameTickUnderFusionOrdering() {
        // The fused task shape: entity stage runs first (NOT mid-tick for the
        // BE container), then the BE stage ticks. Vanilla gets the same
        // result because tickBlockEntities snapshots AFTER the entity
        // section; this is the property fusion must preserve per region.
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("existing-be");
        // entity stage: a falling block lands, placing a new ticking BE
        pu.add("entity-added-be");
        List<String> ran = new ArrayList<>();
        pu.tick(u -> false, ran::add);
        assertEquals(List.of("existing-be", "entity-added-be"), ran,
                "entity-stage additions must tick the same tick (vanilla: snapshot at BE-section entry)");
    }

    @Test
    void removedUnitsArePrunedDuringIteration() {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("keep");
        pu.add("dead");
        List<String> ran = new ArrayList<>();
        pu.tick("dead"::equals, ran::add);
        assertEquals(List.of("keep"), ran);
        List<String> ranAgain = new ArrayList<>();
        pu.tick(u -> false, ranAgain::add);
        assertEquals(List.of("keep"), ranAgain, "a removed unit is pruned, not re-visited");
    }

    @Test
    void reentrantTickFailsLoud() {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("a");
        AtomicReference<IllegalStateException> caught = new AtomicReference<>();
        pu.tick(u -> false, u -> {
            try {
                pu.tick(x -> false, x -> {});
            } catch (IllegalStateException e) {
                caught.set(e);
            }
        });
        assertNotNull(caught.get(), "re-entrant tick is an ownership bug and must throw");
    }

    @Test
    void drainAllReturnsLiveAndPendingInOrderAndEmpties() {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("live-1");
        pu.add("live-2");
        pu.tick(u -> false, u -> {
            if (u.equals("live-1")) {
                pu.add("pending-1");
            }
        });
        assertEquals(List.of("live-1", "live-2", "pending-1"), pu.drainAll(),
                "drain returns live then pending, in add order");
        assertTrue(pu.isEmpty(), "drain must empty the container");
        assertEquals(0, pu.size());
    }

    @Test
    void drainAllMidTickFailsLoud() {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("a");
        AtomicReference<IllegalStateException> caught = new AtomicReference<>();
        pu.tick(u -> false, u -> {
            try {
                pu.drainAll();
            } catch (IllegalStateException e) {
                caught.set(e);
            }
        });
        assertNotNull(caught.get(), "mid-tick drain races the owner and must throw");
    }

    @Test
    void concurrentAddDuringTickLandsInPendingNotThisIteration() throws Exception {
        PendingUnits<String> pu = new PendingUnits<>();
        pu.add("a");
        pu.add("b");
        CountDownLatch inTick = new CountDownLatch(1);
        CountDownLatch posted = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> ran = new ConcurrentLinkedQueue<>();

        Thread adder = new Thread(() -> {
            try {
                assertTrue(inTick.await(5, TimeUnit.SECONDS));
                pu.add("concurrent");
                posted.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        adder.start();

        pu.tick(u -> false, u -> {
            ran.add(u);
            if (u.equals("a")) {
                inTick.countDown();
                try {
                    // Make the cross-thread add land while we are mid-tick.
                    assertTrue(posted.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        adder.join(5000);

        assertEquals(Set.of("a", "b"), Set.copyOf(ran),
                "a concurrent add must not join the fixed iteration set");
        List<String> next = new ArrayList<>();
        pu.tick(u -> false, next::add);
        assertTrue(next.contains("concurrent"), "the concurrent add runs next tick");
    }
}