package feedlab.service.web;

import feedlab.service.domain.FadeWindow;
import feedlab.service.domain.LatencySummary;
import feedlab.service.domain.RunConfig;
import feedlab.service.domain.RunResult;
import feedlab.service.domain.RunStatus;
import feedlab.service.domain.Segment;
import feedlab.service.domain.SimulationRun;
import feedlab.service.domain.Source;
import feedlab.service.domain.TimelineSecond;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The JSON shapes the API returns. Times are in the units a person reads: micros, millis, Mbps. */
public final class RunViews {

    private RunViews() {}

    public record Summary(
            UUID id,
            RunStatus status,
            Instant createdAt,
            Instant finishedAt,
            String inputFile,
            String start,
            String end,
            Long messages,
            Long messagesLost) {

        public static Summary of(SimulationRun run) {
            RunResult r = run.getResult();
            RunConfig c = run.getConfig();
            return new Summary(
                    run.getId(),
                    run.getStatus(),
                    run.getCreatedAt(),
                    run.getFinishedAt(),
                    c.inputFile(),
                    c.startTime(),
                    c.endTime(),
                    r == null ? null : r.messages(),
                    r == null ? null : r.messagesLost());
        }
    }

    public record Detail(
            UUID id,
            RunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String error,
            Config config,
            Results results) {

        public static Detail of(SimulationRun run) {
            return new Detail(
                    run.getId(),
                    run.getStatus(),
                    run.getCreatedAt(),
                    run.getStartedAt(),
                    run.getFinishedAt(),
                    run.getErrorMessage(),
                    Config.of(run.getConfig(), run.getFades()),
                    run.getResult() == null ? null : Results.of(run.getResult(), run.getLatencySummaries()));
        }
    }

    public record Config(
            String inputFile,
            String start,
            String end,
            Long maxMessages,
            LineA lineA,
            LineB lineB,
            double gapTimeoutMillis,
            long seed) {

        public record LineA(
                double latencyMicros,
                double jitterMicros,
                double loss,
                double bandwidthMbps,
                double maxQueueMillis,
                List<Fade> fades) {}

        public record LineB(double latencyMicros, double jitterMicros, double loss) {}

        public record Fade(double startSeconds, double lengthSeconds, double loss) {}

        static Config of(RunConfig c, List<FadeWindow> fades) {
            return new Config(
                    c.inputFile(),
                    c.startTime(),
                    c.endTime(),
                    c.maxMessages(),
                    new LineA(
                            c.aLatencyNanos() / 1e3,
                            c.aJitterNanos() / 1e3,
                            c.aLoss(),
                            c.aBandwidthBps() / 1e6,
                            c.aMaxQueueNanos() / 1e6,
                            fades.stream()
                                    .map(f -> new Fade(
                                            f.startOffsetNanos() / 1e9,
                                            (f.endOffsetNanos() - f.startOffsetNanos()) / 1e9,
                                            f.loss()))
                                    .toList()),
                    new LineB(c.bLatencyNanos() / 1e3, c.bJitterNanos() / 1e3, c.bLoss()),
                    c.gapTimeoutNanos() / 1e6,
                    c.seed());
        }
    }

    public record Results(
            long messages,
            long dataPackets,
            double durationSeconds,
            long gaps,
            long messagesLost,
            double averageMbps,
            double peakMbps,
            long burstWindowsOverCapacity,
            long burstWindowsActive,
            int maxBufferedPackets,
            List<Latency> latency) {

        static Results of(RunResult r, List<LatencySummary> summaries) {
            return new Results(
                    r.messages(),
                    r.dataPackets(),
                    r.durationNanos() / 1e9,
                    r.gaps(),
                    r.messagesLost(),
                    r.averageBps() / 1e6,
                    r.peakBps() / 1e6,
                    r.burstWindowsOver(),
                    r.burstWindowsActive(),
                    r.maxBufferedPackets(),
                    summaries.stream()
                            .sorted(Comparator.comparing(LatencySummary::segment).thenComparing(LatencySummary::source))
                            .map(Latency::of)
                            .toList());
        }
    }

    /** @param messagesWon messages delivered from this line's copy; null for the arbitrated stream */
    public record Latency(
            Segment segment,
            Source source,
            long messagesSent,
            long messagesLost,
            Long messagesWon,
            double p50Micros,
            double p99Micros,
            double p999Micros,
            double maxMicros) {

        static Latency of(LatencySummary s) {
            return new Latency(
                    s.segment(),
                    s.source(),
                    s.messagesSent(),
                    s.messagesLost(),
                    s.messagesWon(),
                    s.p50Nanos() / 1e3,
                    s.p99Nanos() / 1e3,
                    s.p999Nanos() / 1e3,
                    s.maxNanos() / 1e3);
        }
    }

    public record TimelinePoint(
            long second, long delivered, long winsA, long winsB, long lost, double p50Micros, double p99Micros) {

        public static TimelinePoint of(TimelineSecond t) {
            return new TimelinePoint(
                    t.getSecond(),
                    t.getDelivered(),
                    t.getWinsA(),
                    t.getWinsB(),
                    t.getLost(),
                    t.getP50Nanos() / 1e3,
                    t.getP99Nanos() / 1e3);
        }
    }
}
