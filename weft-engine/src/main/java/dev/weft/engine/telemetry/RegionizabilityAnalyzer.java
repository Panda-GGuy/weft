package dev.weft.engine.telemetry;

import dev.weft.engine.region.ChunkKey;
import dev.weft.engine.region.Region;
import dev.weft.engine.region.RegionManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Answers the P0 question (RFC-0001 §9.1): if this world were regionized
 * under Weft, how much of the measured tick would parallelize, and what
 * speedup would N workers deliver?
 *
 * <p>Method: replay a tick's spatial samples into a {@link RegionManager}
 * partition (same merge rules the real engine uses), sum cost per region,
 * then compute the makespan of an LPT (longest-processing-time) schedule of
 * region costs onto k workers. Non-spatial (GLOBAL) cost is the serial
 * fraction — Amdahl's honest denominator.
 */
public final class RegionizabilityAnalyzer {

    public record RegionCost(long regionId, int chunkCount, long nanos) {}

    public record TypeCost(String typeId, long nanos, long count) {}

    /**
     * @param totalNanos       sum of all sample costs (not wall clock)
     * @param spatialNanos     cost attributable to a chunk (parallelizable pool)
     * @param globalNanos      cost with no spatial home (serial under Weft)
     * @param regions          per-hypothetical-region cost, largest first
     * @param topTypes         most expensive sources, largest first
     * @param speedupByWorkers estimated tick speedup at k workers (LPT makespan
     *                         + serial fraction), keyed by worker count
     * @param throttleableNanos entity cost carrying a WS-1 interval > 1 (the
     *                          activation tiers would throttle it where it stood)
     * @param activationSavedNanos projected WS-1 saving: each throttleable
     *                          sample's cost scaled by its skipped-tick share,
     *                          {@code nanos * (interval - 1) / interval} — an
     *                          upper bound, since only the AI portion of an
     *                          entity tick is actually skipped
     */
    public record Report(long totalNanos, long spatialNanos, long globalNanos,
                         List<RegionCost> regions, List<TypeCost> topTypes,
                         Map<Integer, Double> speedupByWorkers,
                         long throttleableNanos, long activationSavedNanos) {}

    private final int mergeDistance;
    private final int[] workerCounts;
    private final int topN;

    public RegionizabilityAnalyzer(int mergeDistance, int[] workerCounts, int topN) {
        this.mergeDistance = mergeDistance;
        this.workerCounts = workerCounts.clone();
        this.topN = topN;
    }

    public Report analyze(List<TickSample> samples) {
        long spatial = 0;
        long global = 0;
        long throttleable = 0;
        long activationSaved = 0;

        // Partition the sampled chunks exactly as the engine would.
        RegionManager rm = new RegionManager(mergeDistance, 0L);
        Map<Long, Long> costByChunk = new HashMap<>();
        Map<String, long[]> byType = new HashMap<>(); // {nanos, count}

        for (TickSample s : samples) {
            byType.computeIfAbsent(s.typeId(), k -> new long[2]);
            long[] agg = byType.get(s.typeId());
            agg[0] += s.nanos();
            agg[1]++;
            if (s.aiInterval() > 1) {
                throttleable += s.nanos();
                activationSaved += s.nanos() * (s.aiInterval() - 1L) / s.aiInterval();
            }
            if (s.spatial()) {
                spatial += s.nanos();
                costByChunk.merge(s.chunkKey(), s.nanos(), Long::sum);
                rm.addChunk(ChunkKey.x(s.chunkKey()), ChunkKey.z(s.chunkKey()));
            } else {
                global += s.nanos();
            }
        }

        // Sum cost per hypothetical region.
        Map<Long, long[]> regionAgg = new HashMap<>(); // regionId -> {nanos, chunks}
        for (Map.Entry<Long, Long> e : costByChunk.entrySet()) {
            Region r = rm.regionAt(ChunkKey.x(e.getKey()), ChunkKey.z(e.getKey()));
            long[] agg = regionAgg.computeIfAbsent(r.id(), k -> new long[2]);
            agg[0] += e.getValue();
        }
        for (Region r : rm.all()) {
            long[] agg = regionAgg.computeIfAbsent(r.id(), k -> new long[2]);
            agg[1] = r.chunks().size();
        }

        List<RegionCost> regions = new ArrayList<>();
        regionAgg.forEach((id, agg) -> regions.add(new RegionCost(id, (int) agg[1], agg[0])));
        regions.sort(Comparator.comparingLong(RegionCost::nanos).reversed());

        List<TypeCost> topTypes = new ArrayList<>();
        byType.forEach((id, agg) -> topTypes.add(new TypeCost(id, agg[0], agg[1])));
        topTypes.sort(Comparator.comparingLong(TypeCost::nanos).reversed());
        List<TypeCost> trimmedTypes = topTypes.size() > topN
                ? List.copyOf(topTypes.subList(0, topN)) : List.copyOf(topTypes);

        long total = spatial + global;
        Map<Integer, Double> speedups = new HashMap<>();
        for (int k : workerCounts) {
            long makespan = lptMakespan(regions, k);
            long parallelTickCost = global + makespan;
            speedups.put(k, parallelTickCost == 0 ? 1.0 : (double) total / parallelTickCost);
        }

        return new Report(total, spatial, global, List.copyOf(regions), trimmedTypes,
                Map.copyOf(speedups), throttleable, activationSaved);
    }

    /** Makespan of scheduling region costs onto k workers, LPT heuristic. */
    static long lptMakespan(List<RegionCost> regionsLargestFirst, int k) {
        if (k < 1) {
            throw new IllegalArgumentException("workers must be >= 1");
        }
        PriorityQueue<Long> workers = new PriorityQueue<>();
        for (int i = 0; i < k; i++) {
            workers.add(0L);
        }
        for (RegionCost r : regionsLargestFirst) {
            workers.add(workers.poll() + r.nanos());
        }
        long max = 0;
        for (long w : workers) {
            max = Math.max(max, w);
        }
        return max;
    }
}
