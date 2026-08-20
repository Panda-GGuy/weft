package dev.weft.neoforge.gametest;

import dev.weft.engine.telemetry.SectionSamples;
import dev.weft.neoforge.regiontick.RegionizedTicking;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The interleaved A/B/A/B harness P2's throughput benchmarks share, and the
 * accumulated corrections that make its readings mean anything.
 *
 * <p>Three measurement mistakes are baked out here so neither benchmark has to
 * remember them:
 *
 * <ol>
 *   <li><b>Time the section, not the tick.</b> RFC-0008's first bench judged a
 *       change confined to the block-entity section by full-tick MSPT. At those
 *       workloads the section is a small slice of a tick that is mostly other
 *       things, so six same-run A/B readings spanned 0.85x–1.31x — an
 *       instrument that cannot tell a win from a loss. The ruler is
 *       {@link RegionizedTicking.SectionProbe}: one sample per tick, covering
 *       only the section under test, barrier included.</li>
 *   <li><b>Alternate, don't just pair.</b> Same-run A/B kills cross-run
 *       variance but not warmup <em>order</em> bias — whichever phase runs
 *       second inherits a warmer JIT, which is exactly how P2 got a 1.59x it
 *       had to retract. Phases here alternate and the several per condition are
 *       pooled, so drift cancels instead of accumulating, and
 *       {@link #SKIP_LEADING} discards the ticks around each flip, which belong
 *       to neither phase.</li>
 *   <li><b>Don't measure with the profiler running.</b> {@link
 *       dev.weft.neoforge.profiler.WeftProfiler} is server-thread-confined (it
 *       corrupted its own deque when the client thread reached it). Its
 *       per-unit clock reads therefore happen on the serial path, where work
 *       runs on the server thread, and silently <em>stop</em> on any path that
 *       moves work to workers. Leaving it on bills profiling overhead to the
 *       baseline alone and inflates the ratio — the same attribution error as
 *       the warmup artifact, reached from a different direction. Callers set
 *       {@code PROFILING_ENABLED = false}; the full-tick figure comes from
 *       vanilla's own {@code getAverageTickTimeNanos()}, which is what
 *       {@code /tps} and spark report anyway.</li>
 * </ol>
 *
 * <p>What it deliberately does not do is decide whether a reading is
 * <em>trustworthy</em>. That needs a condition with a known answer, and only
 * the benchmark knows what its own would be — so each one carries its own
 * negative control (a one-region world for region parallelism, an
 * under-threshold region for sharding) and its own engagement guards.
 */
final class SectionAb {

    private SectionAb() {}

    /**
     * Ticks discarded at the head of each phase. The tick where the flag flips
     * belongs to neither phase, and the next few run with the other path's
     * branch predictions still warm.
     */
    static final int SKIP_LEADING = 8;

    /** One condition's pooled samples, plus the proof it was really that condition. */
    static final class Reading {

        final SectionSamples section;
        final List<Double> msptSamples = new ArrayList<>();
        int sections;
        int engagedSections;
        int minBuckets = Integer.MAX_VALUE;
        int maxBuckets;

        Reading(int capacity) {
            this.section = new SectionSamples(capacity);
        }

        void onSection(long nanos, int buckets, boolean engaged) {
            section.record(nanos);
            sections++;
            if (engaged) {
                engagedSections++;
            }
            minBuckets = Math.min(minBuckets, buckets);
            maxBuckets = Math.max(maxBuckets, buckets);
        }

        /**
         * Vanilla's rolling tick average, sampled at a phase's end so the
         * 100-tick window it covers is this phase's ticks and not the flip.
         */
        void sampleMspt(ServerLevel level) {
            msptSamples.add(level.getServer().getAverageTickTimeNanos() / 1e6);
        }

        double medianMspt() {
            if (msptSamples.isEmpty()) {
                return 0.0;
            }
            List<Double> sorted = new ArrayList<>(msptSamples);
            sorted.sort(null);
            return sorted.get(sorted.size() / 2);
        }

        SectionSamples.Stats stats() {
            return section.stats(SKIP_LEADING);
        }

        int bucketsSeen() {
            return minBuckets == Integer.MAX_VALUE ? 0 : minBuckets;
        }
    }

    /**
     * Answers "did the mechanism under test actually run in this section?".
     * Region parallelism can read it straight off {@code fannedOut}; RFC-0008
     * sharding cannot — a one-region world always has exactly one bucket and
     * never fans out — so it watches the shard-pass counter instead. The probe
     * fires on the server thread after the barrier, so a stateful
     * implementation is safe.
     */
    interface SectionEngagement {
        boolean engaged(int buckets, boolean fannedOut);
    }

