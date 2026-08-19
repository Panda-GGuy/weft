package dev.weft.neoforge.observability;

import java.util.List;
import java.util.Map;

/**
 * Everything the scrape needs that only the server thread may read
 * (RFC-0009 §9.1), captured as one immutable object.
 *
 * <p><b>Why a snapshot rather than direct reads.</b> The live
 * {@code RegionManager} maps are plain collections mutated on the server thread
 * between ticks and at INGEST (RFC-0007 §3.1); walking them from the scrape
 * thread is a data race, and "it worked in testing" is how that class of bug
 * ships. The same applies to iterating {@code ServerLevel}s and to the module
 * posture table, which resolves against {@code ModList}. So the server thread
 * builds this, publishes it to one volatile field, and the scrape reads the
 * field.
 *
 * <p><b>Cadence: once a second</b>, not once a tick. Building it is O(levels +
 * regions + categories), and the scrape interval it feeds is ten seconds — a
 * per-tick rebuild would be twenty times the work for data nobody reads that
 * often. The cost is up to one second of staleness in the topology and service
 * gauges, well inside the resolution a 10-second sample has anyway.
 *
 * <p>Everything else the exporter needs is already safe from any thread and is
 * read directly at scrape time: {@code TickProfiler.snapshotWindow()},
 * {@code LegacyLane.costByModNanos()}, the scheduler's {@code ForkJoinPool}
 * statistics, and the JVM MX beans.
 */
public record TelemetrySnapshot(long tick, List<LevelTopology> levels,
                                List<ModuleState> modules, Map<String, Long> phaseNanos,
                                long regionMerges, long regionSplits,
                                long ownedSerialSections, long ownedParallelSections,
                                long mailRoutedToRegion, long mailInlineFallback,
                                long mailDrained, long mailFlushed,
                                long legacyExtractedEntities, long legacyExtractedBlockEntities,
                                int entityBuckets, int blockEntityBuckets,
                                int blockEntitiesTicking) {

    /** Empty snapshot: what the scrape sees before the first tick boundary. */
    public static final TelemetrySnapshot EMPTY = new TelemetrySnapshot(
            0L, List.of(), List.of(), Map.of(),
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0);

    /**
     * One level's topology and service state.
     *
     * @param levelId            dimension id, the {@code level} label
     * @param regions            live region count
     * @param chunks             loaded chunks mapped to a region
     * @param largestRegion      chunks in the biggest region — the
     *                           hottest-region-share denominator
     * @param regionChunkCounts  chunks per region, for the distribution histogram
     * @param entitiesByCategory mob-category name to tracked count. Category, not
     *                           entity type: {@code EntityCensus} tracks by
     *                           {@code MobCategory} because that is what the
     *                           mobcap is expressed in, and a per-type gauge would
     *                           mean a full entity walk per scrape
     * @param spawn              spawn-density counters, or null when that module
     *                           is inactive (absent, not zero — RFC-0009 §4)
     */
    public record LevelTopology(String levelId, int regions, int chunks, int largestRegion,
                                List<Integer> regionChunkCounts,
                                Map<String, Integer> entitiesByCategory,
                                SpawnCounters spawn) {}

    /** The subset of {@code WeftServices.SpawnStats} that belongs on the wire. */
    public record SpawnCounters(long authoritativeTicks, long fallbackTicks, long parityTicks,
                                long parityMismatchTicks, long serviceFailures,
                                boolean latchedOff, long censusTracked, long censusDrift,
                                long censusReconciles, long captureNanos, long buildNanos,
                                long computeNanos) {}

    /**
     * One module's resolved posture.
     *
     * @param module the RFC-0003 module id
     * @param state  the five-value collapse the R5 table prints, lower-cased —
     *               taken from the same function {@code /weft status} renders, so
     *               the metric, the table and the event cannot disagree
     */
    public record ModuleState(String module, String state) {}
}
