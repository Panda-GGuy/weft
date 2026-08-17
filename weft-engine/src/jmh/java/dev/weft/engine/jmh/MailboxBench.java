package dev.weft.engine.jmh;

import dev.weft.engine.mail.Mailbox;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * WS-8: mailbox hot path — every cross-owner interaction pays one post and
 * one drain (RFC-0001 §8.2). One op = a region's plausible per-tick mail
 * load: 256 posts then a full drain.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class MailboxBench {

    private final Mailbox<Integer> mailbox = new Mailbox<>();

    @Benchmark
    public int post256Drain() {
        for (int i = 0; i < 256; i++) {
            mailbox.post(i);
        }
        return mailbox.drain().size();
    }
}
