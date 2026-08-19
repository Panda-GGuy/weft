package dev.weft.neoforge.regiontick;

import dev.weft.engine.region.RegionManager;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P2 increment 2 (RFC-0001 §4.2): the real chunk→region mapping, fed by
 * actual chunk load/unload. One {@link RegionManager} per {@link ServerLevel}
 * (chunk keys are per-dimension coordinates, so managers must not be shared
 * across levels); merge happens on load (cheap, local scan), splits are
 * recomputed between ticks, throttled and only when chunks were removed.
 *
 * <p><b>What these managers are.</b> Loader-owned topology, validated by the
 * {@code p2regions} gametest and surfaced in {@code /weft status}. Since
 * increment 4 they decide vanilla-section bucket membership
 * ({@link RegionizedTicking}), and since increment 6 they are the
 * <em>routing authority</em> for owner mail (RFC-0007 §3.1): with
 * {@code ownerMailRouting} active, positionally-owned work is posted to the
 * owning region's own mailbox ({@link OwnerMail}) and drained at the head of
 * that region's bucket run. Mail stranded by a topology mutation (split,
 * emptied region) reroutes to the scheduler's global inbox via the stranded
 * sink wired below. The scheduler's own RegionManager remains only the owner
 * id reservation counter; full engine-pipeline unification is the v2 arc
 * (RFC-0007 §5).
 *
 * <p>Always-on by design: this is pure bookkeeping (a map update per chunk
 * load/unload — the nightly {@code loadgen_fresh_chunk_load} trend guards the
 * steady-load cost, and {@code RegionChurnStormBench} plus
 * {@code RegionChurnTest} guard sustained load/unload churn, the pregen shape
 * that regressed Chunky by ~20 cps before splits were scoped to removals
 * that can actually disconnect), and gating it behind {@code regionizedTicking}
 * would leave the mapping blind to chunks loaded while the flag was off. Tick
 * <em>ownership</em> stays gated by the module flag ({@link RegionizedTicking}).
 *
 * <p>Thread discipline: all mutations happen on the server thread — chunk
 * events that arrive off-thread are posted through the scheduler's inbox and
 * applied at the next INGEST (which runs on the server thread). Reads
 * (status, gametests) are server-thread too.
 */
public final class RegionTopology {

    private RegionTopology() {}

    /**
     * WS-7's view of topology mutation (RFC-0009 §5). One observer, attached only
     * while the observability module is active.
     */
    public interface TopologyObserver {

        /**
         * @param merge      true for a merge, false for a split
         * @param sourceId   the surviving region on a merge; the region that shed
         *                   components on a split
         * @param otherIds   absorbed regions on a merge; new regions on a split
         * @param chunksAfter chunks in {@code sourceId} once the change landed
         */
        void onChange(String levelId, boolean merge, long sourceId, long[] otherIds,
                      int chunksAfter);
    }

    private static volatile TopologyObserver observer;

    /** Attach WS-7's observer, or {@code null} to detach (RFC-0003 R6). */
    public static void setTopologyObserver(TopologyObserver value) {
        observer = value;
    }

    private static final ConcurrentHashMap<ServerLevel, RegionManager> managers =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, AtomicBoolean> splitDirty =
            new ConcurrentHashMap<>();

