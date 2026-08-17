package dev.weft.sandbox.coexist;

import java.util.Map;

/**
 * Resolves one Weft module's runtime state down the RFC-0003 ladder. Pure
 * function of the inputs so every rung is unit-testable off-game; the loader
 * module gathers the inputs (config flags, present mods, mixin-applied
 * checks) and applies the result.
 */
public final class CoexistencePolicy {

    private CoexistencePolicy() {}

    public enum State {
        /** Running normally. */
        ACTIVE,
        /** Running because the user force-enabled it over a yield (R4). */
        ACTIVE_FORCED,
        /** Off by its own config switch (R1). */
        DISABLED_CONFIG,
        /** Off because the user force-disabled it (R4). */
        DISABLED_FORCED,
        /** Rung 2: parked because a known neighbor owns the territory. */
        YIELDED,
        /** Rung 3: parked because its hooks did not actually apply. */
        SELF_DISABLED,
        /** Rung 4: true ownership conflict — the loader reports loudly. */
        REFUSED
    }

    public record Resolution(State state, String detail) {
        public boolean active() {
            return state == State.ACTIVE || state == State.ACTIVE_FORCED;
        }
    }

    /**
     * @param enabledByConfig the module's own switch (R1)
     * @param forceEnabled    user override list, force-on (R4)
     * @param forceDisabled   user override list, force-off (R4)
     * @param hooksApplied    runtime "did my hooks actually apply?" (R2);
     *                        pass true for modules without optional mixins
     * @param neighborPostures postures declared for this module by the
     *                        neighbors present (from {@link NeighborRegistry})
     */
    public static Resolution resolve(boolean enabledByConfig,
                                     boolean forceEnabled,
                                     boolean forceDisabled,
                                     boolean hooksApplied,
                                     Map<String, Posture> neighborPostures) {
        if (forceDisabled) {
            return new Resolution(State.DISABLED_FORCED, "user override (forceDisableModules)");
        }
        if (forceEnabled) {
            // R4 lets the user out-rank a yield, but nothing can out-rank a
            // hook that is not there.
            if (!hooksApplied) {
                return new Resolution(State.SELF_DISABLED,
                        "force-enabled by user, but mixin hooks did not apply");
            }
            return new Resolution(State.ACTIVE_FORCED, "user override (forceEnableModules)");
        }
        if (!enabledByConfig) {
            return new Resolution(State.DISABLED_CONFIG, "config switch off");
        }
        for (Map.Entry<String, Posture> e : neighborPostures.entrySet()) {
            if (e.getValue() == Posture.REFUSE) {
                return new Resolution(State.REFUSED, "ownership conflict with " + e.getKey());
            }
        }
        for (Map.Entry<String, Posture> e : neighborPostures.entrySet()) {
            if (e.getValue() == Posture.YIELD) {
                return new Resolution(State.YIELDED, "to " + e.getKey());
            }
        }
        if (!hooksApplied) {
            return new Resolution(State.SELF_DISABLED, "mixin hooks did not apply");
        }
        return new Resolution(State.ACTIVE, "");
    }
}
