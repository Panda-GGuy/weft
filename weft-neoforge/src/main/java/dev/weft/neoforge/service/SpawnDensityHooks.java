package dev.weft.neoforge.service;

import com.mojang.logging.LogUtils;
import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import org.slf4j.Logger;

import java.util.function.Supplier;

/**
 * P1 spawn-density loader-side glue: the AUTHORITATIVE-mode entry point the
 * {@code ServerChunkCache.tickChunks} mixin calls in place of vanilla's
 * synchronous {@code NaturalSpawner.createState} scan.
 *
 * <p>Fail-soft on every rung (RFC-0003 R2): module inactive, mode not
 * AUTHORITATIVE, services not up, async result missing or stale, or any
 * throwable while building the state — all fall back to running vanilla's
 * own scan for that tick, never blocking on the worker. Repeated build
 * failures latch the authoritative path off entirely (ladder rung 3) and
 * report once; vanilla behavior continues untouched.
 *
 * <p>The {@code active} flag is owned by the coexistence resolution
 * ({@code WeftModules}).
 */
public final class SpawnDensityHooks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Consecutive build failures before the authoritative path latches off. */
    private static final int FAILURE_LATCH = 3;

    private static volatile boolean active;
    private static volatile boolean latchedOff;

    private SpawnDensityHooks() {}

    /** RFC-0003 R2 runtime applied-check for the fail-soft mixin. */
    public static boolean hooksApplied() {
        return SpawnDensityMarker.class.isAssignableFrom(ServerChunkCache.class);
    }

    /** Owned by the WeftModules coexistence resolution. */
    public static void setActive(boolean value) {
        active = value;
        if (value) {
            latchedOff = false; // a fresh resolve (config reload) re-arms the path
        }
    }

    /** True when capture/compute should run at all (SHADOW or AUTHORITATIVE). */
    public static boolean moduleActive() {
        return active;
    }

    /**
     * Called (server thread) from the wrapped {@code createState} call site
     * inside {@code ServerChunkCache.tickChunks}. Returns the state vanilla
     * should use this tick: ours when fresh, vanilla's own otherwise.
     */
    public static NaturalSpawner.SpawnState createStateHook(
            ServerLevel level, int spawnableChunkCount, LocalMobCapCalculator localMobCaps,
            Supplier<NaturalSpawner.SpawnState> vanillaScan) {
        if (!active || latchedOff
                || WeftConfig.SPAWN_DENSITY_MODE != SpawnDensityMode.AUTHORITATIVE) {
            return vanillaScan.get();
        }
        WeftServices services = WeftMod.servicesOrNull();
        if (services == null) {
            return vanillaScan.get();
        }
        return services.createStateAuthoritative(level, spawnableChunkCount, localMobCaps,
                vanillaScan);
    }

    /**
     * Called by {@link WeftServices} when building a state from the async
     * result threw; latches the authoritative path off after
     * {@value #FAILURE_LATCH} failures (RFC-0003 ladder rung 3).
     */
    static void onBuildFailure(long failureCount, Throwable t) {
        if (failureCount >= FAILURE_LATCH && !latchedOff) {
            latchedOff = true;
            LOGGER.error("Weft spawn-density authoritative mode disabled itself after {} "
                    + "consecutive build failures; vanilla's synchronous scan continues "
                    + "untouched. Last failure:", failureCount, t);
        }
    }

    static boolean isLatchedOff() {
        return latchedOff;
    }
}
