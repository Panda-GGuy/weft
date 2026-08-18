package dev.weft.services.jmh;

import dev.weft.services.activation.ActivationScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * WS-1 acceptance benchmark (RFC-0002): one simulated entity phase over the
 * acceptance population — 2000 passive + 500 hostile mobs — with activation
 * scheduling off vs on at the default tiers (32 / 64, 1/4, 1/20).
 *
 * <p>World shape matches the motivating profile (RFC-0004 §0): one player at
 * the center of a ~256-block-radius loaded disc (an ~800-chunk single-player
 * view area), entities placed uniformly by area, so ~93% of them sit beyond
 * the 64-block reduced ring. 10% of hostiles are mid-fight (live target →
 * exempt, never throttled).
 *
 * <p>Cost model per entity tick: {@code AI_TOKENS} of work gated by the
 * scheduler (sensing + goal/target selectors — what MobAiThrottleMixin
 * skips) plus {@code MOVE_TOKENS} that always run (movement, physics,
 * navigation-in-flight — what WS-1 never touches). The 80/20 split reflects
 * the passive-mob-AI-dominated profile that motivated WS-1. Read this as an
 * upper-bound cost model, not as acceptance evidence: it assumes throttled AI
 * dominates a mob's tick, which in-world sub-attribution measured at only
 * ~19-20% of the entity phase. The old flat ≥30% entity-phase bar this
 * comment cited now belongs to WS-1's unbuilt aggressive tier; the shipped
 * parity tier is judged on AI-slice removal instead (RFC-0002 WS-1, split
 * signed off 2026-08-18). The model holds even at a 50/50 split, since
 * ~93% of the population throttles at 1/4-to-1/20 rate. The per-mob
 * decision cost (distance + intervalFor) is included in the throttled
 * path, so the number is net of WS-1's own overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
public class ActivationPhaseBench {

    private static final int PASSIVE = 2000;
    private static final int HOSTILE = 500;
    private static final int WORLD_RADIUS = 256;
    private static final long AI_TOKENS = 160;
    private static final long MOVE_TOKENS = 40;

    @Param({"false", "true"})
    public boolean throttled;

    private final ActivationScheduler scheduler = new ActivationScheduler(
            new ActivationScheduler.Tiers(32, 64, 4, 20),
            Set.of("minecraft:ender_dragon", "minecraft:wither",
                    "minecraft:warden", "minecraft:elder_guardian"),
            Map.of());

    private double[] distSq;
    private String[] typeId;
    private boolean[] exempt;
    private long gameTime;

    @Setup
    public void setUp() {
        SplittableRandom rng = new SplittableRandom(42);
        int total = PASSIVE + HOSTILE;
        distSq = new double[total];
        typeId = new String[total];
        exempt = new boolean[total];
        String[] passiveTypes = {"minecraft:chicken", "minecraft:pig", "minecraft:sheep",
                "minecraft:cow", "minecraft:rabbit", "minecraft:bee", "minecraft:armadillo"};
        String[] hostileTypes = {"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper"};
        for (int i = 0; i < total; i++) {
            // Uniform by area: r = R * sqrt(u) puts most entities far out,
            // exactly like a mob population spread over a loaded disc.
            double r = WORLD_RADIUS * Math.sqrt(rng.nextDouble());
            distSq[i] = r * r;
            boolean hostile = i >= PASSIVE;
            typeId[i] = hostile ? hostileTypes[i % hostileTypes.length]
                    : passiveTypes[i % passiveTypes.length];
            exempt[i] = hostile && rng.nextInt(10) == 0; // mid-fight: live target
        }
    }

    /** One entity phase: tick all 2500 mobs once. */
    @Benchmark
    public void entityPhase(Blackhole bh) {
        long tick = ++gameTime;
        for (int i = 0; i < distSq.length; i++) {
            boolean runAi = true;
            if (throttled && !exempt[i]) {
                int interval = scheduler.intervalFor(typeId[i], distSq[i]);
                runAi = ActivationScheduler.shouldRunThisTick(tick, i, interval);
            }
            if (runAi) {
                Blackhole.consumeCPU(AI_TOKENS);
            }
            Blackhole.consumeCPU(MOVE_TOKENS);
        }
    }
}
