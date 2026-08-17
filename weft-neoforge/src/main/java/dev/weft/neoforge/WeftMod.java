package dev.weft.neoforge;

import com.mojang.logging.LogUtils;
import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.profiler.WeftProfiler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Mod entry point. P1 scope (RFC-0001 §11): stand the engine up alongside the
 * vanilla tick, telemetry-only — no simulation is rerouted yet. The
 * {@code MinecraftServerMixin} feeds tick boundaries; the engine ticks empty
 * regions and reports phase timings, proving the pipeline in production
 * before it owns anything.
 */
@Mod("weft")
public final class WeftMod {

    private static WeftScheduler scheduler;
    private static RegionManager regions;

    private static final Logger LOGGER = LogUtils.getLogger();

    public WeftMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, WeftConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading e) -> WeftConfig.onConfigEvent(e));
        modBus.addListener((ModConfigEvent.Reloading e) -> WeftConfig.onConfigEvent(e));
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> WeftCommands.register(e));
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        long seed = event.getServer().getWorldData().worldGenOptions().seed();
        regions = new RegionManager(WeftConfig.MERGE_DISTANCE, seed);
        GraphScheduler graphs = new GraphScheduler((graph, tick) -> WeftSnapshots.EMPTY);
        scheduler = new WeftScheduler(
                Math.max(2, Runtime.getRuntime().availableProcessors() - WeftConfig.RESERVED_THREADS),
                regions, graphs, new WeftHooks());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (scheduler != null) {
            scheduler.close();
            scheduler = null;
        }
    }

    /** Called by MinecraftServerMixin each vanilla tick (telemetry mode). */
    public static void onVanillaTick() {
        WeftProfiler profiler = WeftProfiler.get();
        profiler.onTickStart();

        // Periodic P0 summary so headless/dedicated runs get data without /weft.
        if (WeftConfig.PROFILING_ENABLED
                && WeftConfig.REPORT_LOG_INTERVAL_TICKS > 0
                && profiler.tickCounter() % WeftConfig.REPORT_LOG_INTERVAL_TICKS == 0) {
            LOGGER.info("\n{}", profiler.buildReport());
        }

        if (scheduler == null) {
            return;
        }
        try {
            scheduler.tick();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
