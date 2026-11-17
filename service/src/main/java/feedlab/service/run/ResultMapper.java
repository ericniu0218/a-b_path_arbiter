package feedlab.service.run;

import feedlab.core.arbiter.Line;
import feedlab.service.domain.LatencySummary;
import feedlab.service.domain.RunResult;
import feedlab.service.domain.Segment;
import feedlab.service.domain.Source;
import feedlab.service.domain.TimelineSecond;
import feedlab.sim.BurstAnalyzer;
import feedlab.sim.SegmentStats;
import feedlab.sim.Simulation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.HdrHistogram.Histogram;

/** Converts the simulator's in-memory result into the rows the service stores. */
final class ResultMapper {

    private ResultMapper() {}

    static RunResult headline(Simulation.Result r) {
        BurstAnalyzer.Result burst = r.burst();
        return new RunResult(
                r.messages(),
                r.dataPackets(),
                r.durationNanos(),
                r.gaps(),
                r.clear().messagesLost() + r.fade().messagesLost(),
                r.durationNanos() == 0 ? 0 : burst.totalBits() * 1e9 / r.durationNanos(),
                burst.peakBps(),
                burst.windowsOverCapacity(),
                burst.activeWindows(),
                r.maxBufferedPackets());
    }

    static List<LatencySummary> latencySummaries(Simulation.Result r) {
        List<LatencySummary> out = new ArrayList<>();
        addSegment(out, Segment.CLEAR, r.clear());
        addSegment(out, Segment.FADE, r.fade());
        return out;
    }

    private static void addSegment(List<LatencySummary> out, Segment segment, SegmentStats s) {
        if (s.messagesSent() == 0) {
            return;
        }
        out.add(summary(segment, Source.LINE_A, s, s.lineMessagesLost(Line.A), s.wins(Line.A), s.lineLatency(Line.A)));
        out.add(summary(segment, Source.LINE_B, s, s.lineMessagesLost(Line.B), s.wins(Line.B), s.lineLatency(Line.B)));
        out.add(summary(segment, Source.ARBITRATED, s, s.messagesLost(), null, s.arbitratedLatency()));
    }

    private static LatencySummary summary(
            Segment segment, Source source, SegmentStats s, long lost, Long won, Histogram h) {
        boolean empty = h.getTotalCount() == 0;
        return new LatencySummary(
                segment,
                source,
                s.messagesSent(),
                lost,
                won,
                empty ? 0 : h.getValueAtPercentile(50),
                empty ? 0 : h.getValueAtPercentile(99),
                empty ? 0 : h.getValueAtPercentile(99.9),
                empty ? 0 : h.getMaxValue());
    }

    static List<TimelineSecond> timeline(UUID runId, Simulation.Result r) {
        return r.timeline().stream()
                .map(row -> new TimelineSecond(
                        runId,
                        row.second(),
                        row.delivered(),
                        row.winsA(),
                        row.winsB(),
                        row.lost(),
                        row.p50Nanos(),
                        row.p99Nanos()))
                .toList();
    }
}
