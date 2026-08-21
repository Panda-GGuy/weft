package dev.weft.neoforge.regiontick;

import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.region.PendingUnits;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import dev.weft.neoforge.legacy.LegacyRouting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.EntityTickList;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * P2 loader-side glue (RFC-0001 Ã‚Â§11): routes vanilla's entity and
 * block-entity tick sections through the engine when {@code regionizedTicking}
 * is active.
 *
 * <p><b>Increment 1 Ã¢â‚¬â€ whole-level serial ownership.</b> Each
 * {@link ServerLevel} is one engine-owned region (an id reserved from the
 * scheduler's {@code RegionManager}, deliberately never entered into a chunk
 * map); its sections run through {@link WeftScheduler#runOwnedSerial} Ã¢â‚¬â€ same
 * thread, same vanilla iteration order, bit-identical by construction.
 *
 * <p><b>Increment 4 Ã¢â‚¬â€ partitioned ticking (still serial).</b> With
 * {@code partitionedTicking} also on, each section is instead grouped by
 * {@link RegionTopology}'s <em>real</em> regions and executed
 * bucket-by-bucket in canonical (ascending region id) order, each bucket
 * under a REGION thread context carrying its real region id Ã¢â‚¬â€ the execution
 * shape of parallel regions with the concurrency removed. Vanilla order is
 * preserved <em>within</em> each region; only cross-region interleaving
 * changes, which is unobservable for regions kept Ã¢â€°Â¥ mergeDistance apart
 * (RFC-0001 Ã‚Â§4.2) Ã¢â‚¬â€ the parity suite holds this at E0 on its single-region
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
 * workers. True parallelism (class E1) needs the shared-structure audit Ã¢â‚¬â€
 * entity-section storage mutation, cross-region teleports, {@code
 * level.random} draws, packet sends Ã¢â‚¬â€ and owner-mail rerouting; serial
 * partitioning has none of those hazards by construction and exists so the
 * partition seam, the real-id contexts, and the canonical order are proven
 * before threads arrive.
 *
 * <p>The {@code active} flag is owned by the coexistence resolution
 * ({@code WeftModules}). R6: inactive means the wrapped call sites invoke the
 * vanilla section directly Ã¢â‚¬â€ zero behavioral residue.
 */
public final class RegionizedTicking {

    private RegionizedTicking() {}

    private static volatile boolean active;
    private static volatile boolean partitioned;
    private static volatile boolean parallel;
    private static volatile boolean mailRouted;
    private static volatile boolean singleJoin;
    private static volatile boolean sharded;

    /** One reserved engine owner id per live ServerLevel (increment 1's "one region"). */
    private static final ConcurrentHashMap<ServerLevel, Long> ownerIds = new ConcurrentHashMap<>();

    private static final LongAdder entitySections = new LongAdder();
    private static final LongAdder blockEntitySections = new LongAdder();
    private static final LongAdder partitionedSections = new LongAdder();
    private static final LongAdder unmappedUnits = new LongAdder();
    /** Navigation updates deferred by hazard 21, used by its non-vacuous gate. */
    private static final LongAdder deferredNavigationUpdates = new LongAdder();
    /** Hazard-21 callbacks observed after the barrier on the server thread. */
    private static final LongAdder completedNavigationUpdates = new LongAdder();
    /** Fail-loud counter: a deferred navigation callback must never run elsewhere. */
    private static final LongAdder misplacedNavigationUpdates = new LongAdder();
    /**
     * Entities sent to the serial tail specifically by hazard 25's
     * memory-reach classification.
     *
     * <p>Deliberately separate from {@link #unreadyUnits}, which is incremented
     * by three different causes Ã¢â‚¬â€ memory-reach entities, entities whose read
     * neighbourhood is not live, and block entities in the same situation. A
     * gate that wants to prove "the villagers took the serial tail" cannot read
     * the conflated counter: on any arena with border chunks the neighbourhood
     * cause alone can satisfy a {@code >= villagerCount} threshold with zero
     * villagers classified, which is a vacuous pass. Same lesson the
     * unmapped/unready split already learned one counter earlier.
     */
    private static final LongAdder memoryReachUnits = new LongAdder();
    private static final LongAdder fusedTicks = new LongAdder();
    private static final LongAdder fusedRegions = new LongAdder();
    /** Fused ticks that stood down from fan-out (fallback path engagement). */
    private static final LongAdder fusedSerialFallbacks = new LongAdder();

    /** Region ids of the most recent partitioned sections (gametest probes). */
    private static volatile long[] lastEntityPartition = new long[0];
    private static volatile long[] lastBlockEntityPartition = new long[0];
    /**
     * Ticking block entities the most recent block-entity section captured.
     *
     * <p>Exists because the WS-7 exporter had no honest source for this and used
     * a dishonest one: it summed {@link #lastBlockEntityPartition()}, which holds
     * <em>region ids</em>, and published the total as a block-entity count. On a
     * three-region world that reported "6" Ã¢â‚¬â€ ids 1+2+3 Ã¢â‚¬â€ while the level was
     * ticking thousands. The loop variable was even named {@code units}, which is
     * how a type-correct {@code long[]} carried the wrong meaning past review.
     */
    private static volatile int lastBlockEntityUnits;

    /** Thread names per bucket of the most recent entity section (E1 probe). */
    private static volatile String[] lastEntityPartitionThreads = new String[0];
    /** Same, for the block-entity section Ã¢â‚¬â€ the only probe a BE-only rig has. */
    private static volatile String[] lastBlockEntityPartitionThreads = new String[0];

    /**
     * Work deferred by a region worker to the end of the current section
     * (RFC-0006 hazard 14: mid-tick dimension changes). Drained on the
     * server thread right after the barrier, same tick.
     */
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> sectionEndTasks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Entity work captured at ServerLevel's entity call site, consumed by its following BE call. */
    private static final ConcurrentHashMap<ServerLevel, FusedFrame> fusedFrames =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ServerLevel, FusedLevelUnits> fusedUnits =
            new ConcurrentHashMap<>();
    private enum FusedPhase { NONE, CAPTURE, ENTITY, FRESH, TICK }
    private static final ThreadLocal<FusedPhase> fusedPhase =
            ThreadLocal.withInitial(() -> FusedPhase.NONE);
    private static final ThreadLocal<FusedRegionUnits> fusedCurrentUnits = new ThreadLocal<>();
    private static final ThreadLocal<Long> fusedCurrentRegion = new ThreadLocal<>();
    // Server-thread-only slots during the wrapped vanilla BE collection pass.
    private static FusedLevelUnits fusedCaptureUnits;
    private static java.util.HashMap<Long, Region> fusedCaptureRegions;
    private static ServerLevel fusedCaptureLevel;
    private static FusedFrame fusedCaptureFrame;

    private static final class FusedFrame {
        final TreeMap<Long, List<Entity>> entities = new TreeMap<>();
        final java.util.HashMap<Long, Region> regions = new java.util.HashMap<>();
        final Consumer<Entity> ticker;
        boolean forceSerial;

        FusedFrame(Consumer<Entity> ticker) {
            this.ticker = ticker;
        }
    }

    private static final class FusedRegionUnits {
        final PendingUnits<BeTickUnit> tickers = new PendingUnits<>();
        final PendingUnits<BlockEntity> fresh = new PendingUnits<>();
        final List<BeTickUnit> afterFreshTickers = new ArrayList<>();
    }

    private static final class FusedLevelUnits {
        final TreeMap<Long, FusedRegionUnits> regions = new TreeMap<>();
        final java.util.IdentityHashMap<TickingBlockEntity, Boolean> knownTickers =
                new java.util.IdentityHashMap<>();

        synchronized FusedRegionUnits region(long regionId) {
            return regions.computeIfAbsent(regionId, ignored -> new FusedRegionUnits());
        }

        synchronized void addTicker(long regionId, BeTickUnit unit) {
            if (knownTickers.put(unit.ticker(), Boolean.TRUE) == null) {
                region(regionId).tickers.add(unit);
            }
        }

        synchronized void addTickerPending(long regionId, BeTickUnit unit,
                                           boolean afterFresh) {
            if (knownTickers.put(unit.ticker(), Boolean.TRUE) != null) {
                return;
            }
            if (afterFresh) {
                region(regionId).afterFreshTickers.add(unit);
            } else {
                region(regionId).tickers.add(unit);
            }
        }

        synchronized void forget(TickingBlockEntity ticker) {
            knownTickers.remove(ticker);
        }

        synchronized List<java.util.Map.Entry<Long, FusedRegionUnits>> snapshot() {
            return new ArrayList<>(regions.entrySet());
        }
    }

    // Block-entity capture state. Server-thread only: sections run on the
    // server thread and levels tick sequentially, so one static slot is
    // enough; non-null only while a partitioned BE section is collecting.
    private static TreeMap<Long, List<BeTickUnit>> beBuckets;
    private static java.util.HashMap<Long, Region> beBucketRegions;
    private static List<Runnable> beTail;
    private static ServerLevel beLevel;
    /** Truly-unmapped units captured this section (see the entity-section note). */
    private static int beUnmapped;

    /**
     * Per-section cache for {@link #readNeighbourhoodLive}. Server-thread only,
     * same discipline as the block-entity capture slots above: sections run on
     * the server thread and levels tick sequentially, so one slot is enough.
     */
    private static java.util.HashMap<Long, Boolean> readyCache;

    /** Units sent to the serial tail because their read neighbourhood was not live. */
    private static final LongAdder unreadyUnits = new LongAdder();

    /**
     * The BLOCK-ENTITY share of {@link #unreadyUnits} — hazard 24's own shape.
     *
     * <p>Split out for the same reason {@link #memoryReachUnits} was: the total
     * is incremented from three sites (memory-reach entities, entities whose
     * read neighbourhood is not live, and block entities in that state), so it
     * can only ever prove the union. Hazard 24 is specifically <em>a block
     * entity on a chunk boundary reading into an evicted neighbour</em>, and a
     * gate for it must not be satisfiable by an entity deferral in the same
     * tick — least of all in a rig that deliberately evicts chunks, where
     * entity deferrals are abundant.
     */
    private static final LongAdder unreadyBlockEntityUnits = new LongAdder();

    /**
     * RFC-0006 <b>hazard 24</b>: may a worker be handed a unit in this chunk?
     *
     * <p>Answers "is this chunk's radius-1 read neighbourhood <em>presently</em>
     * live", and it has to be asked because vanilla's own guarantee is weaker
     * than it looks. {@code ChunkMap.prepareEntityTickingChunk} promises radius-2
     * at {@code ChunkStatus.FULL} <em>at the moment of promotion</em>; it does not
     * promise the neighbours stay resident. When one is evicted Ã¢â‚¬â€ a teleport
     * releasing a ticket, a pre-generator's sweep moving on Ã¢â‚¬â€ vanilla carries on
     * regardless, because {@code getChunk(load=true)} simply loads it again. A
     * region worker has no such option: hazard 1 forbids it from driving the
     * chunk system, so the same lazy load is a hard failure.
     *
     * <p>That is what crashed a live world on a teleport. A vault sitting on the
     * westernmost block of its chunk called {@code setChanged} Ã¢â€ â€™
     * {@code updateNeighbourForOutputSignal}, read one block west into the
     * adjacent chunk, and that chunk was gone. Hazard 22's border fallback could
     * not help: there was no generated view either.
     *
     * <p>Radius 1, not 2, because that is what the failure actually reaches Ã¢â‚¬â€ a
     * block entity's neighbour-signal path is one block. Entities can reach
     * further, and the entity section uses this too; that is deliberately
     * conservative rather than complete, and the guard still fails loud for
     * anything beyond it, which is how the next gap will announce itself rather
     * than corrupt something quietly.
     *
     * <p>Probed with {@code getChunkNow}, which on the server thread is vanilla's
     * own method Ã¢â‚¬â€ a visible-map lookup, no load triggered, no promotion
     * required. Cached per chunk per section, so the cost is nine lookups per
     * <em>chunk</em> rather than per unit: a few hundred microseconds on a
     * chunk-dense world, against a crash.
     */
    private static boolean readNeighbourhoodLive(ServerLevel level, int cx, int cz) {
        java.util.HashMap<Long, Boolean> cache = readyCache;
        long key = ChunkPos.asLong(cx, cz);
        if (cache != null) {
            Boolean hit = cache.get(key);
            if (hit != null) {
                return hit;
            }
        }
        boolean live = true;
        var source = level.getChunkSource();
        outer:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (source.getChunkNow(cx + dx, cz + dz) == null) {
                    live = false;
                    break outer;
                }
            }
        }
        if (cache != null) {
            cache.put(key, live);
        }
        return live;
    }

    /** RFC-0003 R2 applied-check (belt-and-braces: these mixins are fail-loud). */
    public static boolean hooksApplied() {
        return RegionizedEntityTickMarker.class.isAssignableFrom(ServerLevel.class)
                && RegionizedBlockEntityTickMarker.class.isAssignableFrom(Level.class);
    }

    /** Wired as the regionized_ticking module's applyActive (WeftModules). */
    public static void applyActive(boolean value) {
        active = value;
        partitioned = value && WeftConfig.PARTITIONED_TICKING;
        parallel = partitioned && WeftConfig.PARALLEL_REGIONS;
        sharded = partitioned && WeftConfig.BLOCK_ENTITY_SHARDING;
        updateMailRouted(partitioned && WeftConfig.OWNER_MAIL_ROUTING);
        // Increment 7 (RFC-0007 sec. 4): a fused task's first stage is its
        // region's mail drain, so fusion without routing has nothing correct
        // to fuse - the flag resolves against BOTH ancestors, the way
        // parallel resolves against partitioned.
        updateSingleJoin(mailRouted && WeftConfig.SINGLE_JOIN_TICK);
    }

    /** Direct switch for tests (parity/partition gametests drive runs). */
    public static void setActive(boolean value) {
        active = value;
        if (!value) {
            partitioned = false;
            parallel = false;
            sharded = false;
            updateSingleJoin(false);
            updateMailRouted(false);
        }
    }

    /** Direct switch for tests; production resolution goes via applyActive. */
    public static void setPartitioned(boolean value) {
        partitioned = value && active;
        if (!partitioned) {
            parallel = false;
            sharded = false;
            updateSingleJoin(false);
            updateMailRouted(false);
        }
    }

    /**
     * Direct switch for tests; production resolution goes via applyActive.
     * Block-entity sharding (RFC-0008) is independent of {@code parallel}:
     * the colouring fans out inside a single region's bucket, which is
     * exactly the solo-play case where region parallelism does nothing.
     */
    public static void setBlockEntitySharding(boolean value) {
        sharded = value && partitioned;
    }

    /** Direct switch for tests; production resolution goes via applyActive. */
    public static void setParallel(boolean value) {
        parallel = value && partitioned;
    }

    /** Direct switch for tests; production resolution goes via applyActive. */
    public static void setMailRouting(boolean value) {
        updateMailRouted(value && partitioned);
        if (!mailRouted) {
            updateSingleJoin(false);
        }
    }

    /**
     * Direct switch for tests; production resolution goes via applyActive.
     * Increment 7 scaffolding: arming the seam requires the whole ancestor
     * chain (active, partitioned, mail-routed) - see applyActive.
     */
    public static void setSingleJoin(boolean value) {
        updateSingleJoin(value && mailRouted);
    }

    /**
     * Every transition to OFF flushes queued region mail inline (server
     * thread) so nothing is stranded behind a flag no bucket will drain
     * again (RFC-0007 Ã‚Â§3.3 hazard 5). Flag flips happen on the server thread
     * (config resolution, gametests).
     */
    private static void updateMailRouted(boolean value) {
        boolean was = mailRouted;
        mailRouted = value;
        if (was && !value) {
            OwnerMail.flushAllInline();
        }
    }

    public static boolean isActive() {
        return active;
    }

    /** Whether owner mail routes to region mailboxes (increment 6, RFC-0007 Ã‚Â§3). */
    public static boolean isMailRouted() {
        return mailRouted;
    }

    /** Whether the single-join fused tick seam is armed (increment 7, RFC-0007 sec. 4). */
    public static boolean isSingleJoin() {
        return singleJoin;
    }

    /** Server stop: the level instances die with the server; drop their ids. */
    public static void reset() {
        // No flush: queued region mail targets state that is being torn down
        // with the server, exactly like the global inbox dying with the
        // scheduler (WeftMod.postToOwner's documented drop contract).
        mailRouted = false;
        singleJoin = false;
        sharded = false;
        lastBlockEntityUnits = 0;
        sectionProbe = null;
        readyCache = null;
        unreadyUnits.reset();
        memoryReachUnits.reset();
        unreadyBlockEntityUnits.reset();
        ParallelAccess.resetBorderReads();
        BlockEntityShards.reset();
        ownerIds.clear();
        lastEntityPartition = new long[0];
        lastBlockEntityPartition = new long[0];
        lastEntityPartitionThreads = new String[0];
        lastBlockEntityPartitionThreads = new String[0];
        sectionEndTasks.clear();
        deferredNavigationUpdates.reset();
        completedNavigationUpdates.reset();
        misplacedNavigationUpdates.reset();
        fusedFrames.clear();
        fusedUnits.clear();
        fusedTicks.reset();
        fusedSerialFallbacks.reset();
        fusedRegions.reset();
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

        if (singleJoin) {
            captureFusedEntitySection(level, list, ticker, original, engine);
            return;
        }

        // Increment 4: collect in vanilla's own iteration order (the deferred
        // call below is vanilla's own consumer object, never the Operation),
        // then run buckets ascending by real region id.
        RegionManager topology = RegionTopology.managerFor(level);
        TreeMap<Long, List<Entity>> buckets = new TreeMap<>();
        java.util.HashMap<Long, Region> bucketRegions = new java.util.HashMap<>();
        List<Entity> tail = new ArrayList<>();
        // Hazard 24: only worth probing when work can actually reach a worker.
        // Serial buckets already run on the server thread, where a lazy load is
        // legal, so the gate would cost lookups to change nothing.
        boolean gateReads = parallel;
        readyCache = gateReads ? new java.util.HashMap<>() : null;
        // Counted at classification, NOT as tail.size(): the tail now holds two
        // very different populations and conflating them cost a working signal.
        // "unmapped units" is an invariant that must stay 0 (a ticking chunk with
        // no topology region is a bug); hazard-24 deferrals are expected to be
        // non-zero whenever chunks churn. Billing both to unmappedUnits reported
        // 14,647 "unmapped" units in an eviction soak and would have made the
        // partition gate's `unmapped != 0` check meaningless in production.
        int[] unmapped = new int[1];
        engine.runOwnedSerial(ownerId(level), () -> original.accept(list, entity -> {
            ChunkPos chunk = entity.chunkPosition();
            Region region = topology.regionAt(chunk.x, chunk.z);
            if (region == null) {
                unmapped[0]++;
                tail.add(entity);
            } else if (gateReads && MemoryReachEntities.isMemoryReach(entity)) {
                // Hazard 25: Brain AI reads REMEMBERED positions (bed, job site,
                // hive) at arbitrary range, so no neighbourhood radius bounds it.
                unreadyUnits.increment();
                // Also counted on its own: see memoryReachUnits' note - the
                // conflated total cannot distinguish this cause from a border
                // chunk, so a hazard-25 gate reading it could pass vacuously.
                memoryReachUnits.increment();
                tail.add(entity);
            } else if (gateReads && !readNeighbourhoodLive(level, chunk.x, chunk.z)) {
                unreadyUnits.increment();
                tail.add(entity);
            } else {
                buckets.computeIfAbsent(region.id(), k -> new ArrayList<>()).add(entity);
                bucketRegions.putIfAbsent(region.id(), region);
            }
        }));

        // Increment 6 (RFC-0007 Ã‚Â§3.2): each bucket drains its region's owner
        // mail first, under the bucket's own REGION context Ã¢â‚¬â€ delivery lands
        // before any of the owner's simulation this section. Flag captured
        // once so the whole section sees one policy.
        boolean drainMail = mailRouted;
        long[] partition = new long[buckets.size()];
        List<WeftScheduler.OwnedSection> sections = new ArrayList<>(buckets.size());
        int i = 0;
        for (var bucket : buckets.entrySet()) {
            partition[i++] = bucket.getKey();
            Region bucketRegion = bucketRegions.get(bucket.getKey());
            sections.add(new WeftScheduler.OwnedSection(bucket.getKey(), () -> {
                if (drainMail) {
                    OwnerMail.drainInto(bucketRegion);
                }
                for (Entity entity : bucket.getValue()) {
                    ticker.accept(entity);
                }
            }));
        }
        String[] threads = runBuckets(engine, sections,
                level.dimension().location().toString(), "ENTITY");
        readyCache = null;
        if (unmapped[0] > 0) {
            unmappedUnits.add(unmapped[0]);
        }
        if (!tail.isEmpty()) {
            engine.runOwnedSerial(ownerId(level), () -> {
                for (Entity entity : tail) {
                    ticker.accept(entity);
                }
            });
        }
        drainSectionEndTasks();
        partitionedSections.increment();
        lastEntityPartition = partition;
        lastEntityPartitionThreads = threads;
    }

    private static void updateSingleJoin(boolean value) {
        boolean was = singleJoin;
        if (!was && value) {
            // Activation may follow chunk loads that already populated
            // vanilla's global lists. First fused BE collection adopts them.
            singleJoin = true;
            return;
        }
        if (was && !value) {
            // Disable interception before restoring retained units to vanilla.
            // Otherwise our own add* calls are captured back into fusedUnits.
            singleJoin = false;
            flushFusedUnitsToVanilla();
            return;
        }
        singleJoin = value;
    }

    private static void flushFusedUnitsToVanilla() {
        for (var levelEntry : fusedUnits.entrySet()) {
            ServerLevel level = levelEntry.getKey();
            List<BlockEntity> fresh = new ArrayList<>();
            for (var regionEntry : levelEntry.getValue().snapshot()) {
                FusedRegionUnits units = regionEntry.getValue();
                for (BeTickUnit unit : units.tickers.drainAll()) {
                    if (!unit.isRemoved()) {
                        level.addBlockEntityTicker(unit.ticker());
                    }
                }
                for (BeTickUnit unit : units.afterFreshTickers) {
                    if (!unit.isRemoved()) {
                        level.addBlockEntityTicker(unit.ticker());
                    }
                }
                fresh.addAll(units.fresh.drainAll());
                units.afterFreshTickers.clear();
            }
            if (!fresh.isEmpty()) {
                level.addFreshBlockEntities(fresh);
            }
        }
        fusedUnits.clear();
        fusedFrames.clear();
    }

    private static void captureFusedEntitySection(ServerLevel level, EntityTickList list,
                                                  Consumer<Entity> ticker,
                                                  BiConsumer<EntityTickList, Consumer<Entity>> original,
                                                  WeftScheduler engine) {
        if (fusedFrames.containsKey(level)) {
            throw new IllegalStateException("singleJoinTick entity section captured twice before BE section");
        }
        RegionManager topology = RegionTopology.managerFor(level);
        FusedFrame frame = new FusedFrame(ticker);
        readyCache = parallel ? new java.util.HashMap<>() : null;
        engine.runOwnedSerial(ownerId(level), () -> original.accept(list, entity -> {
            ChunkPos chunk = entity.chunkPosition();
            Region region = topology.regionAt(chunk.x, chunk.z);
            if (region == null) {
                throw new IllegalStateException("singleJoinTick entity has no topology owner at "
                        + entity.blockPosition());
            }
            if (parallel && (MemoryReachEntities.isMemoryReach(entity)
                    || !readNeighbourhoodLive(level, chunk.x, chunk.z))) {
                unreadyUnits.increment();
                // Attribute the cause, not just the total. The partitioned path
                // splits these out (memoryReachUnits) and the fused path did not,
                // so a p2fuse gate asserting hazard 25 here would read zero and
                // pass vacuously - the same defect the partitioned counters were
                // split to prevent, reintroduced on the newer path.
                if (MemoryReachEntities.isMemoryReach(entity)) {
                    memoryReachUnits.increment();
                }
                // Fusion cannot move this entity to a post-join tail: an entity
                // may register a BE that must tick in this tick's BE stage.
                // Keep it in owner order and conservatively run the whole fused
                // set on the server thread for this tick.
                frame.forceSerial = true;
            }
            long regionId = region.id();
            frame.entities.computeIfAbsent(regionId, ignored -> new ArrayList<>()).add(entity);
            frame.regions.putIfAbsent(regionId, region);
        }));
        readyCache = null;
        fusedFrames.put(level, frame);
    }

    /**
     * Called (server thread) from the wrapped {@code Level.tickBlockEntities}
     * body when the level is a ServerLevel. In partitioned mode the vanilla
     * loop runs as a collection pass Ã¢â‚¬â€ {@link #captureBlockEntityUnit}
     * buffers each unit Ã¢â‚¬â€ and the buckets execute afterwards, canonical
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

        if (singleJoin) {
            tickFusedSections(level, vanillaSection, engine);
            return;
        }

        TreeMap<Long, List<BeTickUnit>> buckets = new TreeMap<>();
        java.util.HashMap<Long, Region> bucketRegions = new java.util.HashMap<>();
        List<Runnable> tail = new ArrayList<>();
        beBuckets = buckets;
        beBucketRegions = bucketRegions;
        beTail = tail;
        beLevel = level;
        // Hazard 24, same reasoning as the entity section.
        readyCache = parallel ? new java.util.HashMap<>() : null;
        beUnmapped = 0;
        try {
            engine.runOwnedSerial(ownerId(level), vanillaSection);
        } finally {
            beBuckets = null;
            beBucketRegions = null;
            beTail = null;
            beLevel = null;
            readyCache = null;
        }
        int unmappedAtCapture = beUnmapped;

        // Increment 6: bucket-head owner-mail drain, same contract as the
        // entity section (RFC-0007 Ã‚Â§3.2).
        boolean drainMail = mailRouted;
        // RFC-0006 hazard 23: sharding and region fan-out must not both engage
        // in the same section. runBuckets decides fan-out from this same bucket
        // count, so predict it here and stand sharding down when it is true.
        //
        // Without this, a region bucket runs ON A POOL WORKER and then calls
        // BlockEntityShards.runColoured, which submits its colour-pass tasks to
        // THE SAME pool and blocks on Future.get(). A nested blocking join
        // inside a fixed-size ForkJoinPool starves: the worker holding the outer
        // barrier is not available to run the inner tasks. That is not a
        // theoretical risk - it hung a live single-player server, with the
        // server thread parked in awaitAll and all 14 workers idle in awaitWork
        // (jstack, two dumps, same task object).
        //
        // Standing sharding down costs nothing, because RFC-0008 Ã‚Â§1 already
        // scopes it that way: block-entity sharding is "the solo-play lever,
        // where region-level parallelism is a no-op because the world is one
        // region". If two or more regions are already fanning out, the worker
        // threads are in use and intra-region sharding has nothing left to win -
        // it was only ever the answer for the single-bucket case.
        //
        // The alternative fix - flattening the colour passes into the outer
        // barrier so there is one level of submission instead of two - would let
        // both engage at once. It is a bigger change than a hang deserves, and
        // it buys throughput this design does not claim.
        boolean shardThisSection = sharded && !(parallel && buckets.size() >= 2);
        long[] partition = new long[buckets.size()];
        List<WeftScheduler.OwnedSection> sections = new ArrayList<>(buckets.size());
        int i = 0;
        for (var bucket : buckets.entrySet()) {
            partition[i++] = bucket.getKey();
            Region bucketRegion = bucketRegions.get(bucket.getKey());
            long regionId = bucket.getKey();
            List<BeTickUnit> units = bucket.getValue();
            sections.add(new WeftScheduler.OwnedSection(regionId, () -> {
                if (drainMail) {
                    OwnerMail.drainInto(bucketRegion);
                }
                if (shardThisSection
                        && units.size() >= WeftConfig.BLOCK_ENTITY_SHARD_MIN_UNITS) {
                    BlockEntityShards.runColoured(engine, regionId, units);
                } else {
                    units.forEach(u -> u.unit().run());
                }
            }));
        }
        String[] beThreads = runBuckets(engine, sections,
                level.dimension().location().toString(), "BLOCK_ENTITY");
        if (unmappedAtCapture > 0) {
            unmappedUnits.add(unmappedAtCapture);
        }
        if (!tail.isEmpty()) {
            engine.runOwnedSerial(ownerId(level), () -> tail.forEach(Runnable::run));
        }
        drainSectionEndTasks();
        partitionedSections.increment();
        lastBlockEntityPartition = partition;
        lastBlockEntityPartitionThreads = beThreads;
        int units = tail.size();
        for (List<BeTickUnit> bucket : buckets.values()) {
            units += bucket.size();
        }
        lastBlockEntityUnits = units;
    }

    private static void tickFusedSections(ServerLevel level, Runnable vanillaSection,
                                          WeftScheduler engine) {
        FusedFrame frame = fusedFrames.remove(level);
        if (frame == null) {
            throw new IllegalStateException("singleJoinTick BE section reached without entity capture");
        }
        FusedLevelUnits levelUnits = fusedUnits.computeIfAbsent(level, ignored -> new FusedLevelUnits());
        TreeMap<Long, FusedRegionUnits> byRegion = levelUnits.regions;

        // Capture NeoForge fresh-BE work and vanilla tickers before fan-out. The
        // wrapped Level method still owns profiler/tickingBlockEntities state;
        // per-unit hooks divert work into persistent owner containers.
        fusedCaptureUnits = levelUnits;
        fusedCaptureRegions = frame.regions;
        fusedCaptureLevel = level;
        fusedCaptureFrame = frame;
        readyCache = parallel ? new java.util.HashMap<>() : null;
        fusedPhase.set(FusedPhase.CAPTURE);
        try {
            engine.runOwnedSerial(ownerId(level), vanillaSection);
        } finally {
            fusedPhase.remove();
            fusedCaptureUnits = null;
            fusedCaptureRegions = null;
            fusedCaptureLevel = null;
            fusedCaptureFrame = null;
            readyCache = null;
        }

        TreeMap<Long, Region> live = new TreeMap<>();
        RegionManager topology = RegionTopology.managerFor(level);
        rebalanceFusedUnits(topology, levelUnits);
        for (Region region : topology.all()) {
            live.put(region.id(), region);
            levelUnits.region(region.id());
        }
        frame.regions.putAll(live);
        for (Long regionId : frame.entities.keySet()) {
            levelUnits.region(regionId);
        }

        // NeoForge onLoad may mutate level-wide structures and register
        // tickers. Keep ticks containing fresh work on server thread; steady
        // state remains one parallel task per region.
        boolean hasFresh = byRegion.values().stream().anyMatch(value -> !value.fresh.isEmpty());
        boolean runParallel = parallel && !frame.forceSerial && !hasFresh && byRegion.size() >= 2;
        if (parallel && !runParallel) {
            // Fan-out was configured but stood down this tick. Counted so the
            // fallback path is provable rather than inferred from an absence.
            fusedSerialFallbacks.increment();
        }
        // Hazard 23: a fused outer task owns all stages. Never submit shard
        // work from that worker; only serial fusion may use the shard path.
        boolean shardThisTick = sharded && !runParallel && byRegion.size() == 1;
        List<WeftScheduler.FusedRegionTask> tasks = new ArrayList<>();
        long[] partition = new long[byRegion.size()];
        String[] threads = new String[byRegion.size()];
        int index = 0;
        for (var entry : byRegion.entrySet()) {
            long regionId = entry.getKey();
            FusedRegionUnits units = entry.getValue();
            Region region = frame.regions.get(regionId);
            List<Entity> entities = frame.entities.getOrDefault(regionId, List.of());
            partition[index] = regionId;
            final int threadIndex = index++;
            tasks.add(new WeftScheduler.FusedRegionTask(regionId, List.of(
                    () -> {
                        threads[threadIndex] = Thread.currentThread().getName();
                        if (runParallel) {
                            ParallelAccess.enterWorker();
                        }
                        try {
                            if (region != null) {
                                OwnerMail.drainInto(region);
                            }
                        } catch (Throwable failure) {
                            if (runParallel) {
                                ParallelAccess.exitWorker();
                            }
                            throw failure;
                        }
                    },
                    () -> {
                        try {
                            fusedCurrentRegion.set(regionId);
                            fusedCurrentUnits.set(units);
                            fusedPhase.set(FusedPhase.ENTITY);
                            try {
                                entities.forEach(frame.ticker);
                            } finally {
                                fusedPhase.remove();
                                fusedCurrentUnits.remove();
                                fusedCurrentRegion.remove();
                            }
                        } catch (Throwable failure) {
                            if (runParallel) {
                                ParallelAccess.exitWorker();
                            }
                            throw failure;
                        }
                    },
                    () -> {
                        try {
                            tickFusedBlockEntities(engine, regionId, levelUnits, units,
                                    shardThisTick);
                        } finally {
                            if (runParallel) {
                                ParallelAccess.exitWorker();
                            }
                        }
                    })));
        }
        engine.runOwnedFused(tasks, runParallel);

        drainSectionEndTasks();
        fusedTicks.increment();
        fusedRegions.add(tasks.size());
        partitionedSections.add(2);
        lastEntityPartition = partition;
        lastBlockEntityPartition = partition.clone();
        lastEntityPartitionThreads = threads;
        lastBlockEntityPartitionThreads = threads.clone();
        lastBlockEntityUnits = byRegion.values().stream()
                .mapToInt(value -> value.tickers.size()).sum();
    }

    /** Follow topology splits/merges between ticks; ownership never stays stale. */
    private static void rebalanceFusedUnits(RegionManager topology,
                                            FusedLevelUnits levelUnits) {
        List<BeTickUnit> tickers = new ArrayList<>();
        List<BlockEntity> fresh = new ArrayList<>();
        List<BeTickUnit> afterFresh = new ArrayList<>();
        synchronized (levelUnits) {
            for (FusedRegionUnits units : levelUnits.regions.values()) {
                tickers.addAll(units.tickers.drainAll());
                fresh.addAll(units.fresh.drainAll());
                afterFresh.addAll(units.afterFreshTickers);
            }
            levelUnits.regions.clear();
        }
        for (BeTickUnit unit : tickers) {
            Region region = topology.regionAtBlock(
                    unit.ticker().getPos().getX(), unit.ticker().getPos().getZ());
            if (region == null) {
                throw new IllegalStateException("singleJoinTick ticker lost topology owner at "
                        + unit.ticker().getPos());
            }
            levelUnits.region(region.id()).tickers.add(unit);
        }
        for (BlockEntity blockEntity : fresh) {
            BlockPos pos = blockEntity.getBlockPos();
            Region region = topology.regionAtBlock(pos.getX(), pos.getZ());
            if (region == null) {
                throw new IllegalStateException("singleJoinTick fresh BE lost topology owner at " + pos);
            }
            levelUnits.region(region.id()).fresh.add(blockEntity);
        }
        for (BeTickUnit unit : afterFresh) {
            Region region = topology.regionAtBlock(
                    unit.ticker().getPos().getX(), unit.ticker().getPos().getZ());
            if (region == null) {
                throw new IllegalStateException("singleJoinTick pending ticker lost topology owner at "
                        + unit.ticker().getPos());
            }
            levelUnits.region(region.id()).afterFreshTickers.add(unit);
        }
    }

    private static void tickFusedBlockEntities(WeftScheduler engine, long regionId,
                                               FusedLevelUnits levelUnits,
                                               FusedRegionUnits units,
                                               boolean shardThisTick) {
        fusedCurrentRegion.set(regionId);
        try {
        // Fresh block entities are CONSUMED, not ticked. PendingUnits.tick only
        // prunes a unit when its `removed` predicate says so, and a block entity
        // that loaded successfully is never isRemoved() - so tick() left every
        // fresh unit in the container permanently. Two bugs came out of that:
        //
        //   1. onLoad() ran again on every subsequent tick, forever. Vanilla
        //      calls it exactly once, and NeoForge's onLoad invalidates
        //      capabilities, so this was per-tick capability thrash on every
        //      block entity the level had ever loaded.
        //   2. `fresh` never emptied, so `hasFresh` below stayed true forever
        //      and the fused path stood down from fan-out PERMANENTLY after the
        //      first block entity placement - parallel regions silently became
        //      serial for the rest of the session.
        //
        // Draining is also the correct re-entrancy shape: an onLoad that
        // registers another fresh block entity adds to a now-empty container
        // and is handled on the next tick, which is the deferral tick() gave.
        List<BlockEntity> freshNow = units.fresh.drainAll();
        if (!freshNow.isEmpty()) {
            fusedCurrentUnits.set(units);
            fusedPhase.set(FusedPhase.FRESH);
            try {
                for (BlockEntity blockEntity : freshNow) {
                    if (!blockEntity.isRemoved() && blockEntity.hasLevel()) {
                        blockEntity.onLoad();
                    }
                }
            } finally {
                fusedPhase.remove();
                fusedCurrentUnits.remove();
            }
        }
        if (!units.afterFreshTickers.isEmpty()) {
            for (BeTickUnit unit : units.afterFreshTickers) {
                units.tickers.add(unit);
            }
            units.afterFreshTickers.clear();
        }
        fusedCurrentUnits.set(units);
        fusedPhase.set(FusedPhase.TICK);
        try {
        if (shardThisTick && units.tickers.size() >= WeftConfig.BLOCK_ENTITY_SHARD_MIN_UNITS) {
            List<BeTickUnit> captured = units.tickers.drainAll();
            captured.removeIf(unit -> {
                if (unit.isRemoved()) {
                    levelUnits.forget(unit.ticker());
                    return true;
                }
                return false;
            });
            BlockEntityShards.runColoured(engine, regionId, captured);
            captured.forEach(units.tickers::add);
        } else {
            units.tickers.tick(unit -> {
                if (unit.isRemoved()) {
                    levelUnits.forget(unit.ticker());
                    return true;
                }
                return false;
            }, unit -> unit.unit().run());
        }
        } finally {
            fusedPhase.remove();
            fusedCurrentUnits.remove();
        }
        } finally {
            fusedCurrentRegion.remove();
        }
    }

    /** Level.addBlockEntityTicker wrap: true means fused owner container accepted it. */
    public static boolean captureFusedBlockEntityTicker(ServerLevel level,
                                                        TickingBlockEntity ticker,
                                                        Runnable unit) {
        if (!singleJoin) {
            return false;
        }
        FusedRegionUnits units = fusedCurrentUnits.get();
        FusedLevelUnits levelUnits = fusedUnits.get(level);
        Region region = RegionTopology.managerFor(level).regionAtBlock(
                ticker.getPos().getX(), ticker.getPos().getZ());
        if (region == null) {
            throw new IllegalStateException("singleJoinTick ticker has no region at " + ticker.getPos());
        }
        BeTickUnit captured = makeBeTickUnit(level, ticker, unit);
        FusedPhase phase = fusedPhase.get();
        Long currentRegion = fusedCurrentRegion.get();
        if (currentRegion != null && currentRegion.longValue() != region.id()) {
            throw new IllegalStateException("singleJoinTick cross-owner ticker add from region "
                    + currentRegion + " to " + region.id() + " at " + ticker.getPos());
        }
        if (phase == FusedPhase.CAPTURE && level == fusedCaptureLevel) {
            fusedCaptureRegions.putIfAbsent(region.id(), region);
            fusedCaptureUnits.addTicker(region.id(), captured);
        } else if (units != null && levelUnits != null && phase == FusedPhase.FRESH) {
            // Vanilla merges pending tickers only after fresh onLoad callbacks.
            levelUnits.addTickerPending(region.id(), captured, true);
        } else if (units != null && levelUnits != null
                && (phase == FusedPhase.ENTITY || phase == FusedPhase.TICK)) {
            levelUnits.addTicker(region.id(), captured);
        } else if (phase == FusedPhase.NONE && level.getServer().isSameThread()) {
            // Chunk load may register tickers between level ticks. Vanilla puts
            // them straight in its live list; retaining them here makes them
            // live for this region's next BE stage.
            fusedUnits.computeIfAbsent(level, ignored -> new FusedLevelUnits())
                    .addTicker(region.id(), captured);
        } else {
            throw new IllegalStateException("singleJoinTick ticker add outside owner stage at "
                    + ticker.getPos());
        }
        return true;
    }

    /** Per-ticker tick-call wrap during fused collection; captures once, never runs inline. */
    public static boolean captureFusedExistingTicker(ServerLevel level,
                                                     TickingBlockEntity ticker,
                                                     Runnable unit) {
        if (!singleJoin || fusedPhase.get() != FusedPhase.CAPTURE
                || level != fusedCaptureLevel) {
            return false;
        }
        Region region = RegionTopology.managerFor(level).regionAtBlock(
                ticker.getPos().getX(), ticker.getPos().getZ());
        if (region == null) {
            throw new IllegalStateException("singleJoinTick existing ticker has no region at "
                    + ticker.getPos());
        }
        if (parallel && !readNeighbourhoodLive(level,
                ticker.getPos().getX() >> 4, ticker.getPos().getZ() >> 4)) {
            unreadyUnits.increment();
            // Hazard 24 own counter, same reason as the entity side: the
            // conflated total cannot tell a block entity absent-neighbour
            // deferral from an entity cause, so a fused hazard-24 gate needs
            // this one or it asserts on a number it did not cause.
            unreadyBlockEntityUnits.increment();
            // Same rule as unsafe entities: a post-join BE tail would break
            // entity-before-BE ordering. Stand down fan-out for this tick.
            // Collection is server-thread-only, so the frame is stable here.
            FusedFrame frame = fusedCaptureFrame;
            if (frame == null) {
                throw new IllegalStateException(
                        "singleJoinTick BE capture has no fused entity frame");
            }
            frame.forceSerial = true;
        }
        fusedCaptureRegions.putIfAbsent(region.id(), region);
        fusedCaptureUnits.addTicker(region.id(), makeBeTickUnit(level, ticker, unit));
        return true;
    }

    /** Level.addFreshBlockEntities wrap: true means fused owner containers accepted them. */
    public static boolean captureFusedFreshBlockEntities(ServerLevel level,
                                                         java.util.Collection<BlockEntity> fresh) {
        if (!singleJoin) {
            return false;
        }
        for (BlockEntity blockEntity : fresh) {
            BlockPos pos = blockEntity.getBlockPos();
            Region region = RegionTopology.managerFor(level).regionAtBlock(pos.getX(), pos.getZ());
            if (region == null) {
                throw new IllegalStateException("singleJoinTick fresh BE has no region at " + pos);
            }
            FusedRegionUnits units = fusedCurrentUnits.get();
            FusedLevelUnits levelUnits = fusedUnits.get(level);
            FusedPhase phase = fusedPhase.get();
            Long currentRegion = fusedCurrentRegion.get();
            if (currentRegion != null && currentRegion.longValue() != region.id()) {
                throw new IllegalStateException("singleJoinTick cross-owner fresh BE add from region "
                        + currentRegion + " to " + region.id() + " at " + pos);
            }
            if (phase == FusedPhase.CAPTURE && level == fusedCaptureLevel) {
                fusedCaptureRegions.putIfAbsent(region.id(), region);
                fusedCaptureUnits.region(region.id()).fresh.add(blockEntity);
            } else if (units != null && levelUnits != null && phase != FusedPhase.NONE) {
                levelUnits.region(region.id()).fresh.add(blockEntity);
            } else if (phase == FusedPhase.NONE && level.getServer().isSameThread()) {
                // NeoForge chunk load may enqueue fresh BEs outside either
                // section. Preserve vanilla's next-BE-section onLoad timing.
                fusedUnits.computeIfAbsent(level, ignored -> new FusedLevelUnits())
                        .region(region.id()).fresh.add(blockEntity);
            } else {
                throw new IllegalStateException("singleJoinTick fresh BE add outside owner stage at " + pos);
            }
        }
        return true;
    }

    private static BeTickUnit makeBeTickUnit(ServerLevel level, TickingBlockEntity ticker,
                                             Runnable unit) {
        BlockPos pos = ticker.getPos();
        var be = level.getBlockEntity(pos);
        boolean wide = be == null || WideReachBlockEntities.isWideReach(be.getType());
        return new BeTickUnit(ticker,
                dev.weft.engine.region.ChunkKey.fromBlock(pos.getX(), pos.getZ()), wide,
                () -> {
                    if (level.shouldTickBlocksAt(pos)) {
                        LegacyRouting.tickBlockEntityOrDefer(level, ticker, unit);
                    }
                });
    }

    /**
     * Receives the wall time of each vanilla tick section the partitioner
     * executes. Installed only by benchmarks (production reads section timing
     * through the WS-7 exporter's histograms instead).
     *
     * <p>Exists because the exporter aggregates: a histogram cannot be sliced
     * into the alternating phases an interleaved A/B/A/B benchmark pools, and
     * <em>per-tick section samples pooled per phase</em> is the ruler P2's
     * first throughput attempt lacked Ã¢â‚¬â€ it judged a change confined to one
     * section by full-tick MSPT, and the effect was swamped (RFC-0008 Ã‚Â§4,
     * the retracted 1.59x).
     *
     * <p>Called on the server thread, after the barrier, once per section.
     */
    public interface SectionProbe {
        /**
         * @param sectionKind  {@code "ENTITY"} or {@code "BLOCK_ENTITY"}
         * @param sectionNanos wall time the vanilla section paid, barrier included
         * @param buckets      region buckets this section ran
         * @param fannedOut    whether they ran concurrently
         */
        void onSection(String sectionKind, long sectionNanos, int buckets, boolean fannedOut);
    }

    private static volatile SectionProbe sectionProbe;

    /**
     * Install (or clear, with null) the benchmark section tap. A test MUST
     * clear it before finishing: it is a static hook on the tick path, and a
     * probe left installed would silently charge one batch's clock reads to
     * every later batch in the same server.
     */
    public static void setSectionProbe(SectionProbe probe) {
        sectionProbe = probe;
    }

    /**
     * Execute one section's buckets: fanned out on the engine pool when
     * parallel mode is on and there are Ã¢â€°Â¥2 buckets (RFC-0006 Ã‚Â§2 Ã¢â‚¬â€ the
     * server thread barriers here), otherwise increment-4 serial on the
     * calling thread. Region workers are flagged via {@link ParallelAccess}
     * so the safety mixins engage only inside buckets. Returns the thread
     * name each bucket ran on (E1 gametest probe).
     */
    private static String[] runBuckets(WeftScheduler engine,
                                       List<WeftScheduler.OwnedSection> sections) {
        return runBuckets(engine, sections, "", "");
    }

    /**
     * As above, additionally carrying the WS-7 timing probe (RFC-0009 Ã‚Â§9.2 Ã¢â‚¬â€ the
     * one new measurement this workstream adds, and the one the review approved).
     *
     * <p><b>Cost: two {@code System.nanoTime()} calls per BUCKET per section, plus
     * one pair around the barrier.</b> O(buckets), not O(units): the existing P0
     * profiler pays two per <em>entity</em>. On a solo world Ã¢â‚¬â€ one region, which is
     * the WS-10 case Ã¢â‚¬â€ that is two clock reads for the whole section.
     *
     * <p>Double-gated on the observability module being active and on
     * {@code regionTimingEnabled} Ã¢â‚¬â€ or, in tests only, on a {@link SectionProbe}
     * being installed. When all three are off, the {@code long[]} is never
     * allocated and no clock is read (R6: zero residue). What it buys is
     * per-region tick duration, hottest-region share, and a worker-utilisation
     * ratio that is a real work-conservation figure rather than a scrape-time
     * sample of an idle pool (Ã‚Â§3.3).
     */
    private static String[] runBuckets(WeftScheduler engine,
                                       List<WeftScheduler.OwnedSection> sections,
                                       String levelId, String sectionKind) {
        String[] threads = new String[sections.size()];
        boolean exportTiming = !levelId.isEmpty()
                && dev.weft.neoforge.observability.WeftObservability.regionTimingActive();
        SectionProbe probe = sectionProbe;
        boolean timing = exportTiming || probe != null;
        long[] bucketNanos = timing ? new long[sections.size()] : null;
        long sectionStart = timing ? System.nanoTime() : 0L;
        boolean fannedOut = parallel && sections.size() >= 2;

        if (fannedOut) {
            List<WeftScheduler.OwnedSection> wrapped = new ArrayList<>(sections.size());
            for (int i = 0; i < sections.size(); i++) {
                final int index = i;
                WeftScheduler.OwnedSection section = sections.get(i);
                wrapped.add(new WeftScheduler.OwnedSection(section.ownerId(), () -> {
                    threads[index] = Thread.currentThread().getName();
                    long bucketStart = timing ? System.nanoTime() : 0L;
                    ParallelAccess.enterWorker();
                    try {
                        section.work().run();
                    } finally {
                        ParallelAccess.exitWorker();
                        if (timing) {
                            bucketNanos[index] = System.nanoTime() - bucketStart;
                        }
                    }
                }));
            }
            engine.runOwnedParallel(wrapped);
        } else {
            for (int i = 0; i < sections.size(); i++) {
                threads[i] = Thread.currentThread().getName();
                WeftScheduler.OwnedSection section = sections.get(i);
                long bucketStart = timing ? System.nanoTime() : 0L;
                engine.runOwnedSerial(section.ownerId(), section.work());
                if (timing) {
                    bucketNanos[i] = System.nanoTime() - bucketStart;
                }
            }
        }

        if (timing) {
            long sectionNanos = System.nanoTime() - sectionStart;
            if (exportTiming) {
                dev.weft.neoforge.observability.WeftObservability.onSectionBuckets(
                        levelId, sectionKind, bucketNanos, sectionNanos, fannedOut);
            }
            if (probe != null) {
                probe.onSection(sectionKind, sectionNanos, sections.size(), fannedOut);
            }
        }
        return threads;
    }

    /**
     * Queue work to run on the server thread right after the current
     * section's barrier (worker-context dimension changes, RFC-0006 Ã‚Â§3 #14).
     */
    public static void deferToSectionEnd(Runnable task) {
        sectionEndTasks.add(task);
    }

    /**
     * Hazard 21's measured deferral path. The callback is intentionally
     * wrapped here rather than counted in the mixin so completion records the
     * actual post-barrier execution thread, not merely queue admission.
     */
    public static void deferNavigationUpdate(ServerLevel level, Runnable task) {
        deferredNavigationUpdates.increment();
        deferToSectionEnd(() -> {
            if (Thread.currentThread() != level.getServer().getRunningThread()) {
                misplacedNavigationUpdates.increment();
                throw new IllegalStateException(
                        "deferred navigation update did not run on the server thread");
            }
            task.run();
            completedNavigationUpdates.increment();
        });
    }

    /** Test hook: invoke the real sendBlockUpdated mixin path inside an owned worker bucket. */
    public static boolean runNavigationUpdateProbe(ServerLevel level, Runnable workerUpdate) {
        WeftScheduler engine = active && parallel ? WeftMod.schedulerOrNull() : null;
        if (engine == null) {
            return false;
        }
        engine.runOwnedParallel(List.of(new WeftScheduler.OwnedSection(ownerId(level), () -> {
            ParallelAccess.enterWorker();
            try {
                workerUpdate.run();
            } finally {
                ParallelAccess.exitWorker();
            }
        })));
        drainSectionEndTasks();
        return true;
    }

    private static void drainSectionEndTasks() {
        Runnable task;
        while ((task = sectionEndTasks.poll()) != null) {
            task.run();
        }
    }

    /**
     * Per-ticker seam (the {@code TickingBlockEntity.tick()} call-site mixin).
     * Returns true when the unit was captured into a partition bucket Ã¢â‚¬â€ the
     * caller must then NOT run it inline. {@code unit} must not close over a
     * MixinExtras Operation (it executes after the handler frame returns);
     * the mixin passes the ticker's own {@code tick()} through the lane check.
     */
    public static boolean captureBlockEntityUnit(ServerLevel level, TickingBlockEntity ticker,
                                                 Runnable unit) {
        TreeMap<Long, List<BeTickUnit>> buckets = beBuckets;
        if (buckets == null || level != beLevel) {
            return false;
        }
        BlockPos pos = ticker.getPos();
        Region region = RegionTopology.managerFor(level).regionAtBlock(pos.getX(), pos.getZ());
        if (region == null) {
            beUnmapped++;
            beTail.add(unit);
        } else if (readyCache != null
                && !readNeighbourhoodLive(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            // Hazard 24: this is the exact shape that crashed - a block entity
            // whose one-block neighbour read crosses into an evicted chunk.
            unreadyUnits.increment();
            // Counted on its own too: the p2evictionchurn gate must be able to
            // assert THIS cause, not the union the total represents.
            unreadyBlockEntityUnits.increment();
            beTail.add(unit);
        } else {
            // Type lookup goes through the live block entity: a removed
            // ticker reports null and is treated as wide-reach (serial tail),
            // which is the conservative side of the choice.
            var be = level.getBlockEntity(pos);
            boolean wide = be == null || WideReachBlockEntities.isWideReach(be.getType());
            buckets.computeIfAbsent(region.id(), k -> new ArrayList<>())
                    .add(new BeTickUnit(ticker,
                            dev.weft.engine.region.ChunkKey.fromBlock(pos.getX(), pos.getZ()),
                            wide, unit));
            beBucketRegions.putIfAbsent(region.id(), region);
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

    public static long deferredNavigationUpdates() {
        return deferredNavigationUpdates.sum();
    }

    public static long completedNavigationUpdates() {
        return completedNavigationUpdates.sum();
    }

    public static long misplacedNavigationUpdates() {
        return misplacedNavigationUpdates.sum();
    }

    /**
     * Units the hazard-24 gate sent to the serial tail. Unlike unmapped units
     * this is expected to be non-zero on a world with chunk churn Ã¢â‚¬â€ a
     * pre-generator or a teleporting player evicts neighbours constantly Ã¢â‚¬â€ and
     * it is the counter that says how much work the gate is taking off the
     * workers.
     */
    public static long unreadyUnits() {
        return unreadyUnits.sum();
    }

    /**
     * The hazard-25 (memory-reach) share of {@link #unreadyUnits()}, counted
     * separately so a gate can assert that cause specifically. Reading the
     * conflated total instead lets a border-chunk deferral satisfy a
     * villager-shaped threshold with zero villagers classified.
     */
    public static long memoryReachUnits() {
        return memoryReachUnits.sum();
    }

    public static long fusedTicks() {
        return fusedTicks.sum();
    }

    public static long fusedRegions() {
        return fusedRegions.sum();
    }

    /**
     * Fused ticks that STOOD DOWN from fan-out and ran the whole set on the
     * server thread - readiness/hazard-25 deferral, fresh block-entity work, or
     * a single region.
     *
     * <p>Exists because {@code fusedTicks}/{@code fusedRegions} cannot express
     * it: both count a stood-down tick exactly like a fanned-out one, so a gate
     * reading them proves the fused PATH ran, never that fan-out happened or
     * that a fallback was taken. Both directions need their own number.
     */
    public static long fusedSerialFallbacks() {
        return fusedSerialFallbacks.sum();
    }

    /**
     * The block-entity share of {@link #unreadyUnits()} — hazard 24's own
     * shape, for a gate that must prove that cause rather than the union.
     */
    public static long unreadyBlockEntityUnits() {
        return unreadyBlockEntityUnits.sum();
    }

    /** Region ids of the most recent partitioned entity section (probe). */
    public static long[] lastEntityPartition() {
        return lastEntityPartition.clone();
    }

    /** Region ids of the most recent partitioned block-entity section (probe). */
    public static long[] lastBlockEntityPartition() {
        return lastBlockEntityPartition.clone();
    }

    /** Thread names per bucket of the most recent entity section (E1 probe). */
    public static String[] lastEntityPartitionThreads() {
        return lastEntityPartitionThreads.clone();
    }

    /** Thread names per bucket of the most recent block-entity section. */
    public static String[] lastBlockEntityPartitionThreads() {
        return lastBlockEntityPartitionThreads.clone();
    }

    /** Whether block-entity sharding is engaged (RFC-0008). */
    public static boolean isSharded() {
        return sharded;
    }

    // --- RFC-0008 block-entity sharding probes (E2 gate) ---

    public static long shardedSections() {
        return BlockEntityShards.shardedSections();
    }

    public static long shardedUnits() {
        return BlockEntityShards.shardedUnits();
    }

    public static long shardWideReachUnits() {
        return BlockEntityShards.wideReachUnits();
    }

    public static long shardPasses() {
        return BlockEntityShards.shardPasses();
    }

    /** Ticking block entities the most recent block-entity section captured. */
    public static int lastBlockEntityUnits() {
        return lastBlockEntityUnits;
    }

    /** Thread names of the most recent sharded section's tasks (fan-out probe). */
    public static String[] lastShardThreads() {
        return BlockEntityShards.lastShardThreads();
    }

    public static int lastMaxConcurrentShards() {
        return BlockEntityShards.lastMaxConcurrentShards();
    }

    /** Extra detail for the posture report / {@code /weft status} (R5). */
    public static String statusDetail() {
        long e = entitySections.sum();
        long b = blockEntitySections.sum();
        String sections = e == 0 && b == 0
                ? "no sections owned yet"
                : String.format("%d entity + %d block-entity sections owned", e, b);
        String mode = partitioned
                ? String.format("increment %s ticking (per-region buckets, canonical order, %s; "
                        + "%d partitioned sections, %d unmapped units, last partition %d regions)",
                        parallel ? "5 parallel" : "4 partitioned",
                        parallel ? "fan-out at >=2 buckets" : "serial",
                        partitionedSections.sum(), unmappedUnits.sum(), lastEntityPartition.length)
                : "increment 1 ticking (whole level, serial, server thread)";
        String mail = mailRouted ? "; " + OwnerMail.summary() : "";
        String fuse = singleJoin ? String.format("; single join: %d ticks / %d region tasks",
                fusedTicks.sum(), fusedRegions.sum()) : "";
        String shards = sharded ? "; " + BlockEntityShards.summary() : "";
        // Hazard 22's concession, kept in view: a small stable count is the
        // border ring being read as vanilla reads it; a growing one is a worker
        // reaching somewhere it should not.
        long border = ParallelAccess.borderReads();
        String borderReads = border == 0 ? "" : "; " + border + " border chunk reads";
        long unready = unreadyUnits.sum();
        String unreadyStr = unready == 0 ? "" : "; " + unready
                + " units deferred (read neighbourhood not live)";
        // Deliberately NOT appending fanOutEvidence() here: the status command
        // prints it as its own line for every module state. A module's
        // extraDetail is only rendered while it is ACTIVE, so folding it in here
        // would both duplicate the line in the one state that matters most and
        // hide it in the states where an operator is most likely to be confused.
        return mode + ": " + sections + "; " + RegionTopology.summary()
                + mail + fuse + shards + borderReads + unreadyStr;
    }

    /**
     * One-glance "is parallel actually fanning out?" line for field benches.
     * Topology region count alone is not enough: a fat single-bucket partition
     * keeps {@code ownedÃ¢Ë†Â¥=0} even when multiple regions exist on the map.
     */
    public static String fanOutEvidence() {
        int entityBuckets = lastEntityPartition.length;
        int beBuckets = lastBlockEntityPartition.length;
        int entityThreads = distinctThreads(lastEntityPartitionThreads);
        int beThreads = distinctThreads(lastBlockEntityPartitionThreads);
        boolean entityFan = parallel && entityBuckets >= 2;
        boolean beFan = parallel && beBuckets >= 2;
        WeftScheduler engine = WeftMod.schedulerOrNull();
        long ownedSerial = engine != null ? engine.ownedSerialSections() : 0L;
        long ownedParallel = engine != null ? engine.ownedParallelSections() : 0L;
        // Issue #16: `parallelRegions = true` in config is NOT the same thing as
        // a live worker path. When the module yielded (Moonrise) or was never
        // activated, `parallel` is false regardless of the config value, and an
        // operator reading their own toml would conclude the opposite. Say it.
        String engaged = !parallel
                ? (WeftConfig.PARALLEL_REGIONS
                        ? "DISARMED (parallelRegions is set in config, but the module is not "
                                + "active - no worker fan-out can engage)"
                        : "parallel flag off")
                : (entityFan || beFan || ownedParallel > 0)
                        ? "FAN-OUT ENGAGED"
                        : "topology may be multi-region but last section was single-bucket (no fan-out)";
        return String.format(
                "fan-out: %s; last buckets entity=%d (threads=%d) be=%d (threads=%d); owned serial=%d parallel=%d",
                engaged, entityBuckets, entityThreads, beBuckets, beThreads, ownedSerial, ownedParallel);
    }

    private static int distinctThreads(String[] names) {
        if (names == null || names.length == 0) {
            return 0;
        }
        return (int) java.util.Arrays.stream(names)
                .filter(n -> n != null && !n.isEmpty())
                .distinct()
                .count();
    }

    private static long ownerId(ServerLevel level) {
        return ownerIds.computeIfAbsent(level, l -> WeftMod.reserveRegionOwnerId());
    }
}