    /**
     * Recompute splits at most every N ticks per level. The recompute only
     * BFS-walks regions a removal may actually have disconnected (the manager
     * proves most removals can't split anything), so under pregen churn this
     * is a no-op, not a whole-map walk.
     */
    private static final int SPLIT_RECOMPUTE_INTERVAL_TICKS = 20;

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            ChunkPos pos = chunk.getPos();
            onServerThread(level, () -> managerFor(level).addChunk(pos.x, pos.z));
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getChunk() instanceof LevelChunk chunk) {
            ChunkPos pos = chunk.getPos();
            onServerThread(level, () -> {
                RegionManager manager = managers.get(level);
                if (manager != null) {
                    manager.removeChunk(pos.x, pos.z);
                    dirtyFlag(level).set(true);
                }
            });
        }
    }

    /** Split maintenance runs between region work, never mid-tick (RFC §4.2). */
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % SPLIT_RECOMPUTE_INTERVAL_TICKS != 0) {
            return;
        }
        RegionManager manager = managers.get(level);
        if (manager != null && dirtyFlag(level).compareAndSet(true, false)) {
            manager.recomputeSplits();
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            managers.remove(level);
            splitDirty.remove(level);
        }
    }

    /** Server stop: level instances die with the server. */
    public static void reset() {
        managers.clear();
        splitDirty.clear();
    }

    /** The live mapping for a level; creates it on first use (server thread). */
    public static RegionManager managerFor(ServerLevel level) {
        return managers.computeIfAbsent(level, l -> {
            RegionManager manager = new RegionManager(WeftConfig.MERGE_DISTANCE, l.getSeed());
            // RFC-0007 §3.3: mail stranded by a split or an emptied region is
            // rerouted to the global inbox — always-safe delivery at the next
            // INGEST, one tick late, rare. Scheduler gone (server stopping):
            // dropped, its target state is being torn down with it (same
            // contract as WeftMod.postToOwner).
            // WS-7 (RFC-0009 §5): merges and splits become events, with the
            // level label RegionManager has no way to know. Installed
            // unconditionally and cheap — the listener returns immediately when
            // no observer is attached, so R6 holds without re-walking managers
            // every time the module flips.
            String levelId = l.dimension().location().toString();
            manager.setTopologyListener(new dev.weft.engine.region.RegionManager.TopologyListener() {
                @Override
                public void onMerge(long resultId, long[] absorbedIds, int chunksAfter) {
                    TopologyObserver o = observer;
                    if (o != null) {
                        o.onChange(levelId, true, resultId, absorbedIds, chunksAfter);
                    }
                }

                @Override
                public void onSplit(long sourceId, long[] resultIds, int chunksAfter) {
                    TopologyObserver o = observer;
                    if (o != null) {
                        o.onChange(levelId, false, sourceId, resultIds, chunksAfter);
                    }
                }
            });
            manager.setStrandedMailSink(m -> {
                dev.weft.engine.sched.WeftScheduler s = WeftMod.schedulerOrNull();
                if (s != null) {
                    s.submit(m);
                }
            });
            return manager;
        });
    }

    /** Visit every region of every live level (server thread; flush path). */
    static void forEachRegion(java.util.function.Consumer<dev.weft.engine.region.Region> visitor) {
        for (RegionManager manager : managers.values()) {
            // Copy: a visitor that mutates topology must not blow the iterator.
            for (dev.weft.engine.region.Region region : java.util.List.copyOf(manager.all())) {
                visitor.accept(region);
            }
        }
    }

    /** One-line topology summary for {@code /weft status} (R5). */
    public static String summary() {
        if (managers.isEmpty()) {
            return "no chunks tracked yet";
        }
        int levels = 0;
        int regions = 0;
        int chunks = 0;
        int largest = 0;
        for (Map.Entry<ServerLevel, RegionManager> e : managers.entrySet()) {
            levels++;
            regions += e.getValue().all().size();
            chunks += e.getValue().chunkCount();
            largest = Math.max(largest, e.getValue().all().stream()
                    .map(r -> r.chunks().size())
                    .max(Comparator.naturalOrder()).orElse(0));
        }
        return String.format("topology: %d regions / %d chunks across %d levels, largest %d chunks",
                regions, chunks, levels, largest);
    }

    private static void onServerThread(ServerLevel level, Runnable task) {
        if (level.getServer().isSameThread()) {
            task.run();
        } else {
            WeftMod.postToOwner(task);
        }
    }

    private static AtomicBoolean dirtyFlag(ServerLevel level) {
        return splitDirty.computeIfAbsent(level, l -> new AtomicBoolean());
    }
}
