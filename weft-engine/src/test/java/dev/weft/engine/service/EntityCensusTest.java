package dev.weft.engine.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityCensusTest {

    private static final int CATS = 4;
    private static final int MONSTER = 0;
    private static final int CREATURE = 1;

    @Test
    void addRemoveMoveMaintainCounts() {
        EntityCensus census = new EntityCensus(CATS);
        census.add(1, MONSTER, 100L);
        census.add(2, MONSTER, 100L);
        census.add(3, CREATURE, 200L);

        var counts = census.snapshot();
        assertEquals(2, counts.global(MONSTER));
        assertEquals(1, counts.global(CREATURE));
        assertEquals(2, counts.inChunk(100L, MONSTER));
        assertEquals(0, counts.inChunk(100L, CREATURE));
        assertEquals(3, census.trackedCount());

        census.move(1, 300L);
        counts = census.snapshot();
        assertEquals(2, counts.global(MONSTER), "move must not change global counts");
        assertEquals(1, counts.inChunk(100L, MONSTER));
        assertEquals(1, counts.inChunk(300L, MONSTER));

        census.remove(2);
        census.remove(2); // double-remove is a no-op
        counts = census.snapshot();
        assertEquals(1, counts.global(MONSTER));
        assertEquals(0, counts.inChunk(100L, MONSTER));
        assertEquals(2, census.trackedCount());
    }

    @Test
    void moveAndRemoveOfUntrackedIdsAreNoOps() {
        EntityCensus census = new EntityCensus(CATS);
        census.move(99, 100L);
        census.remove(99);
        assertEquals(0, census.trackedCount());
        assertEquals(0, census.snapshot().global(MONSTER));
    }

    @Test
    void reAddReplacesPriorMembership() {
        EntityCensus census = new EntityCensus(CATS);
        census.add(1, MONSTER, 100L);
        census.add(1, CREATURE, 200L); // same id joins again (e.g. respawned id reuse)
        var counts = census.snapshot();
        assertEquals(0, counts.global(MONSTER));
        assertEquals(1, counts.global(CREATURE));
        assertEquals(1, census.trackedCount());
    }

    @Test
    void snapshotIsImmutableCopy() {
        EntityCensus census = new EntityCensus(CATS);
        census.add(1, MONSTER, 100L);
        var counts = census.snapshot();
        census.add(2, MONSTER, 100L);
        assertEquals(1, counts.global(MONSTER), "snapshot must not see later mutations");
        assertThrows(UnsupportedOperationException.class,
                () -> counts.perChunk().put(1L, new int[CATS]));
    }

    @Test
    void reconcileReportsDriftAndRepairs() {
        EntityCensus census = new EntityCensus(CATS);
        census.add(1, MONSTER, 100L);  // will have moved chunks in truth
        census.add(2, CREATURE, 200L); // stale: gone from truth (e.g. tamed -> exempt)
        // id 3 missing: joined without an event reaching us

        int[] ids = {1, 3};
        int[] cats = {MONSTER, CREATURE};
        long[] chunks = {300L, 400L};
        EntityCensus.Drift drift = census.reconcile(ids, cats, chunks, 2);

        assertEquals(1, drift.missing());
        assertEquals(1, drift.stale());
        assertEquals(1, drift.moved());
        assertEquals(3, drift.total());

        var counts = census.snapshot();
        assertEquals(1, counts.global(MONSTER));
        assertEquals(1, counts.global(CREATURE));
        assertEquals(1, counts.inChunk(300L, MONSTER));
        assertEquals(1, counts.inChunk(400L, CREATURE));
        assertEquals(0, counts.inChunk(100L, MONSTER));
        assertEquals(2, census.trackedCount());
    }

    @Test
    void cleanCensusReconcilesWithZeroDrift() {
        EntityCensus census = new EntityCensus(CATS);
        census.add(1, MONSTER, 100L);
        census.add(2, CREATURE, 200L);

        int[] ids = {1, 2};
        int[] cats = {MONSTER, CREATURE};
        long[] chunks = {100L, 200L};
        EntityCensus.Drift drift = census.reconcile(ids, cats, chunks, 2);
        assertEquals(0, drift.total(), "event-fed census matching truth must report zero drift");
    }

    @Test
    void emptyChunkEntriesAreCleanedUp() {
        EntityCensus census = new EntityCensus(CATS);
        census.add(1, MONSTER, 100L);
        census.remove(1);
        assertTrue(census.snapshot().perChunk().isEmpty(),
                "zeroed chunk rows must not accumulate over a long-running server");
    }
}
