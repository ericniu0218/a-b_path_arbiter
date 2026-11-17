package feedlab.core.mold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Test;

class MoldCodecTest {

    @Test
    void encodesTheHeaderFieldsAtTheirSpecifiedOffsets() {
        MoldPacket packet = MoldPacket.data("SESS1", 0x0102030405060708L, List.of(new byte[] {7, 8, 9}));

        byte[] bytes = MoldCodec.encode(packet);

        assertThat(bytes).hasSize(20 + 2 + 3);
        assertThat(new String(bytes, 0, 10, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("SESS1     ");
        ByteBuffer in = ByteBuffer.wrap(bytes);
        assertThat(in.getLong(10)).isEqualTo(0x0102030405060708L);
        assertThat(in.getShort(18)).isEqualTo((short) 1);
        assertThat(in.getShort(20)).isEqualTo((short) 3);
        assertThat(Arrays.copyOfRange(bytes, 22, 25)).containsExactly(7, 8, 9);
    }

    @Test
    void heartbeatHasZeroMessageCountAndNoPayload() {
        byte[] bytes = MoldCodec.encode(MoldPacket.heartbeat("S", 42));

        assertThat(bytes).hasSize(20);
        assertThat(ByteBuffer.wrap(bytes).getShort(18)).isZero();
        MoldPacket decoded = MoldCodec.decode(bytes);
        assertThat(decoded.kind()).isEqualTo(MoldPacket.Kind.HEARTBEAT);
        assertThat(decoded.sequence()).isEqualTo(42);
        assertThat(decoded.nextSequence()).isEqualTo(42);
    }

    @Test
    void endOfSessionUsesTheReservedCount() {
        byte[] bytes = MoldCodec.encode(MoldPacket.endOfSession("S", 99));

        assertThat(ByteBuffer.wrap(bytes).getShort(18) & 0xFFFF).isEqualTo(0xFFFF);
        assertThat(MoldCodec.decode(bytes).kind()).isEqualTo(MoldPacket.Kind.END_OF_SESSION);
    }

    @Test
    void rejectsAPacketShorterThanTheHeader() {
        assertThatThrownBy(() -> MoldCodec.decode(new byte[19])).isInstanceOf(MalformedPacketException.class);
    }

    @Test
    void rejectsATruncatedMessageBlock() {
        byte[] bytes = MoldCodec.encode(MoldPacket.data("S", 1, List.of(new byte[10])));

        assertThatThrownBy(() -> MoldCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)))
                .isInstanceOf(MalformedPacketException.class);
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] bytes = MoldCodec.encode(MoldPacket.data("S", 1, List.of(new byte[10])));

        assertThatThrownBy(() -> MoldCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)))
                .isInstanceOf(MalformedPacketException.class);
    }

    @Test
    void rejectsADataPacketWithNoMessages() {
        assertThatThrownBy(() -> MoldPacket.data("S", 1, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsASessionLongerThanTenCharacters() {
        assertThatThrownBy(() -> MoldPacket.heartbeat("ELEVENCHARS", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Property
    void decodeIsTheInverseOfEncode(
            @ForAll @LongRange(min = 0, max = Long.MAX_VALUE - 100_000) long sequence,
            @ForAll @Size(min = 1, max = 40) List<@Size(max = 200) byte[]> messages,
            @ForAll @IntRange(min = 0, max = 10) int sessionLength) {
        String session = "ABCDEFGHIJ".substring(0, sessionLength);
        MoldPacket original = MoldPacket.data(session, sequence, messages);

        MoldPacket decoded = MoldCodec.decode(MoldCodec.encode(original));

        assertThat(decoded.session()).isEqualTo(session);
        assertThat(decoded.sequence()).isEqualTo(sequence);
        assertThat(decoded.kind()).isEqualTo(MoldPacket.Kind.DATA);
        assertThat(asLists(decoded.messages())).isEqualTo(asLists(messages));
    }

    private static List<List<Byte>> asLists(List<byte[]> messages) {
        List<List<Byte>> out = new ArrayList<>();
        for (byte[] m : messages) {
            List<Byte> bytes = new ArrayList<>();
            for (byte b : m) {
                bytes.add(b);
            }
            out.add(bytes);
        }
        return out;
    }
}
