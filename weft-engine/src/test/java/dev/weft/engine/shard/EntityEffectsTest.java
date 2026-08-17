package dev.weft.engine.shard;

import dev.weft.api.entity.EntityEffectLog;
import dev.weft.api.entity.EntitySpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityEffectsTest {

    /** Applier that records the exact application sequence. */
    private static final class RecordingApplier implements EntityEffects.Applier {
        final List<String> sequence = new ArrayList<>();
        final Map<Long, Long> itemWinners = new ConcurrentHashMap<>();
        final List<String> rejections = new ArrayList<>();
        boolean acceptClaims = true;

        @Override
        public void damage(long target, float amount, long source) {
            sequence.add("damage:" + target + ":" + amount + ":" + source);
        }

        @Override
        public boolean claimItem(long item, long claimant) {
            if (!acceptClaims) {
                return false;
            }
            itemWinners.put(item, claimant);
            sequence.add("claim:" + item + ":" + claimant);
            return true;
        }

        @Override
        public void claimRejected(long writeId, long claimant) {
            rejections.add(claimant + "#" + writeId);
        }

        @Override
        public void spawnEntity(EntitySpec spec) {
            sequence.add("spawn:" + spec.typeHandle());
        }
    }

    @Test
    void applicationOrderIsSourceOrderNotLogOrder() {
        // Same ops distributed across shard logs two different ways (as if
        // ticked under different shard counts) must apply identically.
        List<String> first = applySequence(2, 3);
        List<String> second = applySequence(5, 3);
        assertEquals(first, second);
        // And the order follows ascending source handle.
        assertTrue(first.get(0).endsWith(":0"));
        assertTrue(first.get(first.size() - 1).endsWith(":5"));
    }

    private List<String> applySequence(int shardCount, int opsPerSource) {
        List<EntityEffects.ShardLog> logs = new ArrayList<>();
        for (int s = 0; s < shardCount; s++) {
            logs.add(new EntityEffects.ShardLog());
        }
        // Sources 0..5 round-robin over the logs, mirroring the scheduler's
        // partition; each emits opsPerSource damage ops at target 100.
        for (int source = 0; source <= 5; source++) {
            EntityEffectLog view = logs.get(source % shardCount).forSource(source);
            for (int i = 0; i < opsPerSource; i++) {
                view.damage(100, 1.0f, source);
            }
        }
        RecordingApplier applier = new RecordingApplier();
        assertEquals(6 * opsPerSource, EntityEffects.applyAll(logs, applier));
        return applier.sequence;
    }

    @Test
    void contestedClaimFirstInDeterministicOrderWins() {
        EntityEffects.ShardLog logA = new EntityEffects.ShardLog();
        EntityEffects.ShardLog logB = new EntityEffects.ShardLog();
        // Higher handle recorded FIRST (in log order) — must still lose to
        // the lower source handle.
        long idOf9 = logB.forSource(9).claimItem(777, 9);
        long idOf3 = logA.forSource(3).claimItem(777, 3);

        RecordingApplier applier = new RecordingApplier();
        EntityEffects.applyAll(List.of(logA, logB), applier);

        assertEquals(3L, applier.itemWinners.get(777L));
        assertEquals(List.of("9#" + idOf9), applier.rejections);
        assertEquals(0L, idOf3); // per-source sequence numbers start at 0
    }

    @Test
    void worldSideRejectionReportsTheWinnerToo() {
        EntityEffects.ShardLog log = new EntityEffects.ShardLog();
        long writeId = log.forSource(4).claimItem(555, 4);

        RecordingApplier applier = new RecordingApplier();
        applier.acceptClaims = false; // stack despawned before apply
        EntityEffects.applyAll(List.of(log), applier);

        assertEquals(List.of("4#" + writeId), applier.rejections);
        assertTrue(applier.itemWinners.isEmpty());
    }

    @Test
    void perSourceEmissionOrderIsPreserved() {
        EntityEffects.ShardLog log = new EntityEffects.ShardLog();
        EntityEffectLog view = log.forSource(7);
        view.damage(1, 1.0f, 7);
        view.spawnEntity(new EntitySpec(99, 0, 0, 0));
        view.damage(2, 2.0f, 7);

        RecordingApplier applier = new RecordingApplier();
        EntityEffects.applyAll(List.of(log), applier);
        assertEquals(List.of("damage:1:1.0:7", "spawn:99", "damage:2:2.0:7"), applier.sequence);
    }
}
