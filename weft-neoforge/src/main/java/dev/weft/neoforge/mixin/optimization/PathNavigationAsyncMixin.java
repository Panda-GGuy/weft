package dev.weft.neoforge.mixin.optimization;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.weft.neoforge.path.AsyncPathMarker;
import dev.weft.neoforge.path.PathfindingHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/**
 * WS-2 (RFC-0002): make {@code PathFinder.findPath} — the expensive A*
 * inside {@code PathNavigation.createPath} — run on a Weft path worker
 * instead of the server thread. Everything around it is untouched: vanilla
 * still captures the {@code PathNavigationRegion} on the server thread
 * (that capture is the snapshot the worker reads), and the returned
 * {@code AsyncPath} keeps every caller's control flow identical until the
 * computed nodes land at the next tick boundary.
 *
 * <p>Covers every navigation type (ground/flying/water/wall-climbing) via
 * the shared base-class method. Fail-soft per RFC-0003 R2: lives in the
 * {@code required: false} optimizations config with {@code defaultRequire: 0};
 * {@link AsyncPathMarker} is the runtime applied-check, and a failed
 * application leaves vanilla-synchronous pathfinding, never a broken hybrid.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationAsyncMixin implements AsyncPathMarker {

    @Shadow
    @Final
    protected Mob mob;

    @Shadow
    protected Path path;

    @WrapOperation(
            method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/pathfinder/PathFinder;findPath("
                            + "Lnet/minecraft/world/level/PathNavigationRegion;"
                            + "Lnet/minecraft/world/entity/Mob;"
                            + "Ljava/util/Set;FIF)"
                            + "Lnet/minecraft/world/level/pathfinder/Path;"))
    private Path weft$asyncFindPath(PathFinder finder, PathNavigationRegion region,
                                    Mob targetMob, Set<BlockPos> targets, float followRange,
                                    int accuracy, float maxVisitedMultiplier,
                                    Operation<Path> original) {
        return PathfindingHooks.findPathMaybeAsync(this.mob, targets, this.path,
                () -> original.call(finder, region, targetMob, targets, followRange,
                        accuracy, maxVisitedMultiplier));
    }
}
