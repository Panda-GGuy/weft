package dev.weft.engine.region;

import dev.weft.engine.mail.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RFC-0007 §3.3: mail queued in a region's mailbox must survive every
 * topology mutation — merges follow the chunks (absorb reposts, in order),
 * splits and empty-region drops go to the stranded-mail sink (the loader
 * wires it to the global inbox), and no mail is ever silently dropped or
 * double-delivered while the region stays mapped.
 */
class RegionMailTest {

    private static Message.Task task(String tag) {
        return new Message.Task(() -> {
            throw new AssertionError("test mail must never be executed: " + tag);
        });
    }

    /** A genuine cut sends the parent's queued mail to the sink, exactly once. */
    @Test
    void splitDrainsParentMailToSink() {
        RegionManager rm = new RegionManager(2, 42L);
        List<Message> stranded = new ArrayList<>();
        rm.setStrandedMailSink(stranded::add);

        // Two clusters joined by one bridge chunk (Chebyshev distance 2 apart
        // via the bridge; removing it disconnects them).
        rm.addChunk(0, 0);
        rm.addChunk(2, 0); // bridge
        rm.addChunk(4, 0);
        assertEquals(1, rm.all().size(), "bridged clusters must be one region");

        Region region = rm.regionAt(0, 0);
        Message m1 = task("m1");
        Message m2 = task("m2");
        region.mailbox().post(m1);
        region.mailbox().post(m2);

        rm.removeChunk(2, 0);
        rm.recomputeSplits();

        assertEquals(2, rm.all().size(), "cut must split the region");
        assertEquals(List.of(m1, m2), stranded, "parent's mail must reach the sink in order");
        for (Region r : rm.all()) {
            assertTrue(r.mailbox().isEmpty(), "no region may keep mail across a split");
        }
    }

    /** Dropping an emptied region reroutes its queued mail instead of losing it. */
    @Test
    void emptyRegionRemovalDrainsMailToSink() {
        RegionManager rm = new RegionManager(2, 42L);
        List<Message> stranded = new ArrayList<>();
        rm.setStrandedMailSink(stranded::add);

        rm.addChunk(0, 0);
        Region region = rm.regionAt(0, 0);
        Message m1 = task("m1");
        region.mailbox().post(m1);

        rm.removeChunk(0, 0);

        assertEquals(0, rm.all().size());
        assertEquals(List.of(m1), stranded, "dropped region's mail must reach the sink");
    }

    /** Merge keeps mail with its chunks: absorb reposts in order, sink untouched. */
    @Test
    void mergeRepostsVictimMailIntoAbsorberInOrder() {
        RegionManager rm = new RegionManager(2, 42L);
        List<Message> stranded = new ArrayList<>();
        rm.setStrandedMailSink(stranded::add);

        // Two regions far apart; the larger one will absorb on merge.
        rm.addChunk(0, 0);
        rm.addChunk(0, 1);
        rm.addChunk(10, 0);
        assertEquals(2, rm.all().size());

        Region small = rm.regionAt(10, 0);
        Message m1 = task("m1");
        Message m2 = task("m2");
        small.mailbox().post(m1);
        small.mailbox().post(m2);

        // Bridge them: (5,0) is within mergeDistance 2 of neither... use a
        // chain so each new chunk merges into the growing region.
        rm.addChunk(2, 0);
        rm.addChunk(4, 0);
        rm.addChunk(6, 0);
        rm.addChunk(8, 0);
        assertEquals(1, rm.all().size(), "chain must merge everything into one region");

        Region merged = rm.regionAt(0, 0);
        assertEquals(List.of(m1, m2), merged.mailbox().drain(),
                "victim's mail must follow its chunks into the absorber, in order");
        assertTrue(stranded.isEmpty(), "a merge must never strand mail");
    }

    /** Benign load/unload on a mapped region leaves its mail alone. */
    @Test
    void benignChurnNeverTouchesMail() {
        RegionManager rm = new RegionManager(2, 42L);
        List<Message> stranded = new ArrayList<>();
        rm.setStrandedMailSink(stranded::add);

        rm.addChunk(0, 0);
        rm.addChunk(1, 0);
        rm.addChunk(2, 0);
        Region region = rm.regionAt(0, 0);
        Message m1 = task("m1");
        region.mailbox().post(m1);

        rm.addChunk(3, 0);          // grow
        rm.removeChunk(3, 0);       // shrink from the edge: no split possible
        rm.recomputeSplits();

        assertEquals(1, rm.all().size());
        assertTrue(stranded.isEmpty(), "benign churn must not strand mail");
        assertEquals(List.of(m1), region.mailbox().drain(), "mail must still be queued");
    }
}
