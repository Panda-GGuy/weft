package dev.weft.api.region;

/**
 * The scheduler mods use instead of assuming "the main thread".
 *
 * <p>Vanilla/NeoForge idiom {@code server.execute(...)} maps to
 * {@link #runOnOwner} — "run this where the state I'm touching lives".
 */
public interface RegionScheduler {

    /**
     * Run a task on whichever worker owns the chunk containing
     * ({@code blockX >> 4}, {@code blockZ >> 4}) at execution time.
     * Executes in the next available phase window for that owner.
     */
    void runOnOwner(int blockX, int blockZ, Runnable task);

    /** Run on the global lane (player list, scoreboards, time/weather...). */
    void runGlobal(Runnable task);

    /**
     * Run after {@code delayTicks} full pipeline ticks, on the owner of the
     * given position (re-resolved at fire time; regions move).
     */
    void runDelayed(int blockX, int blockZ, int delayTicks, Runnable task);

    /** True if the current thread currently owns the given block position. */
    boolean isOwnedByCurrentThread(int blockX, int blockZ);
}
