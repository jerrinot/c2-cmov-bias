package org.openjdk.experiments;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@State(Scope.Benchmark)
public class CMoveBiasBench {
    public static final int SIZE = 1 << 20;
    public static final long SEED = 0x5eed_1234_cafe_babeL;
    private static volatile long setupSink;

    @Param({"ALT5050", "RAND5050", "RAND9010", "ALT9010", "SORTED"})
    public String pattern;

    private int[] data;
    private int threshold;
    private long c1;
    private long c2;

    @Setup
    public void setup() {
        data = new int[SIZE];
        threshold = 0;
        c1 = 0x9e37_79b9_7f4a_7c15L;
        c2 = 0xd1b5_4a32_d192_ed03L;

        if (pattern.equals("SORTED")) {
            // A sorted array exposes only one outcome until halfway through the
            // first traversal.  Prime both kernels with the same aggregate 50/50
            // profile first so C2 cannot race that transient one-sided prefix.
            fill(data, "ALT5050");
            long prime = 0;
            for (int i = 0; i < 40; i++) {
                prime ^= independentKernel(data, threshold, c1, c2);
                prime ^= carriedKernel(data, threshold, c1);
            }
            setupSink = prime;
        }
        fill(data, pattern);
    }

    @Benchmark
    public long independentSelect() {
        return independentKernel(data, threshold, c1, c2);
    }

    @Benchmark
    public long carriedSelect() {
        return carriedKernel(data, threshold, c1);
    }

    // The select is independent of the accumulator.  The rotate/XOR recurrence
    // prevents reduction vectorization without disabling SuperWord globally.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static long independentKernel(int[] data, int threshold, long c1, long c2) {
        long s = 0x243f_6a88_85a3_08d3L;
        for (int value : data) {
            long selected = value > threshold ? c1 : c2;
            s = Long.rotateLeft(s, 13) ^ selected;
        }
        return s;
    }

    // The selected value is the next iteration's accumulator, putting CMove
    // latency directly on the loop-carried dependency chain.  Only one arm has
    // one speculative operation.
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public static long carriedKernel(int[] data, int threshold, long mix) {
        long s = 0x243f_6a88_85a3_08d3L;
        for (int value : data) {
            s = value > threshold ? s ^ mix : s;
        }
        return s;
    }

    public static void fill(int[] data, String pattern) {
        SplittableRandom random = new SplittableRandom(SEED);
        switch (pattern) {
            case "ALT5050" -> {
                for (int i = 0; i < data.length; i++) data[i] = (i & 1) == 0 ? 1 : -1;
            }
            case "RAND5050" -> {
                for (int i = 0; i < data.length; i++) data[i] = random.nextInt() < 0 ? 1 : -1;
            }
            case "RAND9010" -> {
                for (int i = 0; i < data.length; i++) data[i] = random.nextInt(10) < 9 ? 1 : -1;
            }
            case "ALT9010" -> {
                for (int i = 0; i < data.length; i++) data[i] = i % 10 == 9 ? -1 : 1;
            }
            case "SORTED" -> {
                int split = data.length / 2;
                for (int i = 0; i < split; i++) data[i] = 1;
                for (int i = split; i < data.length; i++) data[i] = -1;
            }
            default -> throw new IllegalArgumentException("unknown pattern: " + pattern);
        }
    }
}
