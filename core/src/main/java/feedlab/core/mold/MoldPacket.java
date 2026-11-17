package feedlab.core.mold;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One MoldUDP64 downstream packet.
 *
 * <p>{@code sequence} is the sequence number of the first message in the packet. For a heartbeat or
 * an end-of-session packet it is the next sequence number the sender will use, which lets a
 * receiver detect messages lost at the tail of a burst.
 */
public record MoldPacket(String session, long sequence, Kind kind, List<byte[]> messages) {

    public enum Kind {
        DATA,
        HEARTBEAT,
        END_OF_SESSION
    }

    public static final int SESSION_LENGTH = 10;
    public static final int MAX_MESSAGES = 0xFFFE;
    public static final int MAX_MESSAGE_LENGTH = 0xFFFF;

    public MoldPacket {
        if (session == null || session.length() > SESSION_LENGTH) {
            throw new IllegalArgumentException("session must be at most 10 characters");
        }
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(session)) {
            throw new IllegalArgumentException("session must be ASCII");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative: " + sequence);
        }
        messages = List.copyOf(messages);
        if (kind == Kind.DATA) {
            if (messages.isEmpty() || messages.size() > MAX_MESSAGES) {
                throw new IllegalArgumentException("a data packet carries 1 to 65534 messages");
            }
            for (byte[] m : messages) {
                if (m.length > MAX_MESSAGE_LENGTH) {
                    throw new IllegalArgumentException("message longer than 65535 bytes");
                }
            }
        } else if (!messages.isEmpty()) {
            throw new IllegalArgumentException(kind + " packets carry no messages");
        }
    }

    public static MoldPacket data(String session, long firstSequence, List<byte[]> messages) {
        return new MoldPacket(session, firstSequence, Kind.DATA, messages);
    }

    public static MoldPacket heartbeat(String session, long nextSequence) {
        return new MoldPacket(session, nextSequence, Kind.HEARTBEAT, List.of());
    }

    public static MoldPacket endOfSession(String session, long nextSequence) {
        return new MoldPacket(session, nextSequence, Kind.END_OF_SESSION, List.of());
    }

    public int messageCount() {
        return messages.size();
    }

    /** The sequence number that follows the last message in this packet. */
    public long nextSequence() {
        return sequence + messages.size();
    }
}
