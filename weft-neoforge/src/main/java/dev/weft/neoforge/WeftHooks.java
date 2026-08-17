package dev.weft.neoforge;

import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.legacy.LegacyRouting;

/**
 * Serialized-phase hooks (RFC §4.3). P2 increment 3 fills LEGACY with the
 * sandbox's lane — Tier-2 tick work extracted from the vanilla sections runs
 * here, single-threaded on the server thread between vanilla ticks, with
 * per-mod cost attribution ({@link LegacyRouting}). GLOBAL still empty
 * (global-state ticking arrives with the parallel-regions increment).
 */
final class WeftHooks implements WeftScheduler.Hooks {

    @Override
    public void runLegacy(long tick) {
        LegacyRouting.runLegacyPhase(tick);
    }
}
