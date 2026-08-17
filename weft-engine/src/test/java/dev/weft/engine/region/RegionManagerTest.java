package dev.weft.engine.region;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegionManagerTest {

    @Test
    void nearbyChunksShareOneRegion() {
        RegionManager rm = new RegionManager(2, 42L);
        Region a = rm.addChunk(0, 0);
        Region b = rm.addChunk(1, 1);
        assertSame(a, b, "chunks within merge distance must share a region");
        assertEquals(1, rm.all().size());
    }

    @Test
    void distantChunksGetSeparateRegions() {
        RegionManager rm = new RegionManager(2, 42L);
        Region a = rm.addChunk(0, 0);
        Region b = rm.addChunk(100, 100);
        assertNotSame(a, b);
        assertEquals(2, rm.all().size());
    }

    @Test
    void bridgingChunkMergesRegions() {
        RegionManager rm = new RegionManager(1, 42L);
        rm.addChunk(0, 0);
        rm.addChunk(4, 0);
        assertEquals(2, rm.all().size());
        rm.addChunk(2, 0); // within distance 1 of neither? distance to (0,0)=2, (4,0)=2 -> no merge
        assertEquals(3, rm.all().size());
        rm.addChunk(1, 0); // bridges (0,0) and (2,0)
        rm.addChunk(3, 0); // bridges the rest
        assertEquals(1, rm.all().size());
        assertSame(rm.regionAt(0, 0), rm.regionAt(4, 0));
    }

    @Test
    void removalThenSplitProducesConnectedComponents() {
        RegionManager rm = new RegionManager(1, 42L);
        for (int x = 0; x <= 4; x++) {
            rm.addChunk(x, 0);
        }
        assertEquals(1, rm.all().size());

        rm.removeChunk(2, 0); // cut the line in half
        rm.recomputeSplits();

        assertEquals(2, rm.all().size());
        assertNotSame(rm.regionAt(0, 0), rm.regionAt(4, 0));
        assertSame(rm.regionAt(0, 0), rm.regionAt(1, 0));
        assertSame(rm.regionAt(3, 0), rm.regionAt(4, 0));
    }

    @Test
    void everyLoadedChunkHasExactlyOneRegion() {
        RegionManager rm = new RegionManager(2, 42L);
        for (int x = 0; x < 20; x += 3) {
            for (int z = 0; z < 20; z += 3) {
                rm.addChunk(x, z);
            }
        }
        int totalChunks = rm.all().stream().mapToInt(r -> r.chunks().size()).sum();
        assertEquals(49, totalChunks, "no chunk lost or duplicated across regions");
        for (int x = 0; x < 20; x += 3) {
            for (int z = 0; z < 20; z += 3) {
                assertNotNull(rm.regionAt(x, z));
            }
        }
    }

    @Test
    void mergeCarriesMailAndTickables() {
        RegionManager rm = new RegionManager(1, 42L);
        Region a = rm.addChunk(0, 0);
        Region b = rm.addChunk(4, 0);
        b.addTickable((r, t) -> {});
        b.mailbox().post(new dev.weft.engine.mail.Message.Task(() -> {}));

        rm.addChunk(2, 0);
        rm.addChunk(1, 0);
        rm.addChunk(3, 0); // full bridge -> one region

        Region merged = rm.regionAt(0, 0);
        assertEquals(1, rm.all().size());
        assertEquals(1, merged.tickablesView().size(), "tickables must survive merges");
        assertFalse(merged.mailbox().isEmpty(), "mail must survive merges");
    }
}
