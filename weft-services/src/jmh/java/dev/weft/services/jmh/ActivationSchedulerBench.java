package dev.weft.services.jmh;

import dev.weft.services.activation.ActivationScheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * WS-8 x WS-1: the per-mob activation decision runs once per mob per tick
 * (2k+ times per tick on the RFC-0002 WS-1 acceptance world), so its cost
 * must stay in the low nanoseconds.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class ActivationSchedulerBench {

    private final ActivationScheduler scheduler = new ActivationScheduler(
            new ActivationScheduler.Tiers(32, 64, 4, 20),
            Set.of("minecraft:ender_dragon", "minecraft:wither",
                    "minecraft:warden", "minecraft:elder_guardian"),
            Map.of("minecraft:villager", 2));

    private long gameTime;
    private int entityId;

    @Benchmark
    public int decisionNearMidFar() {
        long tick = ++gameTime;
        int id = ++entityId & 0xFFFF;
        int sum = scheduler.intervalFor("minecraft:cow", 20 * 20);
        int mid = scheduler.intervalFor("minecraft:zombie", 50 * 50);
        int far = scheduler.intervalFor("minecraft:sheep", 200 * 200);
        sum += ActivationScheduler.shouldRunThisTick(tick, id, mid) ? mid : -mid;
        sum += ActivationScheduler.shouldRunThisTick(tick, id, far) ? far : -far;
        return sum;
    }
}
