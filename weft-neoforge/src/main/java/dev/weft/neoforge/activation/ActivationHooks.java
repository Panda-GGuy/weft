package dev.weft.neoforge.activation;

import dev.weft.neoforge.WeftConfig;
import dev.weft.services.activation.ActivationScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * WS-1 loader-side glue: measures what the pure {@link ActivationScheduler}
 * needs (distance to nearest player, entity type id) and answers the mixin's
 * per-tick question. The {@code active} flag is owned by the coexistence
 * resolution ({@code WeftModules}) — never by config reads here — so a
 * yielded or self-disabled module is inert regardless of its own switch
 * (RFC-0003 R6: while inactive, every hook is a single volatile read
 * followed by vanilla behavior).
 */
public final class ActivationHooks {

    private ActivationHooks() {}

    private static volatile boolean active;
    private static volatile ActivationScheduler scheduler = buildScheduler();

    // Entity ids are registry objects with stable identity; memoize their
    // string form so the hot path never re-serializes a ResourceLocation.
    private static final Map<EntityType<?>, String> TYPE_IDS = new ConcurrentHashMap<>();

    private static final LongAdder throttleDecisions = new LongAdder();
    private static final LongAdder throttleSkips = new LongAdder();

    /** RFC-0003 R2 runtime applied-check for the fail-soft throttle mixin. */
    public static boolean hooksApplied() {
        return ActivationMarker.class.isAssignableFrom(Mob.class);
    }

    /** Owned by the WeftModules coexistence resolution. */
    public static void setActive(boolean value) {
        active = value;
    }

    /** Rebuild the immutable scheduler from config (load + reload). */
    public static void rebuildFromConfig() {
        scheduler = buildScheduler();
    }

    private static ActivationScheduler buildScheduler() {
        return new ActivationScheduler(
                new ActivationScheduler.Tiers(
                        WeftConfig.ACTIVATION_FULL_RATE_DISTANCE,
                        Math.max(WeftConfig.ACTIVATION_REDUCED_DISTANCE,
                                WeftConfig.ACTIVATION_FULL_RATE_DISTANCE),
                        WeftConfig.ACTIVATION_REDUCED_INTERVAL,
                        Math.max(WeftConfig.ACTIVATION_FAR_INTERVAL,
                                WeftConfig.ACTIVATION_REDUCED_INTERVAL)),
                WeftConfig.ACTIVATION_EXEMPT_TYPES,
                WeftConfig.ACTIVATION_TYPE_OVERRIDES);
    }

    /**
     * The interval the configured tiers assign this mob where it stands:
     * 1 = full rate / exempt / mid-fight. Independent of the module's active
     * flag and of the throttle mixin having applied — the P0 report uses it
     * to project WS-1 savings on packs that haven't enabled anything yet.
     */
    public static int projectedInterval(Mob mob) {
        // Built-in exemptions (RFC-0002 WS-1): raid mobs, and anything with a
        // live attack target - a mob mid-fight never loses ticks, so "targeting
        // a player" is covered with room to spare.
        if (mob.getTarget() != null || mob instanceof Raider
                || !(mob.level() instanceof ServerLevel level)) {
            return 1;
        }
        double distSq = Double.POSITIVE_INFINITY;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                distSq = Math.min(distSq, player.distanceToSqr(mob));
            }
        }
        String typeId = TYPE_IDS.computeIfAbsent(mob.getType(),
                type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        return scheduler.intervalFor(typeId, distSq);
    }

    /**
     * Called from the {@code Mob.serverAiStep} HEAD hook on the ticking
     * (server) thread. True = run AI this tick (the vanilla answer).
     */
    public static boolean shouldTickAi(Mob mob) {
        if (!active) {
            return true;
        }
        int interval = projectedInterval(mob);
        if (interval <= 1) {
            return true;
        }
        throttleDecisions.increment();
        boolean run = ActivationScheduler.shouldRunThisTick(
                mob.level().getGameTime(), mob.getId(), interval);
        if (!run) {
            throttleSkips.increment();
        }
        return run;
    }

    /** Point-in-time view of the throttle counters (WS-8 benchmark deltas). */
    public record Counters(long decisions, long skips) {
        public Counters minus(Counters earlier) {
            return new Counters(decisions - earlier.decisions, skips - earlier.skips);
        }
    }

    public static Counters counters() {
        return new Counters(throttleDecisions.sum(), throttleSkips.sum());
    }

    /** Extra detail for the posture report / {@code /weft status}. */
    public static String statusDetail() {
        long decisions = throttleDecisions.sum();
        long skips = throttleSkips.sum();
        if (decisions == 0) {
            return "no throttleable AI ticks yet";
        }
        return String.format("skipped %d of %d throttleable AI ticks (%.0f%%)",
                skips, decisions, 100.0 * skips / decisions);
    }
}