    /** What a benchmark schedules its phases against. */
    interface PhaseFlag {

        /** Switch the condition under test on or off. Called on the server thread. */
        void set(boolean on);

        /**
         * Called on the last tick of each measured phase, while the flag is
         * still in that phase's position.
         *
         * <p>Exists because "read the probe at the phase boundary" is wrong in a
         * way that looks right: at phase <em>start</em> no section of the new
         * phase has run yet, so
         * {@code lastEntityPartitionThreads()} still describes the previous
         * phase — a parallel phase sampled that way reports the serial phase's
         * "Server thread" everywhere. Engagement evidence has to be collected
         * while the condition it is evidence for is live.
         */
        default void endOfPhase(boolean on) {}
    }

    /**
     * Installs the section tap. Only sections of {@code sectionKind}
     * ({@code "ENTITY"} or {@code "BLOCK_ENTITY"}) reach the live reading, and
     * {@code engaged} answers "did the mechanism under test actually run here?"
     * — fan-out for region parallelism, a sharded pass for RFC-0008 — so a
     * phase that quietly took the serial path is detectable rather than
     * averaged in.
     *
     * <p>The returned probe writes into {@code live[0]}, which the caller
     * repoints at each phase boundary and nulls before finishing. Callers MUST
     * clear the probe with {@code setSectionProbe(null)}: it is a static hook
     * on the tick path, and one left installed charges this batch's clock reads
     * to every later batch in the same server.
     */
    static void install(String sectionKind, Reading[] live, SectionEngagement engaged) {
        RegionizedTicking.setSectionProbe((kind, nanos, buckets, fannedOut) -> {
            Reading target = live[0];
            if (target != null && sectionKind.equals(kind)) {
                target.onSection(nanos, buckets, engaged.engaged(buckets, fannedOut));
            }
        });
    }

    /**
     * Schedules {@code phases} alternating measured windows after a two-stage
     * warmup that runs <em>both</em> paths before either is measured. Phase 0
     * is the OFF condition, so an odd phase count would leave the conditions
     * unbalanced; callers pass an even one.
     *
     * @param onFirstTick rig construction, run at tick 1
     * @param flag        the condition switch
     * @param off         accumulator for OFF phases
     * @param on          accumulator for ON phases
     * @param onFinish    verdict, run after the last measured phase
     * @return the tick the verdict runs at
     */
    static int schedule(GameTestHelper helper, ServerLevel level, int warmupTicks, int phaseTicks,
                        int phases, Runnable onFirstTick, PhaseFlag flag,
                        Reading off, Reading on, Reading[] live, Runnable onFinish) {
        helper.runAfterDelay(1, onFirstTick);
        // Warm the ON path first, then the OFF path, so neither pays first-touch
        // and JIT costs the other gets to inherit.
        helper.runAfterDelay(warmupTicks, () -> flag.set(true));
        helper.runAfterDelay(2 * warmupTicks, () -> flag.set(false));

        for (int phase = 0; phase < phases; phase++) {
            boolean onPhase = phase % 2 == 1;
            Reading target = onPhase ? on : off;
            int at = 2 * warmupTicks + phase * phaseTicks;
            helper.runAfterDelay(at, () -> {
                flag.set(onPhase);
                live[0] = target;
            });
            helper.runAfterDelay(at + phaseTicks - 1, () -> {
                target.sampleMspt(level);
                flag.endOfPhase(onPhase);
            });
        }

        int finishAt = 2 * warmupTicks + phases * phaseTicks;
        helper.runAfterDelay(finishAt, () -> {
            live[0] = null;
            RegionizedTicking.setSectionProbe(null);
            onFinish.run();
        });
        return finishAt;
    }

    /**
     * The failures that make a reading inadmissible rather than merely
     * disappointing, checked in the order that gives the most useful message.
     * Returns a reason, or null when the run is admissible.
     *
     * @param offMustNotEngage the baseline is only a baseline if the mechanism
     *                         never engaged in it
     */
    static String inadmissible(Reading off, Reading on, boolean offMustNotEngage) {
        if (off.stats() == null || on.stats() == null) {
            return "no section samples survived (" + off.sections + " off, " + on.sections
                    + " on) - did the probe ever fire?";
        }
        if (off.section.dropped() > 0 || on.section.dropped() > 0) {
            return "sample window overflowed (" + off.section.dropped() + "/"
                    + on.section.dropped() + " dropped) - the phases measured different "
                    + "numbers of ticks";
        }
        if (offMustNotEngage && off.engagedSections != 0) {
            return off.engagedSections + " of the " + off.sections + " BASELINE sections engaged "
                    + "the mechanism under test - the baseline is not a baseline";
        }
        return null;
    }
}
