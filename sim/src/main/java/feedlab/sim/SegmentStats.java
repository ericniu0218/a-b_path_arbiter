package feedlab.sim;

import feedlab.core.arbiter.Line;
import org.HdrHistogram.Histogram;

/**
 * Results for one slice of the run (clear conditions, or during a fade), counted per message.
 *
 * <p>Latencies are from the moment the sender sent a message's packet to the moment the message
 * was available at the receiver, in nanoseconds of virtual time.
 */
public final class SegmentStats {

    private final Histogram arbitratedLatency = new Histogram(3);
    private final Histogram[] lineLatency = {new Histogram(3), new Histogram(3)};
    private final long[] wins = new long[Line.COUNT];
    private final long[] lineMessagesLost = new long[Line.COUNT];
    private long messagesSent;
    private long messagesLost;

    void recordSent(int messages) {
        messagesSent += messages;
    }

    void recordLineArrival(Line line, long latencyNanos, int messages) {
        lineLatency[line.ordinal()].recordValueWithCount(latencyNanos, messages);
    }

    void recordLineLoss(Line line, int messages) {
        lineMessagesLost[line.ordinal()] += messages;
    }

    void recordDelivered(Line winner, long latencyNanos) {
        wins[winner.ordinal()]++;
        arbitratedLatency.recordValue(latencyNanos);
    }

    void recordLost(long messages) {
        messagesLost += messages;
    }

    public long messagesSent() {
        return messagesSent;
    }

    /** Messages the arbitrated stream never delivered. */
    public long messagesLost() {
        return messagesLost;
    }

    public long messagesDelivered() {
        return arbitratedLatency.getTotalCount();
    }

    /** Messages delivered from this line's copy. */
    public long wins(Line line) {
        return wins[line.ordinal()];
    }

    /** Messages that never arrived on this line, whatever the other line did. */
    public long lineMessagesLost(Line line) {
        return lineMessagesLost[line.ordinal()];
    }

    public Histogram arbitratedLatency() {
        return arbitratedLatency;
    }

    /** Latency of the messages that did arrive on this line, ignoring the other line. */
    public Histogram lineLatency(Line line) {
        return lineLatency[line.ordinal()];
    }
}
