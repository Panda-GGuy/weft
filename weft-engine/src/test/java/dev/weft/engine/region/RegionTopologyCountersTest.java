package dev.weft.engine.region;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WS-7 needs merges and splits both as a rate and as discrete events
 * (RFC-0009 §2, §5), and neither was counted before. These assert the counters
 * and the listener against the topology behaviour {@link RegionManagerTest}
 * already pins, so a change to either cannot quietly stop reporting.
 */
class RegionTopologyCountersTest {

    private record Merge(long resultId, List<Long> absorbed, int chunksAfter) {}

    private record Split(long sourceId, List<Long> results, int chunksAfter) {}

    private static final int MERGE_DISTANCE = 2;

    private final List<Merge> merges = new ArrayList<>();
    private final List<Split> splits = new ArrayList<>();

    private RegionManager observed() {
        RegionManager manager = new RegionManager(MERGE_DISTANCE, 0L);
        manager.setTopologyListener(new RegionManager.TopologyListener() {
            @Override
            public void onMerge(long resultId, long[] absorbedIds, int chunksAfter) {
                merges.add(new Merge(resultId, box(absorbedIds), chunksAfter));
            }

            @Override
            public void onSplit(long sourceId, long[] resultIds, int chunksAfter) {
                splits.add(new Split(sourceId, box(resultIds), chunksAfter));
            }
        });
        return manager;
    }

    private static List<Long> box(long[] values) {
        List<Long> out = new ArrayList<>(values.length);
        for (long value : values) {
            out.add(value);
        }
        return out;
    }

    @Test
    void anIsolatedChunkLoadIsNotAMerge() {
        RegionManager manager = observed();
        manager.addChunk(0, 0);
        manager.addChunk(100, 100);

        assertEquals(0, manager.merges());
        assertTrue(merges.isEmpty(), "two distant chunks form two regions, no merge");
    }

    @Test
    void aBridgingLoadReportsOneMergeNamingEveryAbsorbedRegion() {
        RegionManager manager = observed();
        // Two regions far enough apart to stay separate...
        Region left = manager.addChunk(0, 0);
        Region right = manager.addChunk(MERGE_DISTANCE * 2, 0);
        assertEquals(2, manager.all().size());
        merges.clear();

        // ...then one chunk in the gap bridges them.
        manager.addChunk(MERGE_DISTANCE, 0);

        assertEquals(1, manager.all().size());
        assertEquals(1, manager.merges(), "one region was absorbed");
        assertEquals(1, merges.size(), "one event per bridging load, not per absorbed region");
        Merge merge = merges.get(0);
        assertEquals(3, merge.chunksAfter(), "chunk count is reported after the load lands");
        assertTrue(merge.absorbed().contains(left.id()) || merge.absorbed().contains(right.id()));
        assertTrue(List.of(left.id(), right.id()).contains(merge.resultId()));
    }

    @Test
    void aDisconnectingRemovalReportsTheSplitAndItsNewRegionIds() {
        RegionManager manager = observed();
        // A line of chunks, connected only through the middle one.
        manager.addChunk(0, 0);
        Region bridge = manager.addChunk(MERGE_DISTANCE, 0);
        manager.addChunk(MERGE_DISTANCE * 2, 0);
        assertEquals(1, manager.all().size());
        merges.clear();
        splits.clear();

        manager.removeChunk(MERGE_DISTANCE, 0);
        manager.recomputeSplits();

        assertEquals(2, manager.all().size());
        assertEquals(1, manager.splits(), "one region was shed");
        assertEquals(1, splits.size());
        Split split = splits.get(0);
        assertEquals(bridge.id(), split.sourceId());
        assertEquals(1, split.results().size());
        assertEquals(1, split.chunksAfter(), "the surviving parent kept one chunk");
    }

    @Test
    void countersAreCumulativeSoARateIsMeaningful() {
        RegionManager manager = observed();
        for (int i = 0; i < 5; i++) {
            // Cycles spaced far enough apart that they cannot merge into
            // each other; within a cycle, a gap of exactly 2*mergeDistance is
            // the widest a single chunk can bridge.
            int base = i * 16;
            manager.addChunk(base, 0);
            manager.addChunk(base + MERGE_DISTANCE * 2, 0);
            manager.addChunk(base + MERGE_DISTANCE, 0);
        }
        // Five independent bridge-and-merge cycles: a Prometheus counter has to
        // be monotonic or rate() over it is nonsense.
        assertEquals(5, manager.merges());
    }

    @Test
    void detachingTheListenerLeavesNoResidue() {
        RegionManager manager = observed();
        manager.setTopologyListener(null);

        manager.addChunk(0, 0);
        manager.addChunk(MERGE_DISTANCE * 2, 0);
        manager.addChunk(MERGE_DISTANCE, 0);

        // R6: a disabled module must not be observable at all. The counters keep
        // running (they are two LongAdders on a chunk-load path, not the tick
        // path) but nothing is notified.
        assertTrue(merges.isEmpty());
        assertEquals(1, manager.merges());
    }
}
