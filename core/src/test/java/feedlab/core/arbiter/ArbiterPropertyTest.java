package feedlab.core.arbiter;

import static org.assertj.core.api.Assertions.assertThat;

import feedlab.core.mold.MoldPacket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

/**
 * Checks the arbiter against randomly generated two-line traffic with loss, delay and reordering.
 *
 * <p>Each run builds a stream of packets, sends a copy down each line with its own drops and
 * delays, and feeds the arrivals to the arbiter in time order. A heartbeat that is never dropped
 * closes the stream on both lines so tail loss is always detectable.
 */
class ArbiterPropertyTest {

    /** A gap timeout far longer than any generated run, yet small enough that every hole can still expire at the end. */
    private static final long NEVER = 1_000_000_000_000L;

    private static final long END_OF_TIME = Long.MAX_VALUE / 2;

    private record Arrival(long time, int order, Line line, MoldPacket packet) {}

    /** A generated run: the arrivals plus what the test needs to know to judge the output. */
    private record Traffic(List<Arrival> arrivals, long totalMessages, Map<Long, Long> firstArrival, long endTime) {}

    private static Traffic generate(long seed, int packetCount, double lossA, double lossB, boolean reorder) {
        Random rng = new Random(seed);
        List<Arrival> arrivals = new ArrayList<>();
        Map<Long, Long> firstArrival = new HashMap<>();
        long sequence = 1;
        long sendTime = 0;
        long[] lastArrival = new long[Line.COUNT];
        int order = 0;
        double[] loss = {lossA, lossB};

        for (int p = 0; p < packetCount; p++) {
            int count = 1 + rng.nextInt(5);
            MoldPacket packet = Packets.range(sequence, sequence + count - 1);
            sendTime += 1 + rng.nextInt(100);
            for (Line line : Line.values()) {
                int i = line.ordinal();
                if (rng.nextDouble() < loss[i]) {
                    continue;
                }
                long arrival = sendTime + 100 + rng.nextInt(400);
                if (!reorder) {
                    arrival = Math.max(arrival, lastArrival[i]);
                }
                lastArrival[i] = arrival;
                arrivals.add(new Arrival(arrival, order++, line, packet));
                for (long s = sequence; s < sequence + count; s++) {
                    firstArrival.merge(s, arrival, Math::min);
                }
            }
            sequence += count;
        }

        long endTime = sendTime + 1_000;
        for (Line line : Line.values()) {
            arrivals.add(new Arrival(endTime, order++, line, Packets.heartbeat(sequence)));
        }
        arrivals.sort(Comparator.comparingLong(Arrival::time).thenComparingInt(Arrival::order));
        return new Traffic(arrivals, sequence - 1, firstArrival, endTime);
    }

    private static RecordingListener run(Traffic traffic, long gapTimeout, Arbiter[] holder) {
        RecordingListener out = new RecordingListener();
        Arbiter arbiter = new Arbiter(ArbiterConfig.withGapTimeout(gapTimeout), out);
        for (Arrival a : traffic.arrivals()) {
            arbiter.onPacket(a.line(), a.packet(), a.time());
        }
        arbiter.advanceTo(END_OF_TIME);
        holder[0] = arbiter;
        return out;
    }

