package dev.weft.services.activation;

import java.util.Map;
import java.util.Set;

/**
 * WS-1 (RFC-0002): entity activation scheduling. Decides how often an
 * entity's expensive AI parts (sensing, goal/target selectors) should tick,
 * as a function of its distance to the nearest player.
 *
 * <p>Pure policy, no side effects: the loader module measures the distance,
 * asks {@link #intervalFor}, and gates the AI calls with
 * {@link #shouldRunThisTick}. Distance tiers:
 * <ul>
 *   <li>within {@code fullRateDistance} blocks — every tick (interval 1)</li>
 *   <li>within {@code reducedDistance} blocks — every {@code reducedInterval}
 *       ticks</li>
 *   <li>beyond — every {@code farInterval} ticks</li>
 * </ul>
 *
 * <p>Exempt types always run at full rate. A per-type override replaces the
 * tier interval whenever the type would otherwise be throttled: 1 is a
 * per-type opt-out (RFC-0002 WS-1 compat posture), larger values throttle
 * that type harder or softer than the tiers would. The full-rate ring is
 * inviolate — no override throttles an entity near a player. Instances are
 * immutable; the loader rebuilds on config change.
 */
public final class ActivationScheduler {

    /**
     * Distance tiers in blocks and their tick intervals. Distances compare
     * against 3D distance-squared so the caller never takes a square root.
     */
    public record Tiers(int fullRateDistance, int reducedDistance,
                        int reducedInterval, int farInterval) {
        public Tiers {
            if (fullRateDistance < 0 || reducedDistance < fullRateDistance) {
                throw new IllegalArgumentException(
                        "Require 0 <= fullRateDistance <= reducedDistance, got "
                                + fullRateDistance + ".." + reducedDistance);
            }
            if (reducedInterval < 1 || farInterval < reducedInterval) {
                throw new IllegalArgumentException(
                        "Require 1 <= reducedInterval <= farInterval, got "
                                + reducedInterval + ".." + farInterval);
            }
        }
    }

    private final double fullRateDistanceSq;
    private final double reducedDistanceSq;
    private final int reducedInterval;
    private final int farInterval;
    private final Set<String> exemptTypes;
    private final Map<String, Integer> typeIntervalOverrides;

    public ActivationScheduler(Tiers tiers, Set<String> exemptTypes,
                               Map<String, Integer> typeIntervalOverrides) {
        this.fullRateDistanceSq = (double) tiers.fullRateDistance() * tiers.fullRateDistance();
        this.reducedDistanceSq = (double) tiers.reducedDistance() * tiers.reducedDistance();
        this.reducedInterval = tiers.reducedInterval();
        this.farInterval = tiers.farInterval();
        this.exemptTypes = Set.copyOf(exemptTypes);
        this.typeIntervalOverrides = Map.copyOf(typeIntervalOverrides);
        typeIntervalOverrides.forEach((type, interval) -> {
            if (interval < 1) {
                throw new IllegalArgumentException(
                        "Override interval for " + type + " must be >= 1, got " + interval);
            }
        });
    }

    /**
     * Ticks between AI runs for this entity: 1 = full rate. {@code
     * distSqToNearestPlayer} is the squared block distance to the nearest
     * relevant player; pass {@link Double#POSITIVE_INFINITY} when no player
     * is in the level.
     */
    public int intervalFor(String typeId, double distSqToNearestPlayer) {
        if (exemptTypes.contains(typeId) || distSqToNearestPlayer <= fullRateDistanceSq) {
            return 1;
        }
        Integer override = typeIntervalOverrides.get(typeId);
        if (override != null) {
            return override;
        }
        return distSqToNearestPlayer <= reducedDistanceSq ? reducedInterval : farInterval;
    }

    /**
     * Whether an entity with the given interval runs its AI on this tick.
     * Staggered by entity id so a herd of throttled entities spreads its AI
     * runs across the interval window instead of thundering on one tick.
     */
    public static boolean shouldRunThisTick(long gameTime, int entityId, int interval) {
        if (interval <= 1) {
            return true;
        }
        return Math.floorMod(gameTime + entityId, interval) == 0;
    }
}
