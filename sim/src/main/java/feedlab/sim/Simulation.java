package feedlab.sim;

import feedlab.core.arbiter.Arbiter;
import feedlab.core.arbiter.ArbiterConfig;
import feedlab.core.arbiter.ArbiterListener;
import feedlab.core.arbiter.Line;
import feedlab.core.mold.MoldPacket;
import feedlab.sim.Packetizer.SentPacket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import org.HdrHistogram.Histogram;

/**
 * Runs the whole experiment on a virtual clock: send each packet down both paths, feed the
 * arrivals to the arbiter in time order, and measure what comes out.
 *
 * <p>Nothing here reads the wall clock. Time is the exchange timestamp of each message plus the
 * delays the path models add, so a run is exact and repeatable, and a day of data replays as fast
 * as the machine can process it.
 */
public final class Simulation {

    public record Config(PathModel pathA, PathModel pathB, long gapTimeoutNanos, long seed, long burstWindowNanos) {}

    /** One second of the run, by send time. Latencies are for the arbitrated stream. */
    public record TimelineRow(
            long second, long delivered, long winsA, long winsB, long lost, long p50Nanos, long p99Nanos) {}

    public record PathStats(
            PathModel model, long packetsSent, long packetsLost, long packetsDroppedAtQueue, long maxQueueDelayNanos) {}

    public record Result(
            Config config,
            long messages,
            long dataPackets,
            long durationNanos,
            SegmentStats clear,
            SegmentStats fade,
            List<TimelineRow> timeline,
            BurstAnalyzer.Result burst,
            PathStats pathA,
            PathStats pathB,
            long gaps,
            long undetectedTailLoss,
            int maxBufferedPackets) {

        public SegmentStats segment(boolean inFade) {
            return inFade ? fade : clear;
        }
    }

    private record Arrival(long timeNanos, long order, Line line, MoldPacket packet) {}

    /** A data packet whose messages have not all been delivered or declared lost yet. */
    private record InFlight(long firstSequence, long endSequence, long sendNanos) {}

    private final Config config;
    private final SegmentStats clear = new SegmentStats();
    private final SegmentStats fade = new SegmentStats();
    private final ArrayDeque<InFlight> inFlight = new ArrayDeque<>();
    private final List<TimelineRow> timeline = new ArrayList<>();

    private long originNanos = Long.MIN_VALUE;
    private long undetectedTailLoss;

    private long rowSecond = -1;
    private long rowWinsA;
    private long rowWinsB;
    private long rowLost;
    private final Histogram rowLatency = new Histogram(3);

    private Simulation(Config config) {
        this.config = config;
    }

    public static Result run(Packetizer packets, Config config) {
        return new Simulation(config).execute(packets);
    }

    private boolean inFade(long sendNanos) {
        long offset = sendNanos - originNanos;
        return config.pathA().inFade(offset) || config.pathB().inFade(offset);
    }

    private SegmentStats segmentAt(long sendNanos) {
        return inFade(sendNanos) ? fade : clear;
    }

