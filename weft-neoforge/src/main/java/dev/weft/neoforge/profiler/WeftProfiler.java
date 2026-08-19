package dev.weft.neoforge.profiler;

import dev.weft.engine.telemetry.RegionizabilityAnalyzer;
import dev.weft.engine.telemetry.ReportFormatter;
import dev.weft.engine.telemetry.TickProfiler;
import dev.weft.engine.telemetry.TickSample;
import dev.weft.neoforge.WeftConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * P0 profiler singleton (RFC-0001 §9.1). Mixin hooks feed timings in; the
 * report answers "how much of this pack's tick would Weft parallelize."
 * Runs on stock servers and the single-player integrated server alike —
 * everything records on the server thread.
 *
 * <p>Hardening notes: the timing stack is cleared at every tick boundary so
 * an exception between a HEAD and RETURN hook (entity crash caught by the
 * loader, chunk unload mid-tick) can only skew attribution within that one
 * tick, never leak permanently. All hooks early-out when profiling is
 * toggled off ({@code /weft profile off}), leaving two static reads per
 * hook as the disabled-mode cost.
 */
public final class WeftProfiler {

    private static final WeftProfiler INSTANCE = new WeftProfiler();

    /** Recreated when the configured window size changes; volatile because
     *  report generation may happen from a command/console thread. */
    private volatile TickProfiler profiler;
    /**
     * One frame per open HEAD/RETURN pair: {@code [startNanos, aiAccumNanos,
     * aiSliceStartNanos]}. The AI slice ({@code Mob.serverAiStep}) accumulates
     * into the innermost open frame, so a passenger mob's AI time lands in its
     * vehicle's sample — consistent with where its total cost already lands.
     */
    private final ArrayDeque<long[]> nanoStack = new ArrayDeque<>();
    private long tickCounter;

    /**
     * Thread confinement (learned the hard way): in single player the CLIENT
     * level ticks its block entities through the same
     * {@code LevelChunk$BoundTickingBlockEntity} wrapper our mixin hooks, so
     * hooks fire on the client thread too. Concurrent access corrupted the
     * deque (AIOOBE crash) and polluted samples with client-side timings.
     * Only the thread that runs {@link #onTickStart} (the server thread, via
     * MinecraftServerMixin) may record; all other threads no-op.
     */
    private volatile Thread serverThread;

    private WeftProfiler() {}

    public static WeftProfiler get() {
        return INSTANCE;
    }

    /** Called from MinecraftServerMixin at the top of every server tick. */
    public void onTickStart() {
        serverThread = Thread.currentThread();
        tickCounter++;
        // Leak protection: any start times stranded by an exception path
        // between HEAD and RETURN hooks die with the tick they belong to.
        nanoStack.clear();
        if (!WeftConfig.PROFILING_ENABLED) {
            return;
        }
        TickProfiler p = profiler;
        if (p == null || p.windowSize() != WeftConfig.PROFILE_WINDOW_TICKS) {
            p = new TickProfiler(WeftConfig.PROFILE_WINDOW_TICKS);
            profiler = p;
        }
        p.tickBoundary(tickCounter, System.nanoTime());
    }

    // --- timing hooks (server thread only; stack handles nesting) ---

    public void push() {
        if (!WeftConfig.PROFILING_ENABLED || Thread.currentThread() != serverThread) {
            return;
        }
        nanoStack.push(new long[]{System.nanoTime(), 0L, 0L});
    }

    /**
     * Start of the AI slice inside a mob tick ({@code Mob.serverAiStep} HEAD).
     * Tolerates having no open frame (profiling toggled on mid-tick, or an AI
     * step reached outside a hooked entity tick) by doing nothing.
     */
    public void aiSliceStart() {
        if (!WeftConfig.PROFILING_ENABLED || Thread.currentThread() != serverThread) {
            return;
        }
        long[] frame = nanoStack.peek();
        if (frame != null) {
            frame[2] = System.nanoTime();
        }
    }

    /** End of the AI slice; accumulates into the innermost open frame. */
    public void aiSliceEnd() {
        if (!WeftConfig.PROFILING_ENABLED || Thread.currentThread() != serverThread) {
            return;
        }
        long[] frame = nanoStack.peek();
        if (frame != null && frame[2] != 0L) {
            frame[1] += System.nanoTime() - frame[2];
            frame[2] = 0L;
        }
    }

    public void popEntity(String typeId, long chunkKey) {
        pop(TickSample.Source.ENTITY, typeId, chunkKey, 1);
    }

    /** Entity variant carrying the WS-1 projected activation interval. */
    public void popEntity(String typeId, long chunkKey, int aiInterval) {
        pop(TickSample.Source.ENTITY, typeId, chunkKey, aiInterval);
    }