    @Property(tries = 500)
    void everySequenceNumberIsReportedExactlyOnceAndInOrder(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 150) int packetCount,
            @ForAll @DoubleRange(min = 0, max = 0.6) double lossA,
            @ForAll @DoubleRange(min = 0, max = 0.6) double lossB,
            @ForAll @IntRange(min = 0, max = 600) int gapTimeout,
            @ForAll boolean reorder) {
        Traffic traffic = generate(seed, packetCount, lossA, lossB, reorder);
        Arbiter[] arbiter = new Arbiter[1];

        RecordingListener out = run(traffic, gapTimeout, arbiter);

        // Messages and gaps together tile [1, total] with no overlap and no hole.
        long next = 1;
        for (long[] range : out.rangesInOrder) {
            assertThat(range[0]).isEqualTo(next);
            assertThat(range[1]).isGreaterThan(range[0]);
            next = range[1];
        }
        assertThat(next).isEqualTo(traffic.totalMessages() + 1);

        for (RecordingListener.Delivered m : out.messages) {
            // The payload is the one sent under that sequence number.
            assertThat(Packets.sequenceIn(m.payload())).isEqualTo(m.sequence());
            // Nothing is released before its first copy arrived.
            assertThat(m.atNanos()).isGreaterThanOrEqualTo(traffic.firstArrival().get(m.sequence()));
        }

        // A gap never covers more than was lost, and never claims zero messages.
        long lost = out.gaps.stream().mapToLong(g -> g.to() - g.from()).sum();
        assertThat(arbiter[0].messagesLost()).isEqualTo(lost);
        assertThat(out.messages.size() + lost).isEqualTo(traffic.totalMessages());

        // Every message received on a line was either delivered or discarded, and nothing is left buffered.
        for (Line line : Line.values()) {
            long received = traffic.arrivals().stream()
                    .filter(a -> a.line() == line)
                    .mapToLong(a -> a.packet().messageCount())
                    .sum();
            assertThat(arbiter[0].messagesDelivered(line) + arbiter[0].messagesDiscarded(line))
                    .isEqualTo(received);
        }
        assertThat(arbiter[0].bufferedPackets()).isZero();
    }

    @Property(tries = 500)
    void nothingIsLostIfEitherLineDeliveredItGivenEnoughPatience(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 150) int packetCount,
            @ForAll @DoubleRange(min = 0, max = 0.6) double lossA,
            @ForAll @DoubleRange(min = 0, max = 0.6) double lossB,
            @ForAll boolean reorder) {
        Traffic traffic = generate(seed, packetCount, lossA, lossB, reorder);

        RecordingListener out = run(traffic, NEVER, new Arbiter[1]);

        // With a timeout longer than the whole run, the only losses are messages dropped on both lines.
        assertThat(out.sequences()).containsExactlyElementsOf(
                traffic.firstArrival().keySet().stream().sorted().toList());
    }

    @Property(tries = 500)
    void withNoLossAndNoReorderingEachMessageIsReleasedWhenItsFirstCopyArrives(
            @ForAll long seed, @ForAll @IntRange(min = 1, max = 150) int packetCount) {
        Traffic traffic = generate(seed, packetCount, 0, 0, false);

        RecordingListener out = run(traffic, 0, new Arbiter[1]);

        assertThat(out.gaps).isEmpty();
        assertThat(out.messages).hasSize((int) traffic.totalMessages());
        for (RecordingListener.Delivered m : out.messages) {
            assertThat(m.atNanos()).isEqualTo(traffic.firstArrival().get(m.sequence()));
        }
    }

    @Property(tries = 200)
    void theSameInputAlwaysGivesTheSameOutput(
            @ForAll long seed,
            @ForAll @IntRange(min = 1, max = 100) int packetCount,
            @ForAll @IntRange(min = 0, max = 600) int gapTimeout) {
        Traffic traffic = generate(seed, packetCount, 0.3, 0.3, true);

        RecordingListener first = run(traffic, gapTimeout, new Arbiter[1]);
        RecordingListener second = run(traffic, gapTimeout, new Arbiter[1]);

        assertThat(second.gaps).isEqualTo(first.gaps);
        assertThat(second.messages).hasSameSizeAs(first.messages);
        for (int i = 0; i < first.messages.size(); i++) {
            assertThat(second.messages.get(i).sequence()).isEqualTo(first.messages.get(i).sequence());
            assertThat(second.messages.get(i).winner()).isEqualTo(first.messages.get(i).winner());
            assertThat(second.messages.get(i).atNanos()).isEqualTo(first.messages.get(i).atNanos());
        }
    }
}