    private Result execute(Packetizer packets) {
        PathSimulator[] paths = {
            new PathSimulator(config.pathA(), config.seed()),
            new PathSimulator(config.pathB(), config.seed() + 1)
        };
        Listener listener = new Listener();
        Arbiter arbiter = new Arbiter(ArbiterConfig.withGapTimeout(config.gapTimeoutNanos()), listener);
        PriorityQueue<Arrival> arrivals = new PriorityQueue<>((x, y) -> x.timeNanos() != y.timeNanos()
                ? Long.compare(x.timeNanos(), y.timeNanos())
                : Long.compare(x.order(), y.order()));
        BurstAnalyzer burst = new BurstAnalyzer(config.burstWindowNanos(), config.pathA().bandwidthBps());

        long order = 0;
        long messages = 0;
        long dataPackets = 0;
        long lastSendNanos = 0;

        SentPacket sent;
        while ((sent = packets.next()) != null) {
            long sendNanos = sent.sendNanos();
            if (originNanos == Long.MIN_VALUE) {
                originNanos = sendNanos;
            }
            lastSendNanos = sendNanos;

            // Every arrival still to be scheduled is later than this send time, so anything due by
            // now can be handed to the arbiter in its final order.
            while (!arrivals.isEmpty() && arrivals.peek().timeNanos() <= sendNanos) {
                Arrival a = arrivals.poll();
                arbiter.onPacket(a.line(), a.packet(), a.timeNanos());
            }

            MoldPacket packet = sent.packet();
            int count = packet.messageCount();
            SegmentStats segment = segmentAt(sendNanos);
            if (count > 0) {
                messages += count;
                dataPackets++;
                segment.recordSent(count);
                inFlight.add(new InFlight(packet.sequence(), packet.nextSequence(), sendNanos));
                burst.onPacket(sendNanos, sent.wireBytes(), count);
            }

            for (Line line : Line.values()) {
                OptionalLong arrival = paths[line.ordinal()].transmit(sendNanos, sent.wireBytes());
                if (arrival.isPresent()) {
                    arrivals.add(new Arrival(arrival.getAsLong(), order++, line, packet));
                    if (count > 0) {
                        segment.recordLineArrival(line, arrival.getAsLong() - sendNanos, count);
                    }
                } else if (count > 0) {
                    segment.recordLineLoss(line, count);
                }
            }
        }

        long lastEventNanos = lastSendNanos;
        while (!arrivals.isEmpty()) {
            Arrival a = arrivals.poll();
            arbiter.onPacket(a.line(), a.packet(), a.timeNanos());
            lastEventNanos = a.timeNanos();
        }
        if (messages > 0) {
            long endNanos = lastEventNanos + config.gapTimeoutNanos() + 1;
            arbiter.advanceTo(endNanos);
            // If the last data packets and the closing heartbeat were lost on both paths, the
            // arbiter cannot know those messages existed. The simulation does, so count them.
            if (arbiter.nextExpectedSequence() <= messages) {
                undetectedTailLoss = messages + 1 - arbiter.nextExpectedSequence();
                listener.onGap(arbiter.nextExpectedSequence(), messages + 1, endNanos);
            }
        }
        flushRow();

        return new Result(
                config,
                messages,
                dataPackets,
                originNanos == Long.MIN_VALUE ? 0 : lastSendNanos - originNanos,
                clear,
                fade,
                List.copyOf(timeline),
                burst.finish(),
                stats(paths[0]),
                stats(paths[1]),
                arbiter.gaps(),
                undetectedTailLoss,
                arbiter.maxBufferedPackets());
    }

    private static PathStats stats(PathSimulator p) {
        return new PathStats(
                p.model(), p.packetsSent(), p.packetsLost(), p.packetsDroppedAtQueue(), p.maxQueueDelayNanos());
    }

    /** The send time of the packet that carried {@code sequence}. Sequences are queried in order. */
    private long sendTimeOf(long sequence) {
        while (inFlight.peekFirst().endSequence() <= sequence) {
            inFlight.pollFirst();
        }
        return inFlight.peekFirst().sendNanos();
    }

    private void moveToRow(long sendNanos) {
        long second = (sendNanos - originNanos) / 1_000_000_000L;
        if (second != rowSecond) {
            flushRow();
            rowSecond = second;
        }
    }

    private void flushRow() {
        long delivered = rowLatency.getTotalCount();
        if (rowSecond >= 0 && delivered + rowLost > 0) {
            timeline.add(new TimelineRow(
                    rowSecond,
                    delivered,
                    rowWinsA,
                    rowWinsB,
                    rowLost,
                    delivered == 0 ? 0 : rowLatency.getValueAtPercentile(50),
                    delivered == 0 ? 0 : rowLatency.getValueAtPercentile(99)));
        }
        rowWinsA = 0;
        rowWinsB = 0;
        rowLost = 0;
        rowLatency.reset();
    }

    private final class Listener implements ArbiterListener {

        @Override
        public void onMessage(long sequence, byte[] payload, Line winner, long nowNanos) {
            long sendNanos = sendTimeOf(sequence);
            long latency = nowNanos - sendNanos;
            segmentAt(sendNanos).recordDelivered(winner, latency);
            moveToRow(sendNanos);
            if (winner == Line.A) {
                rowWinsA++;
            } else {
                rowWinsB++;
            }
            rowLatency.recordValue(latency);
        }

        @Override
        public void onGap(long fromSequence, long toSequence, long nowNanos) {
            long sequence = fromSequence;
            while (sequence < toSequence) {
                long sendNanos = sendTimeOf(sequence);
                long upTo = Math.min(toSequence, inFlight.peekFirst().endSequence());
                segmentAt(sendNanos).recordLost(upTo - sequence);
                moveToRow(sendNanos);
                rowLost += upTo - sequence;
                sequence = upTo;
            }
        }
    }
}
