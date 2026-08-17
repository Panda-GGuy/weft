package dev.weft.neoforge.service;

import dev.weft.engine.service.AsyncServiceRunner;
import dev.weft.neoforge.WeftConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the per-level {@link AsyncServiceRunner}s for P1 services and the
 * shadow-mode parity bookkeeping (RFC-0001 §11 P1).
 *
 * <p>Each server level tick end: capture the spawn snapshot (server thread,
 * O(entities), plain primitive arrays), hand it to the runner, and compare
 * the PREVIOUS tick's published result against vanilla's fresh
 * {@code lastSpawnState} — that is exactly the staleness an authoritative
 * async spawn scan would exhibit, so parity numbers here are the evidence
 * for (or against) flipping it on. Small transient deltas are expected
 * (entities spawn/die between the chunk tick and tick end); what matters is
 * the distribution, not occasional off-by-a-few.
 */
public final class WeftServices implements AutoCloseable {

    private static final int CATEGORY_COUNT = MobCategory.values().length;

    private static final class LevelServices {
        final AsyncServiceRunner<SpawnDensityService.Snapshot, SpawnDensityService.Densities> spawnDensity;
        final AtomicLong parityTicks = new AtomicLong();
        final AtomicLong parityMismatchTicks = new AtomicLong();
        final AtomicLong captureNanosLast = new AtomicLong();
        volatile String lastMismatch = "";

        LevelServices(String levelId) {
            this.spawnDensity = AsyncServiceRunner.withDedicatedWorker(new SpawnDensityService(levelId));
        }
    }

    private final Map<String, LevelServices> byLevel = new ConcurrentHashMap<>();

    /** Wired to {@code LevelTickEvent.Post} on the game bus. */
    public void onLevelTickPost(LevelTickEvent.Post event) {
        if (!WeftConfig.SPAWN_SERVICE_SHADOW || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LevelServices services = byLevel.computeIfAbsent(
                level.dimension().location().toString(), LevelServices::new);

        // --- capture (server thread; mirrors createState exactly) ---
        // Skip rules cross-checked against the decompiled 1.21.1 source:
        // persistence-exempt mobs are skipped, classification comes from the
        // NeoForge extension getClassification(true) (mods may override how
        // they count toward caps), and an entity only counts if its chunk
        // resolves as a full chunk (the ChunkGetter.query gate).
        long t0 = System.nanoTime();
        int[] categories = new int[256];
        long[] chunkKeys = new long[256];
        int size = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob
                    && (mob.isPersistenceRequired() || mob.requiresCustomPersistence())) {
                continue;
            }
            MobCategory category = entity.getClassification(true);
            if (category == MobCategory.MISC) {
                continue;
            }
            var chunkPos = entity.chunkPosition();
            if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                continue;
            }
            if (size == categories.length) {
                categories = java.util.Arrays.copyOf(categories, size * 2);
                chunkKeys = java.util.Arrays.copyOf(chunkKeys, size * 2);
            }
            categories[size] = category.ordinal();
            chunkKeys[size] = chunkPos.toLong();
            size++;
        }
        services.captureNanosLast.set(System.nanoTime() - t0);

        // --- parity check: previous tick's async result vs vanilla's fresh state ---
        var previous = services.spawnDensity.latest();
        var vanilla = level.getChunkSource().getLastSpawnState();
        if (previous.isPresent() && vanilla != null) {
            services.parityTicks.incrementAndGet();
            List<String> deltas = new ArrayList<>(0);
            var vanillaCounts = vanilla.getMobCategoryCounts();
            for (MobCategory category : MobCategory.values()) {
                if (category == MobCategory.MISC) {
                    continue;
                }
                int ours = previous.get().result().global(category.ordinal());
                int theirs = vanillaCounts.getOrDefault(category, 0);
                if (ours != theirs) {
                    deltas.add(category.getName() + " " + ours + " vs " + theirs);
                }
            }
            if (!deltas.isEmpty()) {
                services.parityMismatchTicks.incrementAndGet();
                services.lastMismatch = String.join(", ", deltas);
            }
        }

        services.spawnDensity.refresh(level.getGameTime(),
                new SpawnDensityService.Snapshot(CATEGORY_COUNT, categories, chunkKeys, size));
    }

    /** Human-readable status lines for {@code /weft services}. */
    public List<String> statusLines() {
        if (byLevel.isEmpty()) {
            return List.of(WeftConfig.SPAWN_SERVICE_SHADOW
                    ? "No service data yet - let the server tick."
                    : "Spawn-density shadow service is disabled (spawnServiceShadow=false).");
        }
        List<String> lines = new ArrayList<>();
        byLevel.forEach((levelId, s) -> {
            String computeUs = s.spawnDensity.latest()
                    .map(p -> String.format("%.0f", p.computeNanos() / 1e3)).orElse("-");
            long ticks = s.parityTicks.get();
            long mismatches = s.parityMismatchTicks.get();
            lines.add(String.format(
                    "%s [shadow]: capture %.0f us, compute %s us, parity %d/%d ticks clean (%.2f%%)%s, failures %d",
                    s.spawnDensity.serviceId(),
                    s.captureNanosLast.get() / 1e3,
                    computeUs,
                    ticks - mismatches, ticks,
                    ticks == 0 ? 100.0 : 100.0 * (ticks - mismatches) / ticks,
                    mismatches == 0 ? "" : " last: " + s.lastMismatch,
                    s.spawnDensity.failureCount()));
        });
        return lines;
    }

    @Override
    public void close() {
        byLevel.values().forEach(s -> s.spawnDensity.close());
        byLevel.clear();
    }
}
