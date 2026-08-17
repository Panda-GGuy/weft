package dev.weft.engine.telemetry;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects {@link TickSample}s for the current tick and keeps a rolling
 * window of completed ticks (P0 profiler core).
 *
 * <p>Threading: on a stock (unmodified) server all recording happens on the
 * single server thread, so this class is deliberately unsynchronized on the
 * hot path. {@link #snapshotWindow()} copies under a lock so a report can be
 * generated from another thread (e.g. a command source) safely.
 */
public final class TickProfiler {

    /** One completed tick's samples plus its wall-clock duration. */
    public record TickRecord(long tickNumber, long tickNanos, List<TickSample> samples) {}

    private final int windowSize;
    private final Object windowLock = new Object();
    private final ArrayList<TickRecord> window = new ArrayList<>();

    private List<TickSample> current = new ArrayList<>(1024);
    private long currentTick = -1;
    private long tickStartNanos;

    public TickProfiler(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("windowSize must be >= 1");
        }
        this.windowSize = windowSize;
    }

    /** Call at the top of each server tick. Finalizes the previous tick. */
    public void tickBoundary(long tickNumber, long nowNanos) {
        if (currentTick >= 0) {
            TickRecord done = new TickRecord(currentTick, nowNanos - tickStartNanos, current);
            synchronized (windowLock) {
                window.add(done);
                if (window.size() > windowSize) {
                    window.remove(0);
                }
            }
            current = new ArrayList<>(Math.max(1024, current.size()));
        }
        currentTick = tickNumber;
        tickStartNanos = nowNanos;
    }

    /** Record one unit of work in the current tick. Server thread only. */
    public void record(TickSample sample) {
        if (currentTick >= 0) {
            current.add(sample);
        }
    }

    public void record(TickSample.Source source, String typeId, long chunkKey, long nanos) {
        record(new TickSample(source, typeId, chunkKey, nanos));
    }

    /** Copy of the completed-tick window, oldest first. Safe from any thread. */
    public List<TickRecord> snapshotWindow() {
        synchronized (windowLock) {
            return List.copyOf(window);
        }
    }

    public int windowSize() {
        return windowSize;
    }
}
