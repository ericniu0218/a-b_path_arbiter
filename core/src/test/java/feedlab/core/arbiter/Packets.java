package feedlab.core.arbiter;

import feedlab.core.mold.MoldPacket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Builders for test packets. Each message's payload is its own sequence number. */
public final class Packets {

    public static final String SESSION = "TEST";

    private Packets() {}

    /** A data packet carrying messages {@code first} to {@code last}, both inclusive. */
    public static MoldPacket range(long first, long last) {
        List<byte[]> messages = new ArrayList<>();
        for (long s = first; s <= last; s++) {
            messages.add(payload(s));
        }
        return MoldPacket.data(SESSION, first, messages);
    }

    public static MoldPacket heartbeat(long nextSequence) {
        return MoldPacket.heartbeat(SESSION, nextSequence);
    }

    public static byte[] payload(long sequence) {
        return ByteBuffer.allocate(8).putLong(sequence).array();
    }

    public static long sequenceIn(byte[] payload) {
        return ByteBuffer.wrap(payload).getLong();
    }
}
