package feedlab.core.mold;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes MoldUDP64 downstream packets.
 *
 * <pre>
 * offset  length  field
 * 0       10      session (ASCII, space padded)
 * 10      8       sequence number of the first message (big endian)
 * 18      2       message count (0 = heartbeat, 0xFFFF = end of session)
 * 20      ...     message blocks: 2-byte length, then the message bytes
 * </pre>
 */
public final class MoldCodec {

    public static final int HEADER_LENGTH = 20;
    private static final int END_OF_SESSION_COUNT = 0xFFFF;

    private MoldCodec() {}

    public static int encodedLength(MoldPacket packet) {
        int length = HEADER_LENGTH;
        for (byte[] m : packet.messages()) {
            length += 2 + m.length;
        }
        return length;
    }

    public static byte[] encode(MoldPacket packet) {
        ByteBuffer out = ByteBuffer.allocate(encodedLength(packet));
        byte[] session = packet.session().getBytes(StandardCharsets.US_ASCII);
        out.put(session);
        for (int i = session.length; i < MoldPacket.SESSION_LENGTH; i++) {
            out.put((byte) ' ');
        }
        out.putLong(packet.sequence());
        int count = switch (packet.kind()) {
            case DATA -> packet.messageCount();
            case HEARTBEAT -> 0;
            case END_OF_SESSION -> END_OF_SESSION_COUNT;
        };
        out.putShort((short) count);
        for (byte[] m : packet.messages()) {
            out.putShort((short) m.length);
            out.put(m);
        }
        return out.array();
    }

    public static MoldPacket decode(byte[] bytes) {
        if (bytes.length < HEADER_LENGTH) {
            throw new MalformedPacketException("packet shorter than the 20-byte header: " + bytes.length);
        }
        ByteBuffer in = ByteBuffer.wrap(bytes);
        byte[] sessionBytes = new byte[MoldPacket.SESSION_LENGTH];
        in.get(sessionBytes);
        String session = new String(sessionBytes, StandardCharsets.US_ASCII).stripTrailing();
        long sequence = in.getLong();
        if (sequence < 0) {
            throw new MalformedPacketException("sequence number out of range");
        }
        int count = in.getShort() & 0xFFFF;

        if (count == 0 || count == END_OF_SESSION_COUNT) {
            if (in.hasRemaining()) {
                throw new MalformedPacketException("unexpected payload after a heartbeat or end-of-session header");
            }
            return count == 0
                    ? MoldPacket.heartbeat(session, sequence)
                    : MoldPacket.endOfSession(session, sequence);
        }

        List<byte[]> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (in.remaining() < 2) {
                throw new MalformedPacketException("truncated before message block " + i);
            }
            int length = in.getShort() & 0xFFFF;
            if (in.remaining() < length) {
                throw new MalformedPacketException("message block " + i + " is truncated");
            }
            byte[] message = new byte[length];
            in.get(message);
            messages.add(message);
        }
        if (in.hasRemaining()) {
            throw new MalformedPacketException("trailing bytes after the last message block");
        }
        return MoldPacket.data(session, sequence, messages);
    }
}
