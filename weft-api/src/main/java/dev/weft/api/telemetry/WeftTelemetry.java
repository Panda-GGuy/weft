package dev.weft.api.telemetry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * The telemetry publish registry (RFC-0009 §11, shape proposed by
 * RESEARCH-0003 §4.1). Weft modules — and in principle any other mod — publish
 * named series here; WS-7's exporter is the only consumer, and it is a pure
 * reader.
 *
 * <p>Two ways in, and the second is the common one:
 *
 * <ul>
 *   <li><b>Pushed instruments</b> ({@link #counter}, {@link #gauge},
 *       {@link #histogram}) for quantities that genuinely accumulate here and
 *       nowhere else: per-tick durations, guard trips, the exporter's own
 *       health counters.</li>
 *   <li><b>Pulled {@linkplain Collector collectors}</b> ({@link #register})
 *       for everything that Weft <em>already</em> measures. Most of WS-7 is
 *       this: the exporter reads {@code LegacyLane.costByModNanos()},
 *       {@code SpawnStats}, {@code EntityCensus} and friends at scrape time and
 *       formats them. Nothing is duplicated and the tick path pays nothing.</li>
 * </ul>
 *
 * <p><b>R6 — yield must be total.</b> Every publish point begins with one
 * {@code volatile boolean} read, the same disabled-mode cost the P0 profiler
 * hooks already pay. With the module inactive, {@link #enabled()} is false, no
 * child series is ever created, and no collector is ever invoked. Callers must
 * check {@link #enabled()} <em>before</em> doing work to compute a value —
 * a {@code System.nanoTime()} call to feed a histogram that will discard it is
 * exactly the residue R6 forbids.
 *
 * <p><b>Thread safety.</b> Publishing is safe from any thread. Reading is
 * {@link #collectInto}, on the scrape thread; see {@link Collector} for the
 * contract that makes that sound.
 *
 * <p>This class is deliberately in {@code weft-api}: it is the seam modules
 * publish through, so no module needs to know the exporter exists (RFC-0003
 * R1), and the exporter needs to know nothing about the modules.
 */
public final class WeftTelemetry {

    private WeftTelemetry() {}

    /**
     * Duration buckets for tick-scale work, in seconds: 0.5 ms to 5 s. The
     * 50 ms tick budget falls between the 0.05 and 0.1 bounds, so "did this
     * exceed a tick" is a bucket boundary rather than an interpolation.
     */
    public static final double[] TICK_SECONDS = {
        0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0
    };

    /**
     * Duration buckets for work well below one tick (barrier waits, service
     * latencies): 50 µs to 1 s. A barrier wait bucketed with {@link
     * #TICK_SECONDS} would land everything in the first bucket and report
     * nothing.
     */
    public static final double[] SUBTICK_SECONDS = {
        0.00005, 0.0001, 0.00025, 0.0005, 0.001, 0.0025, 0.005, 0.01, 0.025, 0.05, 0.1, 1.0
    };

    /** Buckets for counts of things (chunks per region), not durations. */
    public static final double[] COUNT_BUCKETS = {
        1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 4096
    };

    /**
     * Per-family retention ceiling. This is a runaway guard, not the
     * cardinality cap: the cap that bounds what goes on the wire is applied at
     * snapshot time by the exporter, which is the only party that knows
     * {@code maxLabelCardinality} and can rank by value (RFC-0009 §7). Holding
     * a few thousand {@code LongAdder}s costs kilobytes; shipping a few
     * thousand series to a Prometheus instance is what actually hurts.
     */
    private static final int MAX_CHILDREN = 4096;

    /**
     * Label-tuple key separator: ASCII US (unit separator). Cannot occur in a
     * mod id or a ResourceLocation, so ("ab","c") and ("a","bc") cannot
     * collide into one series - which plain concatenation would have allowed.
     */
    private static final String SEP = String.valueOf((char) 0x1f);

    /** The label value overflow past {@link #MAX_CHILDREN} folds into. */
    public static final String OTHER = "__other__";

    private static final CopyOnWriteArrayList<Collector> collectors = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, Family> families = new ConcurrentHashMap<>();

    private static volatile boolean enabled;

    /** Owned by the {@code observability} module's coexistence resolution. */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** One volatile read: the whole of the disabled-mode cost. */
    public static boolean enabled() {
        return enabled;
    }

    /** Register a pulled source. Idempotent per instance. */
    public static void register(Collector collector) {
        if (!collectors.contains(collector)) {
            collectors.add(collector);
        }
    }

    public static void unregister(Collector collector) {
        collectors.remove(collector);
    }

    /**
     * Drop every registered collector and zero every accumulated series. Called
     * on server stop: the state these describe is being torn down, and a fresh
     * run must not inherit the previous world's counters.
     *
     * <p><b>Families are zeroed, not removed</b>, and that distinction is
     * load-bearing. Callers are told to look a family up once into a static
     * field, which is the natural way to use this API — so removing the family
     * object would leave every such field pointing at an orphan that accepts
     * increments and appears in no scrape. The failure would be silent, and it
     * would happen on the most ordinary path there is: leaving a single-player
     * world and loading another in the same JVM.
     */
    public static void reset() {
        collectors.clear();
        families.values().forEach(Family::clear);
    }

    /**
     * Read every source into {@code sink} (scrape thread). A collector that
     * throws is reported to {@code onError} and skipped; the rest still
     * render — one broken collector must not cost the whole dashboard.
     */
    public static void collectInto(MetricSink sink, java.util.function.BiConsumer<Collector,
            RuntimeException> onError) {
        for (Family family : families.values()) {
            try {
                family.collect(sink);
            } catch (RuntimeException e) {
                onError.accept(family, e);
            }
        }
        for (Collector collector : collectors) {
            try {
                collector.collect(sink);
            } catch (RuntimeException e) {
                onError.accept(collector, e);
            }
        }
    }

    // --- pushed instruments ---

    public static Counters counter(String name, String help, String... labelNames) {
        return (Counters) families.computeIfAbsent(name,
                n -> new Counters(n, help, labelNames));
    }

    public static Gauges gauge(String name, String help, String... labelNames) {
        return (Gauges) families.computeIfAbsent(name, n -> new Gauges(n, help, labelNames));
    }

    public static Histograms histogram(String name, String help, double[] buckets,
                                       String... labelNames) {
        return (Histograms) families.computeIfAbsent(name,
                n -> new Histograms(n, help, buckets, labelNames));
    }

    /**
     * A family of pushed series sharing a name and label set. Look the family
     * up once into a static field; {@link #keyOf} per publish is a concurrent
     * map get.
     */
    public abstract static sealed class Family implements Collector
            permits Counters, Gauges, Histograms {

        final String name;
        final String help;
        final String[] labelNames;
        final ConcurrentHashMap<String, String[]> keys = new ConcurrentHashMap<>();

        Family(String name, String help, String[] labelNames) {
            this.name = name;
            this.help = help;
            this.labelNames = labelNames.clone();
        }

        public String name() {
            return name;
        }

        /**
         * Drop every series this family holds, keeping the family object itself
         * so that handles cached in static fields stay live across a
         * {@link WeftTelemetry#reset()}.
         */
        abstract void clear();

        /**
         * Key for a label tuple, with the {@link #MAX_CHILDREN} runaway guard.
         * Beyond the ceiling every new tuple folds into {@link #OTHER} rather
         * than growing without bound.
         */
        final String keyOf(String... labelValues) {
            if (labelValues.length != labelNames.length) {
                throw new IllegalArgumentException(name + " expects " + labelNames.length
                        + " label values, got " + labelValues.length);
            }
            String key = String.join(SEP, labelValues);
            if (keys.containsKey(key) || keys.size() < MAX_CHILDREN) {
                keys.putIfAbsent(key, labelValues.clone());
                return key;
            }
            String[] other = new String[labelNames.length];
            Arrays.fill(other, OTHER);
            String otherKey = String.join(SEP, other);
            keys.putIfAbsent(otherKey, other);
            return otherKey;
        }
    }

    /** Monotonic totals accumulated here (guard trips, dropped events). */
    public static final class Counters extends Family {

        private final ConcurrentHashMap<String, LongAdder> values = new ConcurrentHashMap<>();

        Counters(String name, String help, String[] labelNames) {
            super(name, help, labelNames);
        }

        public void inc(String... labelValues) {
            add(1L, labelValues);
        }

        public void add(long delta, String... labelValues) {
            if (!enabled) {
                return;
            }
            values.computeIfAbsent(keyOf(labelValues), k -> new LongAdder()).add(delta);
        }

        @Override
        void clear() {
            values.clear();
            keys.clear();
        }

        @Override
        public void collect(MetricSink sink) {
            values.forEach((key, adder) ->
                    sink.counter(name, help, labelNames, keys.get(key), adder.sum()));
        }
    }

    /** Last-value gauges computed at a tick or section boundary. */
    public static final class Gauges extends Family {

        private final ConcurrentHashMap<String, DoubleAdder> values = new ConcurrentHashMap<>();

        Gauges(String name, String help, String[] labelNames) {
            super(name, help, labelNames);
        }

        public void set(double value, String... labelValues) {
            if (!enabled) {
                return;
            }
            DoubleAdder slot = values.computeIfAbsent(keyOf(labelValues), k -> new DoubleAdder());
            slot.reset();
            slot.add(value);
        }

        @Override
        void clear() {
            values.clear();
            keys.clear();
        }

        @Override
        public void collect(MetricSink sink) {
            values.forEach((key, slot) ->
                    sink.gauge(name, help, labelNames, keys.get(key), slot.sum()));
        }
    }

    /** Cumulative histograms of values observed over time. */
    public static final class Histograms extends Family {

        private final double[] buckets;
        private final ConcurrentHashMap<String, Bins> values = new ConcurrentHashMap<>();

        private static final class Bins {
            final LongAdder[] counts;
            final DoubleAdder sum = new DoubleAdder();
            final LongAdder total = new LongAdder();

            Bins(int n) {
                counts = new LongAdder[n];
                for (int i = 0; i < n; i++) {
                    counts[i] = new LongAdder();
                }
            }
        }

        Histograms(String name, String help, double[] buckets, String[] labelNames) {
            super(name, help, labelNames);
            this.buckets = buckets.clone();
        }

        /** Observe one value, in the family's base unit (seconds, or a count). */
        public void observe(double value, String... labelValues) {
            if (!enabled) {
                return;
            }
            Bins bins = values.computeIfAbsent(keyOf(labelValues), k -> new Bins(buckets.length));
            bins.sum.add(value);
            bins.total.increment();
            // Non-cumulative on the way in; cumulated at collect time. Keeps
            // the hot path at one bucket increment instead of a suffix walk.
            int i = bucketIndexOf(value);
            if (i < buckets.length) {
                bins.counts[i].increment();
            }
        }

        /** Convenience for the overwhelmingly common nanosecond source. */
        public void observeNanos(long nanos, String... labelValues) {
            observe(nanos / 1e9, labelValues);
        }

        private int bucketIndexOf(double value) {
            for (int i = 0; i < buckets.length; i++) {
                if (value <= buckets[i]) {
                    return i;
                }
            }
            return buckets.length;
        }

        @Override
        void clear() {
            values.clear();
            keys.clear();
        }

        @Override
        public void collect(MetricSink sink) {
            values.forEach((key, bins) -> {
                long[] cumulative = new long[buckets.length];
                long running = 0;
                for (int i = 0; i < buckets.length; i++) {
                    running += bins.counts[i].sum();
                    cumulative[i] = running;
                }
                sink.histogram(name, help, labelNames, keys.get(key),
                        buckets, cumulative, bins.sum.sum(), bins.total.sum());
            });
        }
    }

    /** Registered pulled sources, for status reporting. */
    public static List<Collector> registered() {
        return List.copyOf(collectors);
    }

    /** Pushed families, for status reporting and tests. */
    public static Map<String, Family> pushedFamilies() {
        return Map.copyOf(families);
    }

    /** Series names currently held by pushed families (tests). */
    public static List<String> pushedNames() {
        return new ArrayList<>(families.keySet());
    }
}
