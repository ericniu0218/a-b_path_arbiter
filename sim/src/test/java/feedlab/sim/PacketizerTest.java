package feedlab.sim;

import static org.assertj.core.api.Assertions.assertThat;

import feedlab.core.mold.MoldCodec;
import feedlab.core.mold.MoldPacket;
import feedlab.sim.ItchFileReader.ItchMessage;
import feedlab.sim.Packetizer.SentPacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacketizerTest {

    private static ItchMessage message(long timestamp, int length) {
        return new ItchMessage(timestamp, new byte[length]);
    }

    private static List<SentPacket> packetize(List<ItchMessage> messages, long maxMessages) {
        Iterator<ItchMessage> it = messages.iterator();
        Packetizer packetizer = new Packetizer(() -> it.hasNext() ? it.next() : null, "S", maxMessages);
        List<SentPacket> out = new ArrayList<>();
        SentPacket p;
        while ((p = packetizer.next()) != null) {
            out.add(p);
        }
        return out;
    }

    @Test
    void messagesInTheSameBurstShareAPacketSentAtTheLastTimestamp() {
        List<SentPacket> out = packetize(List.of(message(1_000, 36), message(4_000, 36), message(9_000, 36)), Long.MAX_VALUE);

        SentPacket data = out.get(0);
        assertThat(data.packet().messageCount()).isEqualTo(3);
        assertThat(data.packet().sequence()).isEqualTo(1);
        assertThat(data.sendNanos()).isEqualTo(9_000);
    }

    @Test
    void aMessageOutsideThePackWindowStartsANewPacket() {
        List<SentPacket> out = packetize(List.of(message(1_000, 36), message(50_000, 36)), Long.MAX_VALUE);

        assertThat(out.get(0).packet().messageCount()).isEqualTo(1);
        assertThat(out.get(1).packet().messageCount()).isEqualTo(1);
        assertThat(out.get(1).packet().sequence()).isEqualTo(2);
        assertThat(out.get(1).sendNanos()).isEqualTo(50_000);
    }

    @Test
    void packetsNeverExceedThePayloadLimitAndSequenceNumbersAreContinuous() {
        List<ItchMessage> messages = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            messages.add(message(1_000, 36)); // all at once
        }

        List<SentPacket> out = packetize(messages, Long.MAX_VALUE);

        long next = 1;
        for (SentPacket p : out) {
            assertThat(MoldCodec.encodedLength(p.packet())).isLessThanOrEqualTo(Packetizer.DEFAULT_MAX_PAYLOAD_BYTES);
            assertThat(p.wireBytes()).isEqualTo(MoldCodec.encodedLength(p.packet()) + Packetizer.FRAME_OVERHEAD_BYTES);
            assertThat(p.packet().sequence()).isEqualTo(next);
            next = p.packet().nextSequence();
        }
        assertThat(next).isEqualTo(501);
        // (1400 - 20) / 38 = 36 messages per full packet
        assertThat(out.get(0).packet().messageCount()).isEqualTo(36);
    }

    @Test
    void aFinalHeartbeatCarriesTheNextSequenceNumber() {
        List<SentPacket> out = packetize(List.of(message(1_000, 36), message(2_000, 36)), Long.MAX_VALUE);

        SentPacket last = out.get(out.size() - 1);
        assertThat(last.packet().kind()).isEqualTo(MoldPacket.Kind.HEARTBEAT);
        assertThat(last.packet().sequence()).isEqualTo(3);
        assertThat(last.sendNanos()).isEqualTo(2_000 + Packetizer.DEFAULT_HEARTBEAT_INTERVAL_NANOS);
    }

    @Test
    void anIdleStreamGetsAHeartbeatEveryInterval() {
        long second = Packetizer.DEFAULT_HEARTBEAT_INTERVAL_NANOS;
        List<SentPacket> out = packetize(List.of(message(0, 36), message(2 * second + 500, 36)), Long.MAX_VALUE);

        assertThat(out).extracting(p -> p.packet().kind())
                .containsExactly(
                        MoldPacket.Kind.DATA,
                        MoldPacket.Kind.HEARTBEAT,
                        MoldPacket.Kind.HEARTBEAT,
                        MoldPacket.Kind.DATA,
                        MoldPacket.Kind.HEARTBEAT);
        assertThat(out.get(1).sendNanos()).isEqualTo(second);
        assertThat(out.get(2).sendNanos()).isEqualTo(2 * second);
        assertThat(out.get(1).packet().sequence()).isEqualTo(2);
    }

    @Test
    void stopsAtTheMessageLimit() {
        List<SentPacket> out = packetize(List.of(message(1, 36), message(2, 36), message(3, 36)), 2);

        assertThat(out.stream().mapToInt(p -> p.packet().messageCount()).sum()).isEqualTo(2);
    }

    @Test
    void anEmptyInputProducesNothing() {
        assertThat(packetize(List.of(), Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void sendTimesNeverGoBackwards() {
        List<SentPacket> out = packetize(List.of(message(100_000, 36), message(20_000, 36)), Long.MAX_VALUE);

        assertThat(out.get(1).sendNanos()).isGreaterThanOrEqualTo(out.get(0).sendNanos());
    }
}
