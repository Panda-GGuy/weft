package dev.weft.neoforge;

import dev.weft.engine.sched.WeftScheduler;

/**
 * Serialized-phase hooks. P1: empty (telemetry mode). P2 fills LEGACY with the
 * sandbox's LegacyLane and GLOBAL with global-state ticking (RFC §4.3).
 */
final class WeftHooks implements WeftScheduler.Hooks {
}
