package feedlab.core.arbiter;

import feedlab.core.mold.MoldPacket;
import java.util.Map;
import java.util.TreeMap;

/**
 * Merges two redundant copies of a sequenced MoldUDP64 stream into one gap-free, duplicate-free
 * stream, taking each message from whichever line delivers it first.
 *
 * <p>The arbiter owns no clock and no threads. Every call carries the current time, so the same
 * inputs always give the same outputs. That makes it usable both with a wall clock and with the
 * virtual clock of a simulation, and it makes every behaviour testable.
 *
 * <h2>Rules</h2>
 *
 * <ul>
 *   <li>A message is released as soon as it is the next expected sequence number.
 *   <li>Copies of messages already released (or already given up on) are discarded.
 *   <li>A packet that arrives ahead of the next expected sequence number is buffered. This is a
 *       hole: the missing messages may still arrive on the other line.
 *   <li>A hole at the head of the stream gets {@code gapTimeoutNanos} to fill, counted from the
 *       moment it becomes the head of the stream. If it does not fill, the arbiter reports a gap
 *       and skips past it.
 *   <li>Heartbeats carry the sender's next sequence number, so they reveal messages lost at the
 *       tail of a burst.
 * </ul>
 *
 * <p>Not thread safe. Times passed in must not go backwards; an earlier time is treated as the
 * latest time seen.
 */
public final class Arbiter {

    private static final long NO_GAP = Long.MIN_VALUE;

    private record Buffered(Line line, MoldPacket packet) {}

    private final ArbiterConfig config;
    private final ArbiterListener listener;
    private final TreeMap<Long, Buffered> buffer = new TreeMap<>();

    private final long[] packetsReceived = new long[Line.COUNT];
    private final long[] messagesDelivered = new long[Line.COUNT];
    private final long[] messagesDiscarded = new long[Line.COUNT];

    private String session;
    private long nextExpected;
    private long highestSeen;
    private long gapSince = NO_GAP;
    private long gapAtSequence;
    private long now = Long.MIN_VALUE;

    private long gaps;
    private long messagesLost;
    private long foreignPackets;
    private int maxBuffered;

    public Arbiter(ArbiterConfig config, ArbiterListener listener) {
        this.config = config;
        this.listener = listener;
        this.nextExpected = config.firstSequence();
        this.highestSeen = config.firstSequence();
    }

    /** Feeds one packet received on {@code line} at time {@code nowNanos}. */
    public void onPacket(Line line, MoldPacket packet, long nowNanos) {
        advanceTo(nowNanos);
        long at = now;
        packetsReceived[line.ordinal()]++;

        if (session == null) {
            session = packet.session();
        } else if (!session.equals(packet.session())) {
            foreignPackets++;
            return;
        }

        long start = packet.sequence();
        long end = packet.nextSequence();
        if (end > highestSeen) {
            highestSeen = end;
        }

        if (packet.messageCount() > 0) {
            if (end <= nextExpected) {
                messagesDiscarded[line.ordinal()] += packet.messageCount();
            } else if (start <= nextExpected) {
                deliver(line, packet, at);
                drain(at);
            } else {
                buffer(line, packet);
                if (buffer.size() > config.maxBufferedPackets()) {
                    skip(at);
                }
            }
        }
        noteGap(at);
    }

    /** Moves time forward without a packet, so that gap timeouts fire on a quiet stream. */
    public void advanceTo(long nowNanos) {
        long t = Math.max(nowNanos, now);
        while (gapSince != NO_GAP && t - gapSince >= config.gapTimeoutNanos()) {
            skip(gapSince + config.gapTimeoutNanos());
        }
        now = t;
    }

    private void deliver(Line line, MoldPacket packet, long at) {
        int from = (int) (nextExpected - packet.sequence());
        for (int i = from; i < packet.messageCount(); i++) {
            long sequence = nextExpected++;
            messagesDelivered[line.ordinal()]++;
            listener.onMessage(sequence, packet.messages().get(i), line, at);
        }
        messagesDiscarded[line.ordinal()] += from;
    }

    private void buffer(Line line, MoldPacket packet) {
        Buffered incoming = new Buffered(line, packet);
        Buffered existing = buffer.get(packet.sequence());
        if (existing == null) {
            buffer.put(packet.sequence(), incoming);
            maxBuffered = Math.max(maxBuffered, buffer.size());
        } else if (existing.packet().nextSequence() >= packet.nextSequence()) {
            messagesDiscarded[line.ordinal()] += packet.messageCount();
        } else {
            // Same first sequence but the new copy covers more messages: keep the longer one.
            messagesDiscarded[existing.line().ordinal()] += existing.packet().messageCount();
            buffer.put(packet.sequence(), incoming);
        }
    }

    /** Releases buffered packets that have become deliverable. */
    private void drain(long at) {
        while (!buffer.isEmpty()) {
            Map.Entry<Long, Buffered> first = buffer.firstEntry();
            if (first.getKey() > nextExpected) {
                return;
            }
            buffer.pollFirstEntry();
            Buffered b = first.getValue();
            if (b.packet().nextSequence() <= nextExpected) {
                messagesDiscarded[b.line().ordinal()] += b.packet().messageCount();
            } else {
                deliver(b.line(), b.packet(), at);
            }
        }
    }

    /** Gives up on the hole at the head of the stream and reports it as a gap. */
    private void skip(long at) {
        long target = buffer.isEmpty() ? highestSeen : buffer.firstKey();
        if (target > nextExpected) {
            gaps++;
            messagesLost += target - nextExpected;
            long from = nextExpected;
            nextExpected = target;
            listener.onGap(from, target, at);
        }
        drain(at);
        noteGap(at);
    }

    /** Starts, keeps or clears the timer for the hole at the head of the stream. */
    private void noteGap(long at) {
        if (highestSeen > nextExpected) {
            if (gapSince == NO_GAP || gapAtSequence != nextExpected) {
                gapSince = at;
                gapAtSequence = nextExpected;
            }
        } else {
            gapSince = NO_GAP;
        }
    }

    public long nextExpectedSequence() {
        return nextExpected;
    }

    public long packetsReceived(Line line) {
        return packetsReceived[line.ordinal()];
    }

    /** Messages released downstream from this line's copy. */
    public long messagesDelivered(Line line) {
        return messagesDelivered[line.ordinal()];
    }

    /** Messages from this line that were dropped as duplicates or because they arrived too late. */
    public long messagesDiscarded(Line line) {
        return messagesDiscarded[line.ordinal()];
    }

    public long gaps() {
        return gaps;
    }

    public long messagesLost() {
        return messagesLost;
    }

    /** Packets ignored because they belonged to a different session. */
    public long foreignPackets() {
        return foreignPackets;
    }

    public int bufferedPackets() {
        return buffer.size();
    }

    public int maxBufferedPackets() {
        return maxBuffered;
    }
}