    public void popBlockEntity(String typeId, long chunkKey) {
        pop(TickSample.Source.BLOCK_ENTITY, typeId, chunkKey, 1);
    }

    /** Work with no spatial home — serial under Weft (the Amdahl bucket). */
    public void popGlobal(String typeId) {
        pop(TickSample.Source.GLOBAL, typeId, TickSample.NO_CHUNK, 1);
    }

    private void pop(TickSample.Source source, String typeId, long chunkKey, int aiInterval) {
        if (!WeftConfig.PROFILING_ENABLED || Thread.currentThread() != serverThread) {
            return;
        }
        // poll() is null on empty: tolerates a toggle-on between HEAD and
        // RETURN of the same tickable (push skipped, pop not).
        long[] frame = nanoStack.poll();
        TickProfiler p = profiler;
        if (frame != null && p != null) {
            p.record(source, typeId, chunkKey, System.nanoTime() - frame[0], aiInterval, frame[1]);
        }
    }

    // --- reporting ---

    /** Build the report over the current window. Safe from any thread. */
    public String buildReport() {
        TickProfiler p = profiler;
        List<TickProfiler.TickRecord> window = p == null ? List.of() : p.snapshotWindow();
        String prefix = WeftConfig.PROFILING_ENABLED
                ? "" : "(profiling is OFF - data below is stale; /weft profile on)\n";
        if (window.isEmpty()) {
            return WeftConfig.PROFILING_ENABLED
                    ? "Weft: no completed ticks in the window yet - let the server run a few seconds."
                    : "Weft: profiling is OFF and no data was recorded. /weft profile on";
        }
        List<TickSample> all = new ArrayList<>();
        for (TickProfiler.TickRecord rec : window) {
            all.addAll(rec.samples());
        }
        RegionizabilityAnalyzer analyzer = new RegionizabilityAnalyzer(
                WeftConfig.MERGE_DISTANCE, WeftConfig.SPEEDUP_WORKER_COUNTS, WeftConfig.REPORT_TOP_TYPES);
        RegionizabilityAnalyzer.Report report = analyzer.analyze(all);
        return prefix + ReportFormatter.format(report, window.size());
    }

    /**
     * Raw completed-tick window, oldest first (WS-8 benchmark harness: the
     * load generator sums per-{@link TickSample.Source} nanos over a tick
     * range instead of going through the report). Safe from any thread.
     */
    public List<TickProfiler.TickRecord> snapshotWindow() {
        TickProfiler p = profiler;
        return p == null ? List.of() : p.snapshotWindow();
    }

    /** Write the report next to the server/world files; returns the path. */
    public Path writeReportFile(Path gameDir) throws IOException {
        Path out = gameDir.resolve("weft-report.txt");
        Files.writeString(out, buildReport());
        return out;
    }

    /**
     * The current window as a JSON document (WS-7, RFC-0009 §6). Safe from any
     * thread, like {@link #buildReport()}.
     *
     * <p>Additive: {@code weft-report.txt} is untouched and remains the human
     * surface. This exists because a formatted table is useless for trend
     * analysis, alerting or correlating a regression against a deployment.
     */
    public String buildReportJson() {
        TickProfiler p = profiler;
        List<TickProfiler.TickRecord> window = p == null ? List.of() : p.snapshotWindow();
        int windowSize = p == null ? WeftConfig.PROFILE_WINDOW_TICKS : p.windowSize();
        if (window.isEmpty()) {
            // Absent, not zero: a document full of zeros would read as a
            // measurement of an idle server rather than as no measurement.
            return dev.weft.engine.telemetry.export.ProfilerSnapshotJson.render(
                    null, 0, windowSize, WeftConfig.PROFILING_ENABLED);
        }
        List<TickSample> all = new ArrayList<>();
        for (TickProfiler.TickRecord rec : window) {
            all.addAll(rec.samples());
        }
        RegionizabilityAnalyzer.Report report = new RegionizabilityAnalyzer(
                WeftConfig.MERGE_DISTANCE, WeftConfig.SPEEDUP_WORKER_COUNTS,
                WeftConfig.REPORT_TOP_TYPES).analyze(all);
        return dev.weft.engine.telemetry.export.ProfilerSnapshotJson.render(
                report, window.size(), windowSize, WeftConfig.PROFILING_ENABLED);
    }

    /** Write {@code weft-report.json} beside the text report; returns the path. */
    public Path writeReportJson(Path gameDir) throws IOException {
        Path out = gameDir.resolve("weft-report.json");
        Files.writeString(out, buildReportJson());
        return out;
    }

    public long tickCounter() {
        return tickCounter;
    }
}
