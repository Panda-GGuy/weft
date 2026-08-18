package dev.weft.neoforge.regiontick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;

/**
 * RFC-0006 hazard 10: {@code CollectingNeighborUpdater} keeps a per-level
 * update-chain stack, so block writes from parallel region buckets would
 * interleave chains. This delegator routes the server thread to the level's
 * original updater and each region worker to its own thread-local instance —
 * an update chain always runs to completion within the thread that started
 * it, so per-chain semantics are exactly vanilla's.
 *
 * <p>Installed by {@code LevelParallelSafetyMixin} on server levels only.
 */
public final class WeftThreadAwareNeighborUpdater implements NeighborUpdater {

    private final Level level;
    private final NeighborUpdater mainThreadUpdater;
    private final ThreadLocal<NeighborUpdater> workerUpdater;

    public WeftThreadAwareNeighborUpdater(Level level, NeighborUpdater mainThreadUpdater) {
        this.level = level;
        this.mainThreadUpdater = mainThreadUpdater;
        this.workerUpdater = ThreadLocal.withInitial(() -> new CollectingNeighborUpdater(
                level, ((ServerLevel) level).getServer().getMaxChainedNeighborUpdates()));
    }

    private NeighborUpdater delegate() {
        return ParallelAccess.isRegionWorker() ? workerUpdater.get() : mainThreadUpdater;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos,
                            int flags, int recursionLeft) {
        delegate().shapeUpdate(direction, state, pos, neighborPos, flags, recursionLeft);
    }

    @Override
    public void neighborChanged(BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        delegate().neighborChanged(pos, neighborBlock, neighborPos);
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        delegate().neighborChanged(state, pos, neighborBlock, neighborPos, movedByPiston);
    }
}
