package dev.weft.neoforge.regiontick;

import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.entity.EntityTickList;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * P2 loader-side glue (RFC-0001 §11): routes vanilla's entity and
 * block-entity tick sections through the engine when {@code regionizedTicking}
 * is active.
 *
 * <p><b>Increment 1 — whole-level serial ownership.</b> Each
 * {@link ServerLevel} is one engine-owned region (an id reserved from the
 * scheduler's {@code RegionManager}, deliberately never entered into a chunk
 * map); its sections run through {@link WeftScheduler#runOwnedSerial} — same
 * thread, same vanilla iteration order, bit-identical by construction.
 *
 * <p><b>Increment 4 — partitioned ticking (still serial).</b> With
 * {@code partitionedTicking} also on, each section is instead grouped by
 * {@link RegionTopology}'s <em>real</em> regions and executed
 * bucket-by-bucket in canonical (ascending region id) order, each bucket
 * under a REGION thread context carrying its real region id — the execution
 * shape of parallel regions with the concurrency removed. Vanilla order is
 * preserved <em>within</em> each region; only cross-region interleaving
 * changes, which is unobservable for regions kept ≥ mergeDistance apart
 * (RFC-0001 §4.2) — the parity suite holds this at E0 on its single-region
 * arena, and the {@code p2partition} gametest holds two independent islands
 * to control-equal end states. Collection preserves vanilla's own semantics:
 * {@code EntityTickList.forEach} freezes the iterated map (mid-tick spawns
 * don't tick until next tick), the per-entity consumer re-checks
 * removal/despawn/range at execution, and removed BE tickers are null-ticker
 * no-ops. Units in chunks the topology doesn't map (should be none: ticking
 * chunks are a subset of loaded chunks) run in a counted tail under the
 * level's owner id.
 *
 * <p>What increment 4 deliberately does <em>not</em> do: run buckets on
 * workers. True parallelism (class E1) needs the shared-structure audit —
 * entity-section storage mutation, cross-region teleports, {@code
 * level.random} draws, packet sends — and owner-mail rerouting; serial
 * partitioning has none of those hazards by construction and exists so the
 * partition seam, the real-id contexts, and the canonical order are proven
 * before threads arrive.
 *
 * <p>The {@code active} flag is owned by the coexistence resolution
 * ({@code WeftModules}). R6: inactive means the wrapped call sites invoke the
 * vanilla section directly — zero behavioral residue.
 */
public final class RegionizedTicking {

    private RegionizedTicking() {}

    private static volatile boolean active;
    private static volatile boolean partitioned;

    /** One reserved engine owner id per live ServerLevel (increment 1's "one region"). */
    private static final ConcurrentHashMap<ServerLevel, Long> ownerIds = new ConcurrentHashMap<>();

    private static final LongAdder entitySections = new LongAdder();
    private static final LongAdder blockEntitySections = new LongAdder();
    private static final LongAdder partitionedSections = new LongAdder();
    private static final LongAdder unmappedUnits = new LongAdder();

    /** Region ids of the most recent partitioned sections (gametest probes). */
    private static volatile long[] lastEntityPartition = new long[0];
    private static volatile long[] lastBlockEntityPartition = new long[0];

    // Block-entity capture state. Server-thread only: sections run on the
    // server thread and levels tick sequentially, so one static slot is
    // enough; non-null only while a partitioned BE section is collecting.
    private static TreeMap<Long, List<Runnable>> beBuckets;
    private static List<Runnable> beTail;
    private static ServerLevel beLevel;

    /** RFC-0003 R2 applied-check (belt-and-braces: these mixins are fail-loud). */
    public static boolean hooksApplied() {
        return RegionizedEntityTickMarker.class.isAssignableFrom(ServerLevel.class)
                && RegionizedBlockEntityTickMarker.class.isAssignableFrom(Level.class);
    }

    /** Wired as the regionized_ticking module's applyActive (WeftModules). */
    public static void applyActive(boolean value) {
        active = value;
        partitioned = value && WeftConfig.PARTITIONED_TICKING;
    }

    /** Direct switch for tests (parity/partition gametests drive runs). */
    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            partitioned = false;
        }
    }

    /** Direct switch for tests; production resolution goes via applyActive. */
    public static void setPartitioned(boolean value) {
        partitioned = value && active;
    }

    public static boolean isActive() {
        return active;
    }

    /** Server stop: the level instances die with the server; drop their ids. */
    public static void reset() {
        ownerIds.clear();
        lastEntityPartition = new long[0];
        lastBlockEntityPartition = new long[0];
    }

    /**
     * Called (server thread) from the wrapped {@code entityTickList.forEach}
     * call site inside {@code ServerLevel.tick}. {@code original} is invoked
     * exactly once, synchronously, within this call (MixinExtras contract).
     * Inactive or engine absent: vanilla runs untouched.
     */
    public static void tickEntitySection(ServerLevel level, EntityTickList list,
                                         Consumer<Entity> ticker,
                                         BiConsumer<EntityTickList, Consumer<Entity>> original) {
        WeftScheduler engine = active ? WeftMod.schedulerOrNull() : null;
        if (engine == null) {
            original.accept(list, ticker);
            return;
        }
        entitySections.increment();
        if (!partitioned) {
            engine.runOwnedSerial(ownerId(level), () -> original.accept(list, ticker));
            return;
        }

        // Increment 4: collect in vanilla's own iteration order (the deferred
        // call below is vanilla's own consumer object, never the Operation),
        // then run buckets ascending by real region id.
        RegionManager topology = RegionTopology.managerFor(level);
        TreeMap<Long, List<Entity>> buckets = new TreeMap<>();
        List<Entity> tail = new ArrayList<>();
        engine.runOwnedSerial(ownerId(level), () -> original.accept(list, entity -> {
            ChunkPos chunk = entity.chunkPosition();
            Region region = topology.regionAt(chunk.x, chunk.z);
            if (region == null) {
                tail.add(entity);
            } else {
                buckets.computeIfAbsent(region.id(), k -> new ArrayList<>()).add(entity);
            }
        }));

        long[] partition = new long[buckets.size()];
        int i = 0;
        for (var bucket : buckets.entrySet()) {
            partition[i++] = bucket.getKey();
            engine.runOwnedSerial(bucket.getKey(), () -> {
                for (Entity entity : bucket.getValue()) {
                    ticker.accept(entity);
                }
            });
        }
        if (!tail.isEmpty()) {
            unmappedUnits.add(tail.size());
            engine.runOwnedSerial(ownerId(level), () -> {
                for (Entity entity : tail) {
                    ticker.accept(entity);
                }
            });
        }
        partitionedSections.increment();
        lastEntityPartition = partition;
    }

    /**
     * Called (server thread) from the wrapped {@code Level.tickBlockEntities}
     * body when the level is a ServerLevel. In partitioned mode the vanilla
     * loop runs as a collection pass — {@link #captureBlockEntityUnit}
     * buffers each unit — and the buckets execute afterwards, canonical
     * order, real region ids.
     */
    public static void tickBlockEntitySectionOwned(ServerLevel level, Runnable vanillaSection) {
        WeftScheduler engine = active ? WeftMod.schedulerOrNull() : null;
        if (engine == null) {
            vanillaSection.run();
            return;
        }
        blockEntitySections.increment();
        if (!partitioned) {
            engine.runOwnedSerial(ownerId(level), vanillaSection);
            return;
        }

        TreeMap<Long, List<Runnable>> buckets = new TreeMap<>();
        List<Runnable> tail = new ArrayList<>();
        beBuckets = buckets;
        beTail = tail;
        beLevel = level;
        try {
            engine.runOwnedSerial(ownerId(level), vanillaSection);
        } finally {
            beBuckets = null;
            beTail = null;
            beLevel = null;
        }

        long[] partition = new long[buckets.size()];
        int i = 0;
        for (var bucket : buckets.entrySet()) {
            partition[i++] = bucket.getKey();
            engine.runOwnedSerial(bucket.getKey(), () -> bucket.getValue().forEach(Runnable::run));
        }
        if (!tail.isEmpty()) {
            unmappedUnits.add(tail.size());
            engine.runOwnedSerial(ownerId(level), () -> tail.forEach(Runnable::run));
        }
        partitionedSections.increment();
        lastBlockEntityPartition = partition;
    }

    /**
     * Per-ticker seam (the {@code TickingBlockEntity.tick()} call-site mixin).
     * Returns true when the unit was captured into a partition bucket — the
     * caller must then NOT run it inline. {@code unit} must not close over a
     * MixinExtras Operation (it executes after the handler frame returns);
     * the mixin passes the ticker's own {@code tick()} through the lane check.
     */
    public static boolean captureBlockEntityUnit(ServerLevel level, TickingBlockEntity ticker,
                                                 Runnable unit) {
        TreeMap<Long, List<Runnable>> buckets = beBuckets;
        if (buckets == null || level != beLevel) {
            return false;
        }
        BlockPos pos = ticker.getPos();
        Region region = RegionTopology.managerFor(level).regionAtBlock(pos.getX(), pos.getZ());
        if (region == null) {
            beTail.add(unit);
        } else {
            buckets.computeIfAbsent(region.id(), k -> new ArrayList<>()).add(unit);
        }
        return true;
    }

    /** Engine-owned entity sections since boot (parity-suite engagement check). */
    public static long entitySections() {
        return entitySections.sum();
    }

    /** Engine-owned block-entity sections since boot. */
    public static long blockEntitySections() {
        return blockEntitySections.sum();
    }

    /** Sections executed via per-region buckets since boot (increment 4). */
    public static long partitionedSections() {
        return partitionedSections.sum();
    }

    /** Units whose chunk had no topology region (should stay 0). */
    public static long unmappedUnits() {
        return unmappedUnits.sum();
    }

    /** Region ids of the most recent partitioned entity section (probe). */
    public static long[] lastEntityPartition() {
        return lastEntityPartition.clone();
    }

    /** Region ids of the most recent partitioned block-entity section (probe). */
    public static long[] lastBlockEntityPartition() {
        return lastBlockEntityPartition.clone();
    }

    /** Extra detail for the posture report / {@code /weft status} (R5). */
    public static String statusDetail() {
        long e = entitySections.sum();
        long b = blockEntitySections.sum();
        String sections = e == 0 && b == 0
                ? "no sections owned yet"
                : String.format("%d entity + %d block-entity sections owned", e, b);
        String mode = partitioned
                ? String.format("increment 4 partitioned ticking (per-region buckets, serial, "
                        + "canonical order; %d partitioned sections, %d unmapped units, "
                        + "last partition %d regions)",
                        partitionedSections.sum(), unmappedUnits.sum(), lastEntityPartition.length)
                : "increment 1 ticking (whole level, serial, server thread)";
        return mode + ": " + sections + "; " + RegionTopology.summary();
    }

    private static long ownerId(ServerLevel level) {
        return ownerIds.computeIfAbsent(level, l -> WeftMod.reserveRegionOwnerId());
    }
}
