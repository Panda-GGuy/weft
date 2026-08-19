package dev.weft.neoforge.legacy;

import dev.weft.api.CompatTier;
import dev.weft.neoforge.WeftMod;
import dev.weft.sandbox.LegacyLane;
import dev.weft.sandbox.ModClassifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * P2 increment 3 (RFC-0001 §7): the legacy lane goes live. Tick work owned by
 * Tier-2 (unverified) mods is extracted from the vanilla tick sections and
 * executed in the engine's LEGACY phase instead — single-threaded, on the
 * server thread, in vanilla's own iteration order, against a fully settled
 * world (§7.2), with per-mod cost attribution (§9.1's "your tick is 61% mod
 * X" number).
 *
 * <p><b>Where deferred work runs.</b> The engine tick executes at the head of
 * {@code MinecraftServer.tickServer}, so Phase 4 sits <em>between</em> vanilla
 * ticks: a unit extracted from vanilla tick N runs before vanilla tick N+1
 * touches anything — after tick N fully settled, at the same game time the
 * unit would have observed inline (the level's time increment for N+1 has not
 * happened yet). Each unit still runs exactly once per server tick; what
 * changes is its position within the tick, which is precisely the lane's
 * documented semantic (§7.2 — Phase 4 runs after parallel work settles).
 * Safety of the deferral leans on vanilla's own re-checks at execution time:
 * the entity consumer re-tests {@code isRemoved}/despawn/ticking-range, and
 * the BE ticker wrapper re-tests chunk tickability (removed BEs become
 * null-tickers).
 *
 * <p><b>Classification.</b> Loader-owned namespaces ({@code minecraft},
 * {@code neoforge}, {@code weft}) are Tier 0 — vanilla content is what the
 * engine itself owns — and everything else falls to the {@link ModClassifier}
 * (default Tier 2 per §7.1; the annotation/manifest feeds are the P4 compat-DB
 * deliverable, so today "not engine" simply means "legacy"). Verified mods
 * would tick inline (parallel once regions parallelize). Decisions are cached
 * per type; the per-unit hot cost is one map lookup.
 *
 * <p><b>Known gap (closed by the parallel-regions increment):</b> a legacy
 * passenger riding a vanilla vehicle is ticked by the vehicle's
 * {@code tickPassenger} inline, not through the entity section consumer, so
 * it does not route through the lane. Harmless while all ticking is still
 * server-thread serial; the parallel increment must defer the whole
 * vehicle+passenger chain when any participant is legacy.
 *
 * <p>The {@code active} flag is owned by the coexistence resolution
 * ({@code WeftModules}). R6: inactive means the wrapped call sites run the
 * vanilla unit directly — zero behavioral residue. The lane itself drains
 * every engine tick regardless, so deactivation mid-run cannot strand queued
 * units: they execute at the next tick head, then extraction simply stops.
 */
public final class LegacyRouting {

    private LegacyRouting() {}

    private static volatile boolean active;

    /** Created per server run on first use; dropped in {@link #reset}. */
    private static volatile LegacyLane lane;

    /**
     * Tier decisions for content the loader itself owns. Everything else is
     * the classifier's call (RFC-0001 §7.1: unknown defaults to Tier 2).
     */
    private static final Set<String> ENGINE_NAMESPACES = Set.of("minecraft", "neoforge", "weft");

    private static volatile ModClassifier classifier =
            new ModClassifier(Set.of(), Set.of(), Map.of());

    private static final ConcurrentHashMap<String, CompatTier> tierByBlockEntityType =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<EntityType<?>, CompatTier> tierByEntityType =
            new ConcurrentHashMap<>();

    /**
     * Test-only forced tiers, keyed by full type id ("minecraft:furnace").
     * The gametest environment has no Tier-2 mods, so the p2legacy gate
     * forces vanilla types onto the lane to exercise it; cleared in teardown.
     */
    private static final ConcurrentHashMap<String, CompatTier> testOverrides =
            new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.LongAdder deferredBlockEntities =
            new java.util.concurrent.atomic.LongAdder();
    private static final java.util.concurrent.atomic.LongAdder deferredEntities =
            new java.util.concurrent.atomic.LongAdder();

    /** RFC-0003 R2 applied-check (belt-and-braces: these mixins are fail-loud). */
    public static boolean hooksApplied() {
        return LegacyLaneBlockEntityMarker.class.isAssignableFrom(
                net.minecraft.world.level.Level.class)
                && dev.weft.neoforge.regiontick.RegionizedEntityTickMarker.class
                        .isAssignableFrom(ServerLevel.class);
    }

    /** Owned by the WeftModules coexistence resolution. */
    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Phase 4 body ({@code WeftHooks.runLegacy}): one lane pass. Runs under
     * the scheduler's LEGACY thread context on the server thread. Always
     * drains — see the class note on deactivation.
     */
    public static void runLegacyPhase(long tick) {
        LegacyLane l = lane;
        if (l != null) {
            l.runTick();
            // WS-7: the lane already timed its own pass; this only reads it.
            dev.weft.neoforge.observability.WeftObservability.onLegacyLanePass(l.lastTickNanos());
        }
    }

    /**
     * Entity-section seam: wraps vanilla's own per-entity consumer so Tier-2
     * entities are extracted to the lane while everything else runs inline,
     * untouched. Inactive: returns the consumer itself (zero residue).
     */
    public static Consumer<Entity> wrapEntityTicker(ServerLevel level, Consumer<Entity> vanilla) {
        if (!active || WeftMod.schedulerOrNull() == null) {
            return vanilla;
        }
        return entity -> {
            EntityType<?> type = entity.getType();
            if (tierOfEntity(type) == CompatTier.LEGACY) {
                deferredEntities.increment();
                lane().submit(modIdOf(EntityType.getKey(type)), submissionOrder(),
                        () -> vanilla.accept(entity));
            } else {
                vanilla.accept(entity);
            }
        };
    }

    /**
     * Block-entity seam: called for each ticker the vanilla loop was about to
     * run. Tier-2 tickers are extracted to the lane (the deferred call is the
     * ticker's own {@code tick()} — the vanilla wrapper with its liveness
     * re-checks); everything else runs inline via {@code inline}.
     */
    public static void tickBlockEntityOrDefer(ServerLevel level, TickingBlockEntity ticker,
                                              Runnable inline) {
        if (!active || WeftMod.schedulerOrNull() == null) {
            inline.run();
            return;
        }
        String typeId = ticker.getType();
        if (tierOfBlockEntity(typeId) == CompatTier.LEGACY) {
            deferredBlockEntities.increment();
            lane().submit(modIdOf(typeId), submissionOrder(), ticker::tick);
        } else {
            inline.run();
        }
    }

    /**
     * Ordering group for lane submissions (RFC-0006 hazard 16): the current
     * REGION owner id — the real region id inside a partitioned/parallel
     * bucket, the level owner id in whole-level mode. Deterministic either
     * way once the drain sorts by (regionOrder, seq).
     */
    private static long submissionOrder() {
        dev.weft.engine.guard.ThreadContext ctx = dev.weft.engine.guard.ThreadContext.current();
        return ctx.kind() == dev.weft.engine.guard.ThreadContext.Kind.REGION ? ctx.ownerId() : 0;
    }

    /** Units extracted since boot (parity vacuous-run guards + status). */
    public static long deferredBlockEntities() {
        return deferredBlockEntities.sum();
    }

    public static long deferredEntities() {
        return deferredEntities.sum();
    }

    /** The live lane (tests probe it; created on first use per server run). */
    public static LegacyLane lane() {
        LegacyLane l = lane;
        if (l == null) {
            synchronized (LegacyRouting.class) {
                l = lane;
                if (l == null) {
                    lane = l = new LegacyLane();
                }
            }
        }
        return l;
    }

    /** Extra detail for the posture report / {@code /weft status} (R5). */
    public static String statusDetail() {
        LegacyLane l = lane;
        long be = deferredBlockEntities.sum();
        long e = deferredEntities.sum();
        if (l == null || (be == 0 && e == 0)) {
            return "no Tier-2 tick work extracted yet";
        }
        String top = l.costByModNanos().entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(en -> String.format("%s %.1fms", en.getKey(), en.getValue() / 1e6))
                .collect(Collectors.joining(", "));
        return String.format("%d BE + %d entity units extracted; last pass %.2fms; top: %s",
                be, e, l.lastTickNanos() / 1e6, top);
    }

    /** Server stop: the world the queued work targets is being torn down. */
    public static void reset() {
        LegacyLane l = lane;
        if (l != null) {
            l.clear();
        }
        lane = null;
        tierByBlockEntityType.clear();
        tierByEntityType.clear();
        testOverrides.clear();
    }

    // --- classification ---

    private static CompatTier tierOfBlockEntity(String typeId) {
        return tierByBlockEntityType.computeIfAbsent(typeId, LegacyRouting::tierOfTypeId);
    }

    private static CompatTier tierOfEntity(EntityType<?> type) {
        return tierByEntityType.computeIfAbsent(type,
                t -> tierOfTypeId(EntityType.getKey(t).toString()));
    }

    private static CompatTier tierOfTypeId(String typeId) {
        CompatTier forced = testOverrides.get(typeId);
        if (forced != null) {
            return forced;
        }
        String namespace = namespaceOf(typeId);
        if (ENGINE_NAMESPACES.contains(namespace)) {
            return CompatTier.ENGINE;
        }
        // NeoForge convention: registry namespace == modid.
        return classifier.tierOfMod(namespace);
    }

    private static String namespaceOf(String typeId) {
        int colon = typeId.indexOf(':');
        return colon > 0 ? typeId.substring(0, colon) : ResourceLocation.DEFAULT_NAMESPACE;
    }

    private static String modIdOf(ResourceLocation key) {
        return key.getNamespace();
    }

    private static String modIdOf(String typeId) {
        return namespaceOf(typeId);
    }

    // --- test hooks (p2legacy gametest; cleared by reset/teardown) ---

    /** Force a full type id ("minecraft:furnace") onto a tier. Test only. */
    public static void forceTierForTest(String typeId, CompatTier tier) {
        testOverrides.put(typeId, tier);
        tierByBlockEntityType.clear();
        tierByEntityType.clear();
    }

    public static void clearTestOverrides() {
        testOverrides.clear();
        tierByBlockEntityType.clear();
        tierByEntityType.clear();
    }
}
