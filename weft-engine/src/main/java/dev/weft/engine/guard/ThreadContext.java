package dev.weft.engine.guard;

/**
 * What the current thread is allowed to touch right now. Set by the scheduler
 * around every unit of work; consulted by {@link WeftGuards} on every guarded
 * mutation path.
 */
public final class ThreadContext {

    public enum Kind { REGION, SHARD, GRAPH, GLOBAL, LEGACY, NONE }

    private static final ThreadLocal<ThreadContext> CURRENT =
            ThreadLocal.withInitial(() -> new ThreadContext(Kind.NONE, -1));

    private Kind kind;
    private long ownerId;

    private ThreadContext(Kind kind, long ownerId) {
        this.kind = kind;
        this.ownerId = ownerId;
    }

    public static ThreadContext current() {
        return CURRENT.get();
    }

    public static void enter(Kind kind, long ownerId) {
        ThreadContext c = CURRENT.get();
        c.kind = kind;
        c.ownerId = ownerId;
    }

    public static void exit() {
        enter(Kind.NONE, -1);
    }

    public Kind kind() {
        return kind;
    }

    public long ownerId() {
        return ownerId;
    }
}
