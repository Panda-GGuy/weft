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
 * Mod entry point. P1 scope (RFC-0001 §11): the engine runs alongside the
 * vanilla tick ({@code MinecraftServerMixin} feeds tick boundaries) and real
 * work now routes through it — WS-1 throttles distant-mob AI in place, and
 * WS-2 computes mob paths on Weft workers with results delivered through the
 * scheduler's mailbox ({@link #postToOwner}) at the next tick boundary.
 * Regions still carry no chunks, so the region phases tick empty and owner
 * routing resolves to the global inbox, drained on the server thread at
 * INGEST — the same ownership path that becomes region-mail once later P2
 * increments assign real chunks. P2 increment 1 ({@code regionizedTicking},
 * default off) routes vanilla's entity/block-entity tick sections through
 * {@code WeftScheduler.runOwnedSerial} — serial, server thread, vanilla
 * order — establishing tick ownership with bit-identical semantics; see
 * {@code RegionizedTicking} and the RFC-0005 parity suite.
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

    /**
     * Post a task to its owner via the scheduler's mailbox (RFC-0001 §4.1):
     * it runs on the server thread when the scheduler drains the global
     * inbox at the top of the next tick (INGEST). This is the delivery path
     * for every async service result (WS-2). Safe from any thread. If the
     * scheduler is already gone (server stopping), the task is dropped —
     * its target state is being torn down with it.
     */
    public static void postToOwner(Runnable task) {
        WeftScheduler s = scheduler;
        if (s != null) {
            s.submit(new dev.weft.engine.mail.Message.Task(task));
        }
    }

    /** P1 service status for {@code /weft services}; empty before server start. */
    public static java.util.List<String> serviceStatusLines() {
        WeftServices s = services;
        return s == null ? java.util.List.of("Server not running.") : s.statusLines();
    }

    /** The live services container, or null outside server runtime (mixin entry path). */
    public static WeftServices servicesOrNull() {
        return services;
    }

    /** The live scheduler, or null outside server runtime (mixin entry path). */
    public static WeftScheduler schedulerOrNull() {
        return scheduler;
    }

    /**
     * Reserve an engine region-owner id (P2 increment 1: one per ServerLevel,
     * see {@code RegionizedTicking}). -1 outside server runtime.
     */
    public static long reserveRegionOwnerId() {
        RegionManager r = regions;
        return r != null ? r.reserveRegionId() : -1L;
    }

    // WS-10 (RFC-0004): the coexistence resolution owns this flag; the
    // scheduler is created after the first resolve, so remember it here and
    // apply on both creation and re-resolution.
    private static volatile boolean entityShardingActive;

    /** Wired as the entity_sharding module's applyActive (RFC-0003 R6). */
    public static void applyEntitySharding(boolean active) {
        entityShardingActive = active;
        WeftScheduler s = scheduler;
        if (s != null) {
            s.setEntitySharding(active, WeftConfig.ENTITY_SHARD_MIN_BATCH);
        }
    }

    /** R5 extra detail for the posture table / {@code /weft status}. */
    public static String entityShardingDetail() {
        WeftScheduler s = scheduler;
        if (s == null) {
            return "";
        }
        return String.format("last tick: %d regions sharded, max %d shards",
                s.lastShardedRegions(), s.lastMaxShards());
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
        scheduler.setEntitySharding(entityShardingActive, WeftConfig.ENTITY_SHARD_MIN_BATCH);
    }

    private void onServerStopping(ServerStoppingEvent event) {
        // Pathfinding workers first: they post into the scheduler's inbox.
        dev.weft.neoforge.path.PathfindingHooks.shutdown();
        // Level instances die with the server; drop their region-owner ids.
        dev.weft.neoforge.regiontick.RegionizedTicking.reset();
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
