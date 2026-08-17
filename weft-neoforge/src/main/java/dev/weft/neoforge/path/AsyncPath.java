package dev.weft.neoforge.path;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Path} whose nodes are still being computed off-thread (WS-2,
 * RFC-0002). Returned synchronously from the wrapped {@code
 * PathFinder.findPath} call so every vanilla caller sees a normal, non-null
 * path; all reads delegate to {@link #current}:
 *
 * <ul>
 * <li><b>Pending, mob had a live path:</b> delegates to that path — the mob
 *     keeps following its old route until the new one lands (no stutter on
 *     the chase-repath cycle).</li>
 * <li><b>Pending, no previous path:</b> delegates to a one-node stub at the
 *     mob's own position — the mob holds still for the tick or two the
 *     compute takes.</li>
 * <li><b>Filled:</b> delegates to the computed result, or to an empty
 *     (immediately done) path when the compute found none — navigation
 *     stops naturally and the goal re-plans, exactly as after a vanilla
 *     null path.</li>
 * </ul>
 *
 * <p>{@link #fill} must be called on the server thread at a tick boundary
 * (the service routes it through the Weft scheduler's mailbox); every other
 * method is called by vanilla navigation code on the server thread only.
 * {@link #sameAs} is identity on purpose: {@code moveTo} uses it to decide
 * whether to adopt the new path object, and a pending path that compares
 * equal to its own continuation would never be adopted — the fill would go
 * nowhere.
 */
public final class AsyncPath extends Path {

    private volatile Path current;
    private volatile boolean processed;

    AsyncPath(BlockPos target, @Nullable Path continuation, BlockPos mobPos) {
        super(stubNodes(mobPos), target, false);
        this.current = continuation != null && !continuation.isDone()
                ? continuation : new Path(stubNodes(mobPos), target, false);
    }

    private static List<Node> stubNodes(BlockPos pos) {
        List<Node> nodes = new ArrayList<>(1);
        nodes.add(new Node(pos.getX(), pos.getY(), pos.getZ()));
        return nodes;
    }

    /** Server thread, tick boundary. Null = the compute found no path. */
    void fill(@Nullable Path result) {
        this.current = result != null
                ? result : new Path(new ArrayList<>(), getTarget(), false);
        this.processed = true;
    }

    public boolean isProcessed() {
        return processed;
    }

    // --- full delegation to the current backing path ---

    @Override
    public void advance() {
        current.advance();
    }

    @Override
    public boolean notStarted() {
        return current.notStarted();
    }

    @Override
    public boolean isDone() {
        return current.isDone();
    }

    @Override
    @Nullable
    public Node getEndNode() {
        return current.getEndNode();
    }

    @Override
    public Node getNode(int index) {
        return current.getNode(index);
    }

    @Override
    public void truncateNodes(int length) {
        current.truncateNodes(length);
    }

    @Override
    public void replaceNode(int index, Node node) {
        current.replaceNode(index, node);
    }

    @Override
    public int getNodeCount() {
        return current.getNodeCount();
    }

    @Override
    public int getNextNodeIndex() {
        return current.getNextNodeIndex();
    }

    @Override
    public void setNextNodeIndex(int index) {
        current.setNextNodeIndex(index);
    }

    @Override
    public Vec3 getEntityPosAtNode(Entity entity, int index) {
        return current.getEntityPosAtNode(entity, index);
    }

    @Override
    public BlockPos getNodePos(int index) {
        return current.getNodePos(index);
    }

    @Override
    public Vec3 getNextEntityPos(Entity entity) {
        return current.getNextEntityPos(entity);
    }

    @Override
    public BlockPos getNextNodePos() {
        return current.getNextNodePos();
    }

    @Override
    public Node getNextNode() {
        return current.getNextNode();
    }

    @Override
    @Nullable
    public Node getPreviousNode() {
        return current.getPreviousNode();
    }

    @Override
    public boolean sameAs(@Nullable Path other) {
        return other == this; // identity: see class javadoc
    }

    @Override
    public boolean canReach() {
        // Optimistic while pending (goals that test reachability at start
        // would otherwise never begin); truthful once processed.
        return !processed || current.canReach();
    }

    @Override
    public float getDistToTarget() {
        return current.getDistToTarget();
    }

    @Override
    public Path copy() {
        return this; // debug-only caller; splitting state would desync fills
    }

    @Override
    public String toString() {
        return "AsyncPath(processed=" + processed + ", " + current + ")";
    }
}
