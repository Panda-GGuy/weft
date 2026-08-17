package dev.weft.neoforge;

import com.mojang.logging.LogUtils;
import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
import dev.weft.neoforge.coexist.WeftModules;
import dev.weft.neoforge.profiler.WeftProfiler;
import dev.weft.neoforge.service.WeftServices;
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
    private static WeftServices services;

    private static final Logger LOGGER = LogUtils.getLogger();

    public WeftMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, WeftConfig.SPEC);
        modBus.addListener((ModConfigEvent.Loading e) -> {
            WeftConfig.onConfigEvent(e);
            WeftModules.onConfigReload();
        });
        modBus.addListener((ModConfigEvent.Reloading e) -> {
            WeftConfig.onConfigEvent(e);
            WeftModules.onConfigReload();
        });
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> WeftCommands.register(e));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (services != null) {
                services.onLevelTickPost(e);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
            if (services != null) {
                services.onEntityJoin(e);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) -> {
            if (services != null) {
                services.onEntityLeave(e);
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityEvent.EnteringSection e) -> {
            if (services != null) {
                services.onEnteringSection(e);
            }
        });
    }

    /** P1 service status for {@code /weft services}; empty before server start. */
    public static java.util.List<String> serviceStatusLines() {
        WeftServices s = services;
        return s == null ? java.util.List.of("Server not running.") : s.statusLines();
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        // RFC-0003 R5: resolve every module down the coexistence ladder and
        // log the one-glance posture table before anything starts working.
        WeftModules.resolveAndLog();
        long seed = event.getServer().getWorldData().worldGenOptions().seed();
        services = new WeftServices();
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
        if (services != null) {
            services.close();
            services = null;
        }
    }

    /** Called by MinecraftServerMixin each vanilla tick (telemetry mode). */
    public static void onVanillaTick() {
        WeftProfiler profiler = WeftProfiler.get();
        profiler.onTickStart();

        // Periodic P0 summary so headless/dedicated runs get data without /weft.
        // Built off-thread: buildReport() is thread-safe by design and costs
        // ~50ms on a very busy pack's window (measured by EngineBenchmark) —
        // that would be a full skipped tick if done here.
        if (WeftConfig.PROFILING_ENABLED
                && WeftConfig.REPORT_LOG_INTERVAL_TICKS > 0
                && profiler.tickCounter() % WeftConfig.REPORT_LOG_INTERVAL_TICKS == 0) {
            java.util.concurrent.CompletableFuture.runAsync(
                    () -> LOGGER.info("\n{}", profiler.buildReport()));
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
