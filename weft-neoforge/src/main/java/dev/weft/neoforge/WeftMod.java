package dev.weft.neoforge;

import dev.weft.engine.graph.GraphScheduler;
import dev.weft.engine.region.RegionManager;
import dev.weft.engine.sched.WeftScheduler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

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

    public WeftMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
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
