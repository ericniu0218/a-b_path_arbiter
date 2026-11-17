package feedlab.sim;

import feedlab.core.mold.MoldCodec;
import feedlab.core.mold.MoldPacket;
import feedlab.sim.ItchFileReader.ItchMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Plays the sender's role: groups ITCH messages into MoldUDP64 packets and stamps each packet with
 * the virtual time it leaves the sender.
 *
 * <p>Messages are packed together while they fit in one packet and were generated within {@code
 * packWindowNanos} of the first message in it. The packet is sent at the timestamp of its last
 * message. When the stream is idle the sender emits a heartbeat every {@code
 * heartbeatIntervalNanos}, and one more after the final message so that tail loss is detectable.
 */
public final class Packetizer {

    /** A packet and the time it was sent. {@code wireBytes} includes Ethernet, IP and UDP headers. */
    public record SentPacket(long sendNanos, MoldPacket packet, int wireBytes) {}

    /** Ethernet (14) + IPv4 (20) + UDP (8). */
    public static final int FRAME_OVERHEAD_BYTES = 42;

    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 1_400;
    public static final long DEFAULT_PACK_WINDOW_NANOS = 10_000;
    public static final long DEFAULT_HEARTBEAT_INTERVAL_NANOS = 1_000_000_000L;

    private final Supplier<ItchMessage> source;
    private final String session;
    private final int maxPayloadBytes;
    private final long packWindowNanos;
    private final long heartbeatIntervalNanos;
    private final long maxMessages;

    private ItchMessage lookahead;
    private boolean sourceDone;
    private long messagesRead;
    private long nextSequence = 1;
    private long lastSendNanos = Long.MIN_VALUE;
    private boolean finished;

    public Packetizer(Supplier<ItchMessage> source, String session, long maxMessages) {
        this(source, session, maxMessages, DEFAULT_MAX_PAYLOAD_BYTES, DEFAULT_PACK_WINDOW_NANOS,
                DEFAULT_HEARTBEAT_INTERVAL_NANOS);
    }

    public Packetizer(
            Supplier<ItchMessage> source,
            String session,
            long maxMessages,
            int maxPayloadBytes,
            long packWindowNanos,
            long heartbeatIntervalNanos) {
        this.source = source;
        this.session = session;
        this.maxMessages = maxMessages;
        this.maxPayloadBytes = maxPayloadBytes;
        this.packWindowNanos = packWindowNanos;
        this.heartbeatIntervalNanos = heartbeatIntervalNanos;
    }

    private ItchMessage peek() {
        if (lookahead == null && !sourceDone) {
            if (messagesRead >= maxMessages) {
                sourceDone = true;
            } else {
                lookahead = source.get();
                if (lookahead == null) {
                    sourceDone = true;
                } else {
                    messagesRead++;
                }
            }
        }
        return lookahead;
    }

    /** Returns the next packet in send order, or {@code null} when the stream is over. */
    public SentPacket next() {
        if (finished) {
            return null;
        }
        ItchMessage first = peek();

        if (first == null) {
            finished = true;
            if (lastSendNanos == Long.MIN_VALUE) {
                return null; // empty input
            }
            return sent(lastSendNanos + heartbeatIntervalNanos, MoldPacket.heartbeat(session, nextSequence));
        }

        if (lastSendNanos != Long.MIN_VALUE && first.timestampNanos() - lastSendNanos > heartbeatIntervalNanos) {
            return sent(lastSendNanos + heartbeatIntervalNanos, MoldPacket.heartbeat(session, nextSequence));
        }

        List<byte[]> messages = new ArrayList<>();
        int payload = MoldCodec.HEADER_LENGTH;
        long sendNanos = first.timestampNanos();
        while (true) {
            ItchMessage m = peek();
            if (m == null) {
                break;
            }
            int size = 2 + m.data().length;
            boolean fits = payload + size <= maxPayloadBytes || messages.isEmpty();
            boolean sameBurst = m.timestampNanos() - first.timestampNanos() <= packWindowNanos;
            if (!fits || !sameBurst) {
                break;
            }
            messages.add(m.data());
            payload += size;
            sendNanos = Math.max(sendNanos, m.timestampNanos());
            lookahead = null;
        }

        MoldPacket packet = MoldPacket.data(session, nextSequence, messages);
        nextSequence += messages.size();
        // Guard against a file whose timestamps step backwards: send times never decrease.
        return sent(Math.max(sendNanos, lastSendNanos), packet);
    }

    private SentPacket sent(long sendNanos, MoldPacket packet) {
        lastSendNanos = sendNanos;
        return new SentPacket(sendNanos, packet, MoldCodec.encodedLength(packet) + FRAME_OVERHEAD_BYTES);
    }
}
