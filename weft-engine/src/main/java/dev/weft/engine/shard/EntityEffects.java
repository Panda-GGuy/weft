package dev.weft.engine.shard;

import dev.weft.api.entity.EntityEffectLog;
import dev.weft.api.entity.EntitySpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Engine-side recording and deterministic application of entity effect logs
 * (RFC-0004 §2.3) — the entity-layer twin of
 * {@code GraphScheduler.RecordingCommitLog}.
 *
 * <p>Each shard records into its own {@link ShardLog} during the parallel
 * pass (pure recording, no shared state). After every shard in a region has
 * finished, {@link #applyAll} merges the logs and applies them in one
 * deterministic order — sorted by (source entity handle, per-source emission
 * sequence), never thread-finish order — so the same tick, seed, and entity
 * set produce the same outcome at any shard count. Contested claims resolve
 * first-in-order; losers are reported through
 * {@link Applier#claimRejected} and can react next tick.
 */
public final class EntityEffects {

    private EntityEffects() {}

    /** One world mutation an entity tick requested. */
    public sealed interface Effect
            permits Damage, ClaimItem, SetLoveMode, Remove, Spawn {}

    public record Damage(long target, float amount, long source) implements Effect {}
    /** {@code writeId} is the per-source sequence number returned to the emitter. */
    public record ClaimItem(long item, long claimant, long writeId) implements Effect {}
    public record SetLoveMode(long target, boolean active) implements Effect {}
    public record Remove(long handle) implements Effect {}
    public record Spawn(EntitySpec spec) implements Effect {}

    /** An effect stamped with its deterministic ordering key. */
    public record Op(long sourceHandle, long seq, Effect effect) {}

    /**
     * Loader-supplied application of resolved effects; the engine guarantees
     * order and claim arbitration, the loader binds world mutation. Defaults
     * are no-ops so telemetry-phase code can run without a binding.
     */
    public interface Applier {
        default void damage(long target, float amount, long source) {}
        /** @return false if the world-side condition failed (stack gone, etc.). */
        default boolean claimItem(long item, long claimant) {
            return true;
        }
        default void claimRejected(long writeId, long claimant) {}
        default void setLoveMode(long target, boolean active) {}
        default void removeEntity(long handle) {}
        default void spawnEntity(EntitySpec spec) {}
    }

    /**
     * Per-shard effect recorder. Not thread-safe by design: exactly one
     * shard worker writes to it during the parallel pass, and the
     * coordinator reads it only after the phase barrier.
     */
    public static final class ShardLog {
        private final List<Op> ops = new ArrayList<>();

        /**
         * The {@link EntityEffectLog} view handed to one entity's tick.
         * Ordering is keyed on {@code sourceHandle}, so the same entity
         * emits at the same position in the merged order at any shard count.
         */
        public EntityEffectLog forSource(long sourceHandle) {
            return new EntityEffectLog() {
                private long seq;

                @Override
                public void damage(long targetHandle, float amount, long srcHandle) {
                    ops.add(new Op(sourceHandle, seq++, new Damage(targetHandle, amount, srcHandle)));
                }

                @Override
                public long claimItem(long itemHandle, long claimantHandle) {
                    long writeId = seq;
                    ops.add(new Op(sourceHandle, seq++,
                            new ClaimItem(itemHandle, claimantHandle, writeId)));
                    return writeId;
                }

                @Override
                public void setLoveMode(long targetHandle, boolean active) {
                    ops.add(new Op(sourceHandle, seq++, new SetLoveMode(targetHandle, active)));
                }

                @Override
                public void removeEntity(long handle) {
                    ops.add(new Op(sourceHandle, seq++, new Remove(handle)));
                }

                @Override
                public void spawnEntity(EntitySpec spec) {
                    ops.add(new Op(sourceHandle, seq++, new Spawn(spec)));
                }
            };
        }

        List<Op> ops() {
            return ops;
        }

        public boolean isEmpty() {
            return ops.isEmpty();
        }
    }

    /**
     * Merge one region's shard logs and apply them in deterministic order.
     * Coordinator-thread only, between phase barriers (the live entity list
     * is never touched from inside a parallel shard pass).
     *
     * @return number of ops applied (rejected claims included)
     */
    public static int applyAll(List<ShardLog> logs, Applier applier) {
        List<Op> merged = new ArrayList<>();
        for (ShardLog log : logs) {
            merged.addAll(log.ops());
        }
        merged.sort(Comparator.comparingLong(Op::sourceHandle).thenComparingLong(Op::seq));

        Set<Long> claimedItems = new HashSet<>();
        for (Op op : merged) {
            switch (op.effect()) {
                case Damage d -> applier.damage(d.target(), d.amount(), d.source());
                case ClaimItem c -> {
                    // First claim in deterministic order wins; the world-side
                    // condition can still reject the winner (stack despawned).
                    if (claimedItems.add(c.item()) && applier.claimItem(c.item(), c.claimant())) {
                        continue;
                    }
                    applier.claimRejected(c.writeId(), c.claimant());
                }
                case SetLoveMode l -> applier.setLoveMode(l.target(), l.active());
                case Remove r -> applier.removeEntity(r.handle());
                case Spawn s -> applier.spawnEntity(s.spec());
            }
        }
        return merged.size();
    }
}
