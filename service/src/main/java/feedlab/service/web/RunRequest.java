package feedlab.service.web;

import feedlab.service.domain.FadeWindow;
import feedlab.service.domain.RunConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * The body of {@code POST /api/runs}. Only {@code inputFile} is required. Everything else falls
 * back to the same defaults as the command-line tool, except that there is no fade unless one is
 * asked for.
 */
public record RunRequest(
        @NotBlank String inputFile,
        String start,
        String end,
        @Positive Long maxMessages,
        @Valid LineA lineA,
        @Valid LineB lineB,
        @PositiveOrZero Double gapTimeoutMillis,
        Long seed) {

    /** The fast path. A bandwidth of 0 means unlimited. */
    public record LineA(
            @PositiveOrZero Double latencyMicros,
            @PositiveOrZero Double jitterMicros,
            @DecimalMin("0") @DecimalMax("1") Double loss,
            @PositiveOrZero Double bandwidthMbps,
            @PositiveOrZero Double maxQueueMillis,
            List<@Valid @NotNull Fade> fades) {}

    /** The slow path. */
    public record LineB(
            @PositiveOrZero Double latencyMicros,
            @PositiveOrZero Double jitterMicros,
            @DecimalMin("0") @DecimalMax("1") Double loss) {}

    /** A period of raised loss on line A, in seconds from the first message. */
    public record Fade(
            @NotNull @PositiveOrZero Double startSeconds,
            @NotNull @Positive Double lengthSeconds,
            @NotNull @DecimalMin("0") @DecimalMax("1") Double loss) {}

    private static final LineA DEFAULT_A = new LineA(3_980.0, 2.0, 1e-5, 100.0, 2.0, List.of());
    private static final LineB DEFAULT_B = new LineB(6_550.0, 1.0, 1e-7);
    private static final double DEFAULT_GAP_TIMEOUT_MILLIS = 5.0;
    private static final long DEFAULT_SEED = 42;

    public RunConfig toConfig() {
        LineA a = lineA == null ? DEFAULT_A : lineA;
        LineB b = lineB == null ? DEFAULT_B : lineB;
        return new RunConfig(
                inputFile,
                start,
                end,
                maxMessages,
                microsToNanos(or(a.latencyMicros(), DEFAULT_A.latencyMicros())),
                microsToNanos(or(a.jitterMicros(), DEFAULT_A.jitterMicros())),
                or(a.loss(), DEFAULT_A.loss()),
                Math.round(or(a.bandwidthMbps(), DEFAULT_A.bandwidthMbps()) * 1e6),
                Math.round(or(a.maxQueueMillis(), DEFAULT_A.maxQueueMillis()) * 1e6),
                microsToNanos(or(b.latencyMicros(), DEFAULT_B.latencyMicros())),
                microsToNanos(or(b.jitterMicros(), DEFAULT_B.jitterMicros())),
                or(b.loss(), DEFAULT_B.loss()),
                Math.round(or(gapTimeoutMillis, DEFAULT_GAP_TIMEOUT_MILLIS) * 1e6),
                seed == null ? DEFAULT_SEED : seed);
    }

    public List<FadeWindow> toFades() {
        if (lineA == null || lineA.fades() == null) {
            return List.of();
        }
        return lineA.fades().stream()
                .map(f -> new FadeWindow(
                        Math.round(f.startSeconds() * 1e9),
                        Math.round((f.startSeconds() + f.lengthSeconds()) * 1e9),
                        f.loss()))
                .toList();
    }

    private static double or(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static long microsToNanos(double micros) {
        return Math.round(micros * 1_000);
    }
}
