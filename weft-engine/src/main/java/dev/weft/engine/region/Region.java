package dev.weft.engine.region;

import dev.weft.engine.mail.Mailbox;
import dev.weft.engine.mail.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

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
    }

    private final long id;
    private final Set<Long> chunks = new HashSet<>();
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
        long origin = chunks.stream().min(Long::compare).orElse(0L);
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
