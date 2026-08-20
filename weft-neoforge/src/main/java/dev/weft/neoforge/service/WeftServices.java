package dev.weft.neoforge.service;

import com.mojang.logging.LogUtils;
import dev.weft.engine.service.AsyncServiceRunner;
import dev.weft.engine.service.EntityCensus;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.mixin.optimization.ServerChunkCacheAccessor;
import dev.weft.neoforge.mixin.optimization.SpawnStateInvoker;
import dev.weft.neoforge.profiler.WeftProfiler;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Owns the per-level {@link AsyncServiceRunner}s for P1 services and the
 * spawn-density bookkeeping (RFC-0001 §11 P1).
 *
 * <p>Each server level tick end: capture the spawn snapshot (server thread,
 * O(entities), plain primitive arrays) and hand it to the runner. In SHADOW
 * mode the PREVIOUS tick's published result is compared against vanilla's
 * fresh {@code lastSpawnState} — exactly the staleness an authoritative
 * async spawn scan exhibits, so parity numbers are the evidence for (or
 * against) graduation. In AUTHORITATIVE mode
 * ({@link #createStateAuthoritative}) the published result replaces
 * vanilla's synchronous scan, with vanilla as the per-tick fallback whenever
 * the result isn't exactly one tick stale, and periodic verify ticks that
 * run vanilla's scan anyway and keep the parity evidence flowing.
 */
public final class WeftServices implements AutoCloseable {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private static final int CATEGORY_COUNT = MobCategory.values().length;
    private static final MobCategory[] CATEGORIES = MobCategory.values();

    private static final class LevelServices {
        final AsyncServiceRunner<SpawnDensityService.Snapshot, SpawnDensityService.Densities> spawnDensity;
        final AtomicLong parityTicks = new AtomicLong();
        final AtomicLong parityMismatchTicks = new AtomicLong();
        final AtomicLong captureNanosLast = new AtomicLong();
        volatile String lastMismatch = "";

        // Authoritative-mode bookkeeping (server thread writes, /weft reads).
        final AtomicLong authoritativeTicks = new AtomicLong();
        final AtomicLong fallbackTicks = new AtomicLong();
        final AtomicLong buildNanosLast = new AtomicLong();
        final AtomicLong buildFailStreak = new AtomicLong();
        long lastConsumedTick = Long.MIN_VALUE; // server thread only

        // Entity types with a MobSpawnCost in ANY biome of this level's
        // registry (usually a handful: soul-sand-valley skeleton/ghast/
        // enderman + modded). Only these pay a biome lookup at capture.
        volatile Set<EntityType<?>> chargedTypes;

        // Incremental census (server-thread-only): event-fed candidate to
        // replace the O(population) capture. Runs in drift-measurement mode:
        // reconciled against the full capture periodically; the drift numbers
        // are the go/no-go evidence for trusting events (taming/name-tagging
        // fires no event, so some drift is expected — we measure how much).
        final EntityCensus census = new EntityCensus(CATEGORY_COUNT);
        final AtomicLong reconciles = new AtomicLong();
        final AtomicLong driftTotal = new AtomicLong();
        volatile String lastDrift = "";

        LevelServices(String levelId) {
            this.spawnDensity = AsyncServiceRunner.withDedicatedWorker(new SpawnDensityService(levelId));
        }
    }

    private final Map<String, LevelServices> byLevel = new ConcurrentHashMap<>();

    /** The skip rules of NaturalSpawner.createState, minus the chunk gate. */
    private static MobCategory capCategoryOf(Entity entity) {
        if (entity instanceof Mob mob
                && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
            return null;
        }
        MobCategory category = entity.getClassification(true);
        return category == MobCategory.MISC ? null : category;
    }

    private LevelServices servicesOf(ServerLevel level) {
        return byLevel.computeIfAbsent(level.dimension().location().toString(), LevelServices::new);
    }

    // --- census event feed (owner thread; drift-measurement mode) ---
    // P2 E1: with parallel region buckets these events also fire on region
    // workers (spawns/removals/section moves happen inside entity ticks).
    // EntityCensus is owner-thread-only by contract, so off-thread events
    // capture their values eagerly and post the mutation to the next INGEST
    // (server thread). The one-tick lag is within the census's tolerance —
    // it is drift-measured and reconciled by design.

    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (SpawnDensityHooks.moduleActive() && event.getLevel() instanceof ServerLevel level) {
            MobCategory category = capCategoryOf(event.getEntity());
            if (category != null) {
                int id = event.getEntity().getId();
                long chunk = event.getEntity().chunkPosition().toLong();
                onOwner(level, () -> servicesOf(level).census.add(id, category.ordinal(), chunk));
            }
        }
    }

    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (SpawnDensityHooks.moduleActive() && event.getLevel() instanceof ServerLevel level) {
            int id = event.getEntity().getId();
            onOwner(level, () -> servicesOf(level).census.remove(id));
        }
    }

    public void onEnteringSection(EntityEvent.EnteringSection event) {
        if (SpawnDensityHooks.moduleActive() && event.didChunkChange()
                && event.getEntity().level() instanceof ServerLevel level) {
            int id = event.getEntity().getId();
            long chunk = event.getNewPos().chunk().toLong();
            onOwner(level, () -> servicesOf(level).census.move(id, chunk));
        }
    }

    private static void onOwner(ServerLevel level, Runnable mutation) {
        if (level.getServer().isSameThread()) {
            mutation.run();
        } else {
            dev.weft.neoforge.WeftMod.postToOwner(mutation);
        }
    }

    /** Wired to {@code LevelTickEvent.Post} on the game bus. */
    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!SpawnDensityHooks.moduleActive() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelServices services = servicesOf(level);
        Set<EntityType<?>> chargedTypes = chargedTypesOf(services, level);

        // --- capture (server thread; mirrors createState exactly) ---
        // Skip rules cross-checked against the decompiled 1.21.1 source:
        // persistence-exempt mobs are skipped, classification comes from the
        // NeoForge extension getClassification(true) (mods may override how
        // they count toward caps), chunk keys derive from blockPosition()
        // (vanilla's ChunkPos.asLong(blockpos), NOT the section-tracked
        // chunkPosition()), an entity only counts if its chunk passes
        // vanilla's own ChunkGetter gate (getFullChunk: visible holder +
        // completed full-chunk future — getChunkNow's status-only gate
        // over-counts, caught by the graduation gametest), and types with a
        // biome MobSpawnCost contribute a point charge at their block
        // position (getRoughBiome = the chunk's quart-pos noise biome).
        // Attributed to the profiler so /weft report shows the authoritative
        // mode's real residual on-thread cost next to the vanilla scan cost
        // it replaces.
        long t0 = System.nanoTime();
        WeftProfiler.get().push();
        int[] ids = new int[256];
        int[] categories = new int[256];
        long[] chunkKeys = new long[256];
        boolean[] mobs = new boolean[256];
        int size = 0;
        long[] chargePositions = new long[16];
        double[] charges = new double[16];
        int chargeCount = 0;
        ServerChunkCacheAccessor chunkGate = level.getChunkSource() instanceof ServerChunkCacheAccessor a
                ? a : null; // null only if the fail-soft accessor mixin did not apply
        LevelChunk[] queried = new LevelChunk[1];
        java.util.function.Consumer<LevelChunk> sink = c -> queried[0] = c;
        for (Entity entity : level.getAllEntities()) {
            MobCategory category = capCategoryOf(entity);
            if (category == null) {
                continue;
            }
            BlockPos pos = entity.blockPosition();
            long chunkKey = ChunkPos.asLong(pos);
            LevelChunk chunk;
            if (chunkGate != null) {
                queried[0] = null;
                chunkGate.weft$invokeGetFullChunk(chunkKey, sink);
                chunk = queried[0];
            } else {
                // Approximate gate for SHADOW mode without the mixin;
                // AUTHORITATIVE requires the mixin (R2) and never gets here.
                chunk = level.getChunkSource().getChunkNow(
                        ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            }
            if (chunk == null) {
                continue;
            }
            if (size == categories.length) {
                ids = java.util.Arrays.copyOf(ids, size * 2);
                categories = java.util.Arrays.copyOf(categories, size * 2);
                chunkKeys = java.util.Arrays.copyOf(chunkKeys, size * 2);
                mobs = java.util.Arrays.copyOf(mobs, size * 2);
            }
            ids[size] = entity.getId();
            categories[size] = category.ordinal();
            chunkKeys[size] = chunkKey;
            mobs[size] = entity instanceof Mob;
            size++;
            if (!chargedTypes.isEmpty() && chargedTypes.contains(entity.getType())) {
                MobSpawnSettings.MobSpawnCost cost = chunk
                        .getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                                QuartPos.fromBlock(pos.getZ()))
                        .value().getMobSettings().getMobSpawnCost(entity.getType());
                if (cost != null) {
                    if (chargeCount == charges.length) {
                        chargePositions = java.util.Arrays.copyOf(chargePositions, chargeCount * 2);
                        charges = java.util.Arrays.copyOf(charges, chargeCount * 2);
                    }
                    chargePositions[chargeCount] = pos.asLong();
                    charges[chargeCount] = cost.charge();
                    chargeCount++;
                }
            }
        }
        WeftProfiler.get().popGlobal("weft:spawn_density/capture");
        services.captureNanosLast.set(System.nanoTime() - t0);

        // --- census drift measurement: reconcile against the capture ---
        // (the capture is ground truth; drift = what the event feed missed,
        // e.g. persistence flips from taming, which fire no event)
        if (WeftConfig.CENSUS_RECONCILE_INTERVAL_TICKS > 0
                && level.getGameTime() % WeftConfig.CENSUS_RECONCILE_INTERVAL_TICKS == 0) {
            EntityCensus.Drift drift = services.census.reconcile(ids, categories, chunkKeys, size);
            services.reconciles.incrementAndGet();
            if (drift.total() > 0) {
                services.driftTotal.addAndGet(drift.total());
                services.lastDrift = String.format("missing %d, stale %d, moved %d",
                        drift.missing(), drift.stale(), drift.moved());
            }
        }

        // --- SHADOW parity: previous tick's async result vs vanilla's fresh
        // state. Only meaningful while vanilla still computes its own scan;
        // in AUTHORITATIVE mode the verify ticks own this comparison.
        if (WeftConfig.SPAWN_DENSITY_MODE == SpawnDensityMode.SHADOW) {
            var previous = services.spawnDensity.latest();
            var vanilla = level.getChunkSource().getLastSpawnState();
            if (previous.isPresent() && vanilla != null) {
                diffAgainstVanilla(services, previous.get().result(), vanilla.getMobCategoryCounts());
            }
        }

        services.spawnDensity.refresh(level.getGameTime(),
                new SpawnDensityService.Snapshot(CATEGORY_COUNT, categories, chunkKeys, mobs, size,
                        chargePositions, charges, chargeCount));
    }

    /**
     * AUTHORITATIVE mode (called from the {@code tickChunks} mixin via
     * {@link SpawnDensityHooks}): hand vanilla a {@code SpawnState} built
     * from the previous tick's async result. Falls back to vanilla's
     * synchronous scan whenever the result isn't exactly the previous tick's
     * (compute still in flight, level just loaded) — never blocks. Every
     * {@code spawnDensityVerifyIntervalTicks} the vanilla scan runs anyway,
     * is used for that tick, and our result is diffed against it.
     */
    NaturalSpawner.SpawnState createStateAuthoritative(
            ServerLevel level, int spawnableChunkCount, LocalMobCapCalculator localMobCaps,
            Supplier<NaturalSpawner.SpawnState> vanillaScan) {
        LevelServices services = servicesOf(level);
        long gameTime = level.getGameTime();
        var published = services.spawnDensity.latest().orElse(null);
        // Freshness gate: accept only the immediately-previous tick's capture
        // (the staleness shadow mode proved), and consume each publish at
        // most once — vanilla mutates the handed-off PotentialCalculator and
        // count map during spawning (afterSpawn), so a publish is spent the
        // moment it becomes a SpawnState.
        if (published == null || gameTime - published.tick() != 1
                || published.tick() <= services.lastConsumedTick) {
            services.fallbackTicks.incrementAndGet();
            return vanillaScan.get();
        }
        if (WeftConfig.SPAWN_DENSITY_VERIFY_INTERVAL_TICKS > 0
                && gameTime % WeftConfig.SPAWN_DENSITY_VERIFY_INTERVAL_TICKS == 0) {
            NaturalSpawner.SpawnState vanilla = vanillaScan.get();
            long mismatchesBefore = services.parityMismatchTicks.get();
            diffAgainstVanilla(services, published.result(), vanilla.getMobCategoryCounts());
            if (services.parityMismatchTicks.get() != mismatchesBefore) {
                // Verify mismatches are rare and diagnostic gold - surface
                // them in the log, not only in /weft services.
                LOGGER.info("spawn-density verify mismatch, {} tick {}: {}",
                        services.spawnDensity.serviceId(), gameTime, services.lastMismatch);
            }
            return vanilla;
        }
        try {
            long t0 = System.nanoTime();
            WeftProfiler.get().push();
            SpawnDensityService.Densities densities = published.result();
            services.lastConsumedTick = published.tick();
            Object2IntOpenHashMap<MobCategory> counts = new Object2IntOpenHashMap<>();
            for (MobCategory category : CATEGORIES) {
                int n = densities.global(category.ordinal());
                if (n > 0) {
                    counts.put(category, n);
                }
            }
            // Replay per-chunk Mob counts into vanilla's fresh local-cap
            // calculator; per-player attribution stays vanilla's own (lazy
            // player-range lookup, cached per chunk inside addMob).
            for (Map.Entry<Long, int[]> entry : densities.perChunkMobs().entrySet()) {
                ChunkPos chunkPos = new ChunkPos(entry.getKey());
                int[] chunkCounts = entry.getValue();
                for (int ordinal = 0; ordinal < chunkCounts.length; ordinal++) {
                    for (int i = 0; i < chunkCounts[ordinal]; i++) {
                        localMobCaps.addMob(chunkPos, CATEGORIES[ordinal]);
                    }
                }
            }
            NaturalSpawner.SpawnState state = SpawnStateInvoker.weft$newSpawnState(
                    spawnableChunkCount, counts, densities.potential(), localMobCaps);
            WeftProfiler.get().popGlobal("weft:spawn_density/build_state");
            services.buildNanosLast.set(System.nanoTime() - t0);
            services.buildFailStreak.set(0);
            services.authoritativeTicks.incrementAndGet();
            return state;
        } catch (Throwable t) {
            WeftProfiler.get().popGlobal("weft:spawn_density/build_state");
            SpawnDensityHooks.onBuildFailure(services.buildFailStreak.incrementAndGet(), t);
            services.fallbackTicks.incrementAndGet();
            return vanillaScan.get();
        }
    }

    /** Shared parity comparison (shadow every tick, authoritative on verify ticks). */
    private static void diffAgainstVanilla(LevelServices services,
                                           SpawnDensityService.Densities ours,
                                           Object2IntMap<MobCategory> vanillaCounts) {
        services.parityTicks.incrementAndGet();
        List<String> deltas = new ArrayList<>(0);
        for (MobCategory category : CATEGORIES) {
            if (category == MobCategory.MISC) {
                continue;
            }
            int mine = ours.global(category.ordinal());
            int theirs = vanillaCounts.getOrDefault(category, 0);
            if (mine != theirs) {
                deltas.add(category.getName() + " " + mine + " vs " + theirs);
            }
        }
        if (!deltas.isEmpty()) {
            services.parityMismatchTicks.incrementAndGet();
            services.lastMismatch = String.join(", ", deltas);
        }
    }

    /** Types with a MobSpawnCost anywhere in this level's biome registry (computed once). */
    private static Set<EntityType<?>> chargedTypesOf(LevelServices services, ServerLevel level) {
        Set<EntityType<?>> types = services.chargedTypes;
        if (types == null) {
            Set<EntityType<?>> collected = new HashSet<>();
            level.registryAccess().registryOrThrow(Registries.BIOME)
                    .forEach(biome -> collected.addAll(biome.getMobSettings().getEntityTypes()));
            services.chargedTypes = types = Set.copyOf(collected);
        }
        return types;
    }

    /** One level's spawn-density counters (gametest assertions + observability). */
    public record SpawnStats(long authoritativeTicks, long fallbackTicks, long parityTicks,
                             long parityMismatchTicks, long serviceFailures, boolean latchedOff,
                             String lastMismatch) {}

    /**
     * One level's census state for WS-7 (RFC-0009 Appendix A).
     *
     * <p>By mob <em>category</em>, not by entity type: that is what
     * {@link EntityCensus} tracks, because the mobcap is expressed in
     * categories. A per-type gauge would mean a full entity walk per scrape,
     * which is a measurement job in a different workstream.
     */
    public record CensusStats(int tracked, long drift, long reconciles,
                              Map<String, Integer> byCategory) {}

    /**
     * The last measured duration of each spawn-density stage, in nanoseconds
     * (WS-7 / RFC-0009 §3).
     *
     * <p>Last values, not a histogram, because last values are what the service
     * retains — {@code captureNanosLast} and friends are overwritten each tick.
     * A histogram would either need a new accumulation on the capture path or
     * would silently be a once-a-second <em>sample</em> of a per-tick quantity
     * dressed up as a distribution. Gauges named for what they are beat a
     * histogram that is not one.
     *
     * @param computeNanos 0 when the async service has published nothing yet
     */
    public record ServiceLatency(long captureNanos, long buildNanos, long computeNanos) {}

    /** Server-thread read; the exporter calls this while building its snapshot. */
    public ServiceLatency serviceLatency(ServerLevel level) {
        LevelServices s = servicesOf(level);
        return new ServiceLatency(s.captureNanosLast.get(), s.buildNanosLast.get(),
                s.spawnDensity.latest().map(p -> p.computeNanos()).orElse(0L));
    }

    /** Server-thread read; the exporter calls this while building its snapshot. */
    public CensusStats censusStats(ServerLevel level) {
        LevelServices s = servicesOf(level);
        EntityCensus.Counts counts = s.census.snapshot();
        Map<String, Integer> byCategory = new java.util.LinkedHashMap<>();
        for (MobCategory category : CATEGORIES) {
            byCategory.put(category.getName(), counts.global(category.ordinal()));
        }
        return new CensusStats(s.census.trackedCount(), s.driftTotal.get(),
                s.reconciles.get(), byCategory);
    }

    public SpawnStats spawnStats(ServerLevel level) {
        LevelServices s = servicesOf(level);
        return new SpawnStats(s.authoritativeTicks.get(), s.fallbackTicks.get(),
                s.parityTicks.get(), s.parityMismatchTicks.get(),
                s.spawnDensity.failureCount(), SpawnDensityHooks.isLatchedOff(),
                s.lastMismatch);
    }

    /** Human-readable status lines for {@code /weft services}. */
    public List<String> statusLines() {
        SpawnDensityMode mode = WeftConfig.SPAWN_DENSITY_MODE;
        if (byLevel.isEmpty()) {
            return List.of(SpawnDensityHooks.moduleActive()
                    ? "No service data yet - let the server tick."
                    : "Spawn-density service is inactive (mode " + mode + ", see /weft status).");
        }
        List<String> lines = new ArrayList<>();
        byLevel.forEach((levelId, s) -> {
            String computeUs = s.spawnDensity.latest()
                    .map(p -> String.format("%.0f", p.computeNanos() / 1e3)).orElse("-");
            long ticks = s.parityTicks.get();
            long mismatches = s.parityMismatchTicks.get();
            String parity = String.format("parity %d/%d ticks clean (%.2f%%)%s",
                    ticks - mismatches, ticks,
                    ticks == 0 ? 100.0 : 100.0 * (ticks - mismatches) / ticks,
                    mismatches == 0 ? "" : " last: " + s.lastMismatch);
            if (mode == SpawnDensityMode.AUTHORITATIVE) {
                long used = s.authoritativeTicks.get();
                long fellBack = s.fallbackTicks.get();
                lines.add(String.format(
                        "%s [authoritative%s]: %d ticks served async, %d fell back to vanilla, "
                                + "capture %.0f us, compute %s us, build %.0f us, verify %s, failures %d",
                        s.spawnDensity.serviceId(),
                        SpawnDensityHooks.isLatchedOff() ? ", LATCHED OFF" : "",
                        used, fellBack,
                        s.captureNanosLast.get() / 1e3,
                        computeUs,
                        s.buildNanosLast.get() / 1e3,
                        parity,
                        s.spawnDensity.failureCount()));
            } else {
                lines.add(String.format(
                        "%s [shadow]: capture %.0f us, compute %s us, %s, failures %d",
                        s.spawnDensity.serviceId(),
                        s.captureNanosLast.get() / 1e3,
                        computeUs,
                        parity,
                        s.spawnDensity.failureCount()));
            }
            lines.add(String.format(
                    "  census: %d tracked, drift %d over %d reconciles%s",
                    s.census.trackedCount(),
                    s.driftTotal.get(), s.reconciles.get(),
                    s.lastDrift.isEmpty() ? "" : " (last: " + s.lastDrift + ")"));
        });
        return lines;
    }

    @Override
    public void close() {
        byLevel.values().forEach(s -> s.spawnDensity.close());
        byLevel.clear();
    }
}
