package feedlab.sim;

import feedlab.core.arbiter.Line;
import feedlab.sim.Simulation.PathStats;
import feedlab.sim.Simulation.Result;
import feedlab.sim.Simulation.TimelineRow;
import java.util.Locale;
import org.HdrHistogram.Histogram;

/** Renders a simulation result as a plain-text report and a per-second CSV timeline. */
public final class Report {

    private Report() {}

    public static String text(String input, Result r) {
        StringBuilder out = new StringBuilder();
        PathStats a = r.pathA();
        PathStats b = r.pathB();

        line(out, "PROTECTED FEED LAB");
        line(out, "Input: %s", input);
        line(out, "       %,d messages in %,d packets over %.1f s of exchange time",
                r.messages(), r.dataPackets(), r.durationNanos() / 1e9);
        line(out, "");
        line(out, "SETUP");
        line(out, "  Line A  %s", describe(a));
        line(out, "  Line B  %s", describe(b));
        line(out, "  Gap timeout %.3f ms, seed %d", r.config().gapTimeoutNanos() / 1e6, r.config().seed());
        line(out, "");

        line(out, "LATENCY, sender to receiver, in microseconds");
        line(out, "  %-26s %10s %10s %10s %10s %12s %9s", "", "p50", "p99", "p99.9", "max", "msgs lost", "lost %");
        segment(out, "CLEAR", r.clear());
        segment(out, "FADE", r.fade());
        line(out, "  'Alone' rows time the packets that arrived on that line and count the messages that did not.");
        line(out, "");

        line(out, "WHICH LINE'S COPY WAS DELIVERED");
        line(out, "  %-8s %12s %12s", "", "Line A", "Line B");
        wins(out, "clear", r.clear());
        wins(out, "fade", r.fade());
        line(out, "");

        BurstAnalyzer.Result burst = r.burst();
        line(out, "BURSTS, in %.0f ms windows of send time", burst.windowNanos() / 1e6);
        line(out, "  Average rate   %10.2f Mbps  %,12.0f msgs/s",
                r.durationNanos() == 0 ? 0 : burst.totalBits() * 1e3 / r.durationNanos(),
                r.durationNanos() == 0 ? 0 : burst.totalMessages() * 1e9 / r.durationNanos());
        line(out, "  Peak window    %10.2f Mbps  %,12.0f msgs/s", burst.peakBps() / 1e6, burst.peakMessagesPerSecond());
        if (burst.capacityBps() > 0) {
            line(out, "  Windows above Line A's %.0f Mbps: %,d of %,d active windows (%.3f%%)",
                    burst.capacityBps() / 1e6,
                    burst.windowsOverCapacity(),
                    burst.activeWindows(),
                    percent(burst.windowsOverCapacity(), burst.activeWindows()));
        }
        line(out, "");

        line(out, "PATHS");
        path(out, "Line A", a);
        path(out, "Line B", b);
        line(out, "");

        long lost = r.clear().messagesLost() + r.fade().messagesLost();
        line(out, "ARBITER");
        line(out, "  Gaps reported %,d, messages lost %,d of %,d, peak reorder buffer %,d packets",
                r.gaps(), lost, r.messages(), r.maxBufferedPackets());
        if (r.undetectedTailLoss() > 0) {
            line(out, "  %,d of those were at the very end of the stream and lost on both paths with the closing heartbeat,",
                    r.undetectedTailLoss());
            line(out, "  so no receiver could have detected them.");
        }
        return out.toString();
    }

    public static String timelineCsv(Result r) {
        StringBuilder out = new StringBuilder("second,delivered,wins_a,wins_b,lost,p50_us,p99_us\n");
        for (TimelineRow row : r.timeline()) {
            out.append(String.format(Locale.ROOT, "%d,%d,%d,%d,%d,%.1f,%.1f%n",
                    row.second(), row.delivered(), row.winsA(), row.winsB(), row.lost(),
                    row.p50Nanos() / 1e3, row.p99Nanos() / 1e3));
        }
        return out.toString();
    }

    private static String describe(PathStats p) {
        PathModel m = p.model();
        StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%-9s latency %.3f ms, jitter %.1f us, loss %.0e",
                m.name(), m.latencyNanos() / 1e6, m.jitterNanos() / 1e3, m.lossProbability()));
        if (m.bandwidthBps() > 0) {
            s.append(String.format(Locale.ROOT, ", %.0f Mbps, queue limit %.1f ms", m.bandwidthBps() / 1e6, m.maxQueueNanos() / 1e6));
        } else {
            s.append(", unlimited bandwidth");
        }
        for (PathModel.Fade f : m.fades()) {
            s.append(String.format(Locale.ROOT, ", fade %.1f-%.1f s at %.0f%% loss",
                    f.startOffsetNanos() / 1e9, f.endOffsetNanos() / 1e9, f.lossProbability() * 100));
        }
        return s.toString();
    }

    private static void segment(StringBuilder out, String name, SegmentStats s) {
        line(out, "  %s (%,d messages sent)", name, s.messagesSent());
        if (s.messagesSent() == 0) {
            return;
        }
        latencyRow(out, "Line A alone", s.lineLatency(Line.A), s.lineMessagesLost(Line.A), s.messagesSent());
        latencyRow(out, "Line B alone", s.lineLatency(Line.B), s.lineMessagesLost(Line.B), s.messagesSent());
        latencyRow(out, "Arbitrated A+B", s.arbitratedLatency(), s.messagesLost(), s.messagesSent());
    }

    private static void latencyRow(StringBuilder out, String label, Histogram h, long lost, long sent) {
        if (h.getTotalCount() == 0) {
            line(out, "    %-24s %10s %10s %10s %10s %,12d %8.4f%%", label, "-", "-", "-", "-", lost, percent(lost, sent));
            return;
        }
        line(out, "    %-24s %10.1f %10.1f %10.1f %10.1f %,12d %8.4f%%",
                label,
                h.getValueAtPercentile(50) / 1e3,
                h.getValueAtPercentile(99) / 1e3,
                h.getValueAtPercentile(99.9) / 1e3,
                h.getMaxValue() / 1e3,
                lost,
                percent(lost, sent));
    }

    private static void wins(StringBuilder out, String name, SegmentStats s) {
        long delivered = s.messagesDelivered();
        line(out, "  %-8s %11.3f%% %11.3f%%", name, percent(s.wins(Line.A), delivered), percent(s.wins(Line.B), delivered));
    }

    private static void path(StringBuilder out, String label, PathStats p) {
        line(out, "  %s  %,d packets sent, %,d lost in transit, %,d dropped at a full queue, worst queue wait %.1f us",
                label, p.packetsSent(), p.packetsLost(), p.packetsDroppedAtQueue(), p.maxQueueDelayNanos() / 1e3);
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static void line(StringBuilder out, String format, Object... args) {
        out.append(String.format(Locale.ROOT, format, args)).append('\n');
    }
}
