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
 */
public final class WeftProfiler {

    private static final WeftProfiler INSTANCE = new WeftProfiler();

    private final TickProfiler profiler = new TickProfiler(WeftConfig.PROFILE_WINDOW_TICKS);
    private final ArrayDeque<Long> nanoStack = new ArrayDeque<>();
    private long tickCounter;

    private WeftProfiler() {}

    public static WeftProfiler get() {
        return INSTANCE;
    }

    /** Called from MinecraftServerMixin at the top of every server tick. */
    public void onTickStart() {
        tickCounter++;
        profiler.tickBoundary(tickCounter, System.nanoTime());
    }

    // --- timing hooks (server thread only; stack handles nesting) ---

    public void push() {
        nanoStack.push(System.nanoTime());
    }

    public void popEntity(String typeId, long chunkKey) {
        pop(TickSample.Source.ENTITY, typeId, chunkKey);
    }

    public void popBlockEntity(String typeId, long chunkKey) {
        pop(TickSample.Source.BLOCK_ENTITY, typeId, chunkKey);
    }

    private void pop(TickSample.Source source, String typeId, long chunkKey) {
        Long start = nanoStack.poll();
        if (start != null) {
            profiler.record(source, typeId, chunkKey, System.nanoTime() - start);
        }
    }

    // --- reporting ---

    /** Build the report over the current window. Safe from any thread. */
    public String buildReport() {
        List<TickProfiler.TickRecord> window = profiler.snapshotWindow();
        if (window.isEmpty()) {
            return "Weft: no completed ticks in the window yet — let the server run a few seconds.";
        }
        List<TickSample> all = new ArrayList<>();
        for (TickProfiler.TickRecord rec : window) {
            all.addAll(rec.samples());
        }
        RegionizabilityAnalyzer analyzer = new RegionizabilityAnalyzer(
                WeftConfig.MERGE_DISTANCE, WeftConfig.SPEEDUP_WORKER_COUNTS, WeftConfig.REPORT_TOP_TYPES);
        RegionizabilityAnalyzer.Report report = analyzer.analyze(all);
        return ReportFormatter.format(report, window.size());
    }

    /** Write the report next to the server/world files; returns the path. */
    public Path writeReportFile(Path gameDir) throws IOException {
        Path out = gameDir.resolve("weft-report.txt");
        Files.writeString(out, buildReport());
        return out;
    }

    public long tickCounter() {
        return tickCounter;
    }
}
