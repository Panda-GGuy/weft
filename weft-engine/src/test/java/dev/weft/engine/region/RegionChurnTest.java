package dev.weft.engine.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards for the pregen churn regression (2026-08-17: Chunky ran ~20 cps
 * slower with the always-on topology feed). A pregen-shaped load/unload storm
 * must not do split-BFS work — a trailing-edge removal provably cannot split
 * its region — and reseed must stay O(log n), not a full-set scan. The
 * nightly {@code RegionChurnStormBench} JMH trend tracks the absolute cost;
 * these tests pin the algorithmic properties deterministically.
 */
class RegionChurnTest {

    /** Serpentine sweep with a trailing unload window: Chunky's shape. */
    private static void storm(RegionManager rm, int gridW, int gridH, int window,
                              int splitEvery, Runnable everyRecompute) {
        ArrayDeque<long[]> loaded = new ArrayDeque<>();
        int adds = 0;
        for (int z = 0; z < gridH; z++) {
            for (int i = 0; i < gridW; i++) {
                int x = (z % 2 == 0) ? i : gridW - 1 - i;
                rm.addChunk(x, z);
                adds++;
                loaded.addLast(new long[]{x, z});
                if (loaded.size() > window) {
                    long[] old = loaded.pollFirst();
                    rm.removeChunk((int) old[0], (int) old[1]);
                }
                if (adds % splitEvery == 0) {
                    rm.recomputeSplits();
                    everyRecompute.run();
                }
            }
        }
    }

    /**
     * The regression guard: a contiguous sweep's trailing unloads never
     * disconnect the region, so the local no-split proof must skip the BFS
     * for every single one — pending split work stays at zero for the whole
     * storm. If this fails, every 20-tick recompute during pregen walks the
     * entire loaded set again.
     */
    @Test
    void pregenShapedChurnNeverQueuesSplitWork() {
        RegionManager rm = new RegionManager(8, 42L);
        storm(rm, 60, 60, 400, 100,
                () -> assertEquals(0, rm.pendingSplitChecks(),
                        "trailing-edge removal queued split work"));
        assertEquals(0, rm.pendingSplitChecks());
        assertEquals(1, rm.all().size(), "a contiguous sweep is one region");
        assertEquals(400, rm.chunkCount());
    }

    /** The storm must leave the mapping consistent: a partition, no strays. */
    @Test
    void churnPreservesMappingInvariants() {
        RegionManager rm = new RegionManager(4, 7L);
        storm(rm, 40, 40, 150, 50, () -> {});
        rm.recomputeSplits();

        int totalChunks = rm.all().stream().mapToInt(r -> r.chunks().size()).sum();
        assertEquals(rm.chunkCount(), totalChunks,
                "regions must partition the tracked chunks");
        for (Region r : rm.all()) {
            for (long key : r.chunks()) {
                assertSame(r, rm.regionAt(ChunkKey.x(key), ChunkKey.z(key)),
                        "chunk set and chunk->region map disagree");
            }
        }
    }

    /** A genuine cut still queues exactly its region and still splits. */
    @Test
    void genuineCutQueuesAndSplits() {
        RegionManager rm = new RegionManager(1, 42L);
        for (int x = 0; x <= 4; x++) {
            rm.addChunk(x, 0);
        }
        Region before = rm.regionAt(0, 0);
        assertEquals(0, rm.pendingSplitChecks());

        rm.removeChunk(2, 0);
        assertEquals(1, rm.pendingSplitChecks(), "a real cut must queue its region");
        rm.recomputeSplits();
        assertEquals(0, rm.pendingSplitChecks());
        assertEquals(2, rm.all().size());
        assertNotSame(rm.regionAt(0, 0), rm.regionAt(4, 0));

        // The no-split fast path must not have swapped region identity.
        assertTrue(rm.regionAt(0, 0) == before || rm.regionAt(4, 0) == before,
                "the surviving component keeps its region");
    }

    /**
     * A removal that only shrinks the edge must not queue split work and must
     * keep the region instance (no spurious split/rebuild).
     */
    @Test
    void edgeShrinkIsFreeOfSplitWork() {
        RegionManager rm = new RegionManager(2, 42L);
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                rm.addChunk(x, z);
            }
        }
        Region region = rm.regionAt(3, 3);
        rm.removeChunk(0, 0);
        rm.removeChunk(5, 5);
        assertEquals(0, rm.pendingSplitChecks(),
                "edge shrink can never split; the local proof must catch it");
        rm.recomputeSplits();
        assertSame(region, rm.regionAt(3, 3));
        assertEquals(1, rm.all().size());
    }

    /**
     * RFC-0001 §6.6: the region RNG derives from the world seed and the
     * minimum chunk key, so the same final chunk set gives the same stream
     * no matter what order built it — pins the reseed change (sorted set
     * first() instead of a full-scan min) to identical semantics.
     */
    @Test
    void reseedDependsOnlyOnChunkSetMinimum() {
        RegionManager a = new RegionManager(2, 99L);
        a.addChunk(3, 3);
        a.addChunk(4, 3);
        a.addChunk(5, 3);

        RegionManager b = new RegionManager(2, 99L);
        b.addChunk(5, 3);
        b.addChunk(3, 3);
        b.addChunk(4, 3);

        assertEquals(a.regionAt(3, 3).random().nextLong(),
                b.regionAt(3, 3).random().nextLong(),
                "same chunk set must reseed to the same stream");
    }

    /**
     * Wall-time canary with a wide margin, not a benchmark: the pre-fix code
     * ran this storm in ~25 s (whole-map BFS every recompute plus an O(n)
     * reseed per event); the fixed paths run it in ~1 s. The bound only
     * exists to fail loudly if either term goes super-linear again — precise
     * numbers belong to the nightly {@code RegionChurnStormBench}.
     */
    @Test
    void churnStormWallTimeCanary() {
        RegionManager rm = new RegionManager(8, 42L);
        long t0 = System.nanoTime();
        storm(rm, 200, 200, 8192, 100, () -> {});
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertTrue(elapsedMs < 10_000,
                "churn storm took " + elapsedMs + " ms - a topology hot path went O(n) again");
    }
}
