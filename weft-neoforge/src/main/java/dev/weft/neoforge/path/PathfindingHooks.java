package dev.weft.neoforge.path;

import dev.weft.neoforge.WeftConfig;
import dev.weft.neoforge.WeftMod;
import dev.weft.services.path.PathService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * WS-2 loader-side glue (RFC-0002): routes vanilla pathfinding computes
 * through the engine's {@link PathService} when the module is active.
 *
 * <p>The compute itself stays vanilla's own {@code PathFinder.findPath}
 * (per-navigation instance, so single-flight per mob id means no instance
 * is ever used by two threads), run on a path worker over the
 * {@code PathNavigationRegion} vanilla already captured on the server
 * thread. That keeps exact node-evaluation parity — including modded
 * {@code NodeEvaluator} overrides — while removing the search from the
 * tick. Results return through the Weft scheduler's mailbox and are applied
 * by {@link AsyncPath#fill} at the next tick boundary (RFC-0001 §4.1
 * ownership discipline; the engine-native hierarchical/flow-field pathfinder
 * takes over as the compute when P2 gives the engine real region ownership).
 *
 * <p>Stale-read caveat, accepted deliberately (same call Pufferfish/Petal
 * made): the captured region reads live chunk sections, so a block that
 * changes mid-compute can leave one stale node in a path — the mob corrects
 * on its next repath. A world mutation from the worker is impossible; the
 * compute only reads. Known quirk: with {@code /debug} profiling active,
 * off-thread computes may log profiler-thread warnings.
 *
 * <p>The {@code active} flag is owned by the coexistence resolution
 * ({@code WeftModules}); R6: deactivation closes the worker pool so a
 * yielded module leaves zero residue.
 */
public final class PathfindingHooks {

    private PathfindingHooks() {}

    private static volatile boolean active;
    private static volatile PathService service;

    private static final LongAdder submitted = new LongAdder();
    private static final LongAdder filled = new LongAdder();
    private static final LongAdder noPath = new LongAdder();

    /** RFC-0003 R2 runtime applied-check for the fail-soft mixin. */
    public static boolean hooksApplied() {
        return AsyncPathMarker.class.isAssignableFrom(PathNavigation.class);
    }

    /** Owned by the WeftModules coexistence resolution. */
    public static synchronized void setActive(boolean value) {
        active = value;
        if (value && service == null) {
            service = new PathService("pathfinding", WeftConfig.PATHFINDING_THREADS,
                    (key, task) -> WeftMod.postToOwner(task));
        } else if (!value && service != null) {
            service.close();
            service = null;
        }
    }

    /** Server stop: drop workers regardless of module state. */
    public static synchronized void shutdown() {
        if (service != null) {
            service.close();
            service = null;
        }
    }

    /**
     * Called (server thread) from the wrapped {@code PathFinder.findPath}
     * call site inside {@code PathNavigation.createPath}. Inactive: runs
     * vanilla synchronously. Active: submits the vanilla compute to a path
     * worker and immediately returns a pending {@link AsyncPath} that
     * continues the mob's current path until the result lands.
     */
    public static Path findPathMaybeAsync(Mob mob, Set<BlockPos> targets, Path currentPath,
                                          Supplier<Path> vanillaCompute) {
        PathService s = service;
        if (!active || s == null || targets.isEmpty()) {
            return vanillaCompute.get();
        }
        AsyncPath pending = new AsyncPath(targets.iterator().next(), currentPath,
                mob.blockPosition());
        submitted.increment();
        s.submitTask(mob.getId(), vanillaCompute::get, result -> {
            (result == null ? noPath : filled).increment();
            pending.fill(result);
        });
        return pending;
    }

    /** Requests routed off-thread since boot (WS-8: engagement/vacuous-pass check). */
    public static long submittedCount() {
        return submitted.sum();
    }

    /** Extra detail for the posture report / {@code /weft status} (R5). */
    public static String statusDetail() {
        PathService s = service;
        long total = submitted.sum();
        if (s == null || total == 0) {
            return "no pathfinding requests yet";
        }
        return String.format(
                "%d requests off-thread (%d filled, %d no-path, %d coalesced), last compute %.0f us, queue %d",
                total, filled.sum(), noPath.sum(), s.coalescedCount(),
                s.lastComputeNanos() / 1e3, s.queueDepth());
    }
}
