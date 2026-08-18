package dev.weft.engine.region;

import dev.weft.engine.mail.Mailbox;
import dev.weft.engine.mail.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;

/**
 * One unit of parallel world simulation: a connected set of loaded chunks and
 * everything inside them. Only the worker currently ticking this region may
 * touch its state (enforced by WeftGuards).
 */
public final class Region {

    /** Loader-supplied per-region simulation work (entities, BEs, fluids...). */
    @FunctionalInterface
    public interface Tickable {
        void tick(Region region, long tickNumber);

        /**
         * Sharded-path entry point (RFC-0004): same contract as
         * {@link #tick(Region, long)} plus the shard's RNG substream and
         * effect log. Tickables that never interact cross-entity can ignore
         * it — the default delegates to the serial signature.
         */
        default void tick(Region region, long tickNumber, dev.weft.engine.shard.ShardContext shard) {
            tick(region, tickNumber);
        }
    }

    private final long id;
    // Sorted so reseed's origin (the minimum chunk key) is O(log n) rather
    // than a full scan: reseed runs on every chunk load/unload, and an O(n)
    // min turns sustained churn (pregen) quadratic in the loaded-chunk count.
    private final NavigableSet<Long> chunks = new TreeSet<>();
    private final Mailbox<Message> mailbox = new Mailbox<>();
    private final List<Tickable> tickables = new ArrayList<>();
    private final long worldSeed;
    private SplittableRandom random;

    Region(long id, long worldSeed) {
        this.id = id;
        this.worldSeed = worldSeed;
        reseed();
    }

    /**
     * Deterministic per-region RNG (RFC-0001 §6.6): derived from world seed
     * and the region's minimum chunk key, so a region rebuilt from the same
     * chunks after merge/split resumes an identical stream shape.
     */
    void reseed() {
        long origin = chunks.isEmpty() ? 0L : chunks.first();
        this.random = new SplittableRandom(worldSeed ^ (origin * 0x9E3779B97F4A7C15L));
    }

    public long id() {
        return id;
    }

    public Set<Long> chunks() {
        return chunks;
    }

    public Mailbox<Message> mailbox() {
        return mailbox;
    }

    public SplittableRandom random() {
        return random;
    }

    public void addTickable(Tickable t) {
        tickables.add(t);
    }

    List<Tickable> tickables() {
        return tickables;
    }

    /** Read-only view for the scheduler; mutation goes through addTickable. */
    public List<Tickable> tickablesView() {
        return java.util.Collections.unmodifiableList(tickables);
    }
}
