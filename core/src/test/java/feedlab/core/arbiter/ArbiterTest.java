package feedlab.core.arbiter;

import static feedlab.core.arbiter.Packets.heartbeat;
import static feedlab.core.arbiter.Packets.range;
import static org.assertj.core.api.Assertions.assertThat;

import feedlab.core.mold.MoldPacket;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArbiterTest {

    private static final long TIMEOUT = 5_000;

    private RecordingListener out;
    private Arbiter arbiter;

    @BeforeEach
    void setUp() {
        out = new RecordingListener();
        arbiter = new Arbiter(ArbiterConfig.withGapTimeout(TIMEOUT), out);
    }

    private static List<Long> seqs(long first, long last) {
        return LongStream.rangeClosed(first, last).boxed().toList();
    }

    @Test
    void releasesInOrderPacketsImmediately() {
        arbiter.onPacket(Line.A, range(1, 3), 100);
        arbiter.onPacket(Line.A, range(4, 5), 200);

        assertThat(out.sequences()).isEqualTo(seqs(1, 5));
        assertThat(out.messages.get(0).atNanos()).isEqualTo(100);
        assertThat(out.messages.get(4).atNanos()).isEqualTo(200);
        assertThat(out.gaps).isEmpty();
    }

    @Test
    void theFirstCopyWinsAndTheSecondIsDiscarded() {
        arbiter.onPacket(Line.A, range(1, 3), 100);
        arbiter.onPacket(Line.B, range(1, 3), 150);

        assertThat(out.sequences()).isEqualTo(seqs(1, 3));
        assertThat(out.messages).allMatch(m -> m.winner() == Line.A);
        assertThat(arbiter.messagesDelivered(Line.A)).isEqualTo(3);
        assertThat(arbiter.messagesDiscarded(Line.B)).isEqualTo(3);
    }

    @Test
    void theOtherLineFillsAHoleAndBufferedPacketsAreReleasedWithIt() {
        arbiter.onPacket(Line.A, range(1, 2), 100);
        arbiter.onPacket(Line.A, range(5, 6), 110); // 3-4 lost on A
        assertThat(out.sequences()).isEqualTo(seqs(1, 2));

        arbiter.onPacket(Line.B, range(1, 2), 300);
        arbiter.onPacket(Line.B, range(3, 4), 310);

        assertThat(out.sequences()).isEqualTo(seqs(1, 6));
        assertThat(out.gaps).isEmpty();
        assertThat(out.messages.get(2).winner()).isEqualTo(Line.B);
        assertThat(out.messages.get(4).winner()).isEqualTo(Line.A);
        // The buffered packet from A is released at the moment B fills the hole.
        assertThat(out.messages.get(4).atNanos()).isEqualTo(310);
    }

    @Test
    void reportsAGapWhenNeitherLineFillsTheHoleInTime() {
        arbiter.onPacket(Line.A, range(1, 2), 100);
        arbiter.onPacket(Line.A, range(5, 6), 110);
        arbiter.advanceTo(110 + TIMEOUT - 1);
        assertThat(out.gaps).isEmpty();

        arbiter.advanceTo(110 + TIMEOUT);

        assertThat(out.gaps).containsExactly(new RecordingListener.Gap(3, 5, 110 + TIMEOUT));
        assertThat(out.sequences()).containsExactly(1L, 2L, 5L, 6L);
        assertThat(arbiter.messagesLost()).isEqualTo(2);
    }

    @Test
    void theGapDeadlineDoesNotDependOnWhenTheArbiterIsNextCalled() {
        arbiter.onPacket(Line.A, range(1, 2), 100);
        arbiter.onPacket(Line.A, range(5, 6), 110);

        arbiter.onPacket(Line.A, range(7, 7), 1_000_000);

        assertThat(out.gaps).containsExactly(new RecordingListener.Gap(3, 5, 110 + TIMEOUT));
        assertThat(out.messages.get(2).atNanos()).isEqualTo(110 + TIMEOUT);
        assertThat(out.messages.get(4).atNanos()).isEqualTo(1_000_000);
    }

    @Test
    void aCopyThatArrivesAfterTheGapWasDeclaredIsDiscarded() {
        arbiter.onPacket(Line.A, range(1, 2), 100);
        arbiter.onPacket(Line.A, range(5, 6), 110);
        arbiter.advanceTo(110 + TIMEOUT);

        arbiter.onPacket(Line.B, range(3, 4), 110 + TIMEOUT + 1);

        assertThat(out.sequences()).containsExactly(1L, 2L, 5L, 6L);
        assertThat(arbiter.messagesDiscarded(Line.B)).isEqualTo(2);
    }

    @Test
    void aHeartbeatRevealsMessagesLostAtTheTail() {
        arbiter.onPacket(Line.A, range(1, 3), 100);
        arbiter.onPacket(Line.A, heartbeat(6), 1_000); // 4-5 were sent but never arrived

        arbiter.advanceTo(1_000 + TIMEOUT);

        assertThat(out.gaps).containsExactly(new RecordingListener.Gap(4, 6, 1_000 + TIMEOUT));
        assertThat(arbiter.nextExpectedSequence()).isEqualTo(6);
    }

    @Test
    void aHeartbeatOnAHealthyStreamDoesNothing() {
        arbiter.onPacket(Line.A, range(1, 3), 100);
        arbiter.onPacket(Line.A, heartbeat(4), 1_000);
        arbiter.advanceTo(1_000_000);

        assertThat(out.gaps).isEmpty();
        assertThat(out.sequences()).isEqualTo(seqs(1, 3));
    }

    @Test
    void deliversOnlyTheNewMessagesOfAPartlyOverlappingPacket() {
        arbiter.onPacket(Line.A, range(1, 3), 100);
        arbiter.onPacket(Line.B, range(2, 5), 200);

        assertThat(out.sequences()).isEqualTo(seqs(1, 5));
        assertThat(arbiter.messagesDelivered(Line.B)).isEqualTo(2);
        assertThat(arbiter.messagesDiscarded(Line.B)).isEqualTo(2);
    }

    @Test
    void reorderedPacketsOnOneLineAreDeliveredInSequence() {
        arbiter.onPacket(Line.A, range(4, 6), 100);
        arbiter.onPacket(Line.A, range(1, 3), 101);

        assertThat(out.sequences()).isEqualTo(seqs(1, 6));
        assertThat(out.gaps).isEmpty();
    }

    @Test
    void eachHoleGetsItsOwnTimeoutOnceItReachesTheHeadOfTheStream() {
        arbiter.onPacket(Line.A, range(1, 1), 0);
        arbiter.onPacket(Line.A, range(3, 3), 10); // hole at 2
        arbiter.onPacket(Line.A, range(5, 5), 20); // hole at 4

        arbiter.advanceTo(1_000_000);

        assertThat(out.gaps)
                .containsExactly(
                        new RecordingListener.Gap(2, 3, 10 + TIMEOUT),
                        new RecordingListener.Gap(4, 5, 10 + 2 * TIMEOUT));
        assertThat(out.sequences()).containsExactly(1L, 3L, 5L);
    }

    @Test
    void givesUpOnTheOldestHoleWhenTheReorderBufferIsFull() {
        out = new RecordingListener();
        arbiter = new Arbiter(new ArbiterConfig(1, Long.MAX_VALUE / 4, 2), out);

        arbiter.onPacket(Line.A, range(3, 3), 10);
        arbiter.onPacket(Line.A, range(5, 5), 20);
        assertThat(out.messages).isEmpty();

        arbiter.onPacket(Line.A, range(7, 7), 30);

        assertThat(out.gaps).containsExactly(new RecordingListener.Gap(1, 3, 30));
        assertThat(out.sequences()).containsExactly(3L);
        assertThat(arbiter.bufferedPackets()).isEqualTo(2);
        assertThat(arbiter.maxBufferedPackets()).isEqualTo(3);
    }

    @Test
    void ignoresPacketsFromAnotherSession() {
        arbiter.onPacket(Line.A, range(1, 2), 100);
        arbiter.onPacket(Line.B, MoldPacket.data("OTHER", 3, List.of(new byte[1])), 110);

        assertThat(out.sequences()).isEqualTo(seqs(1, 2));
        assertThat(arbiter.foreignPackets()).isEqualTo(1);
    }

    @Test
    void honoursAConfiguredFirstSequence() {
        out = new RecordingListener();
        arbiter = new Arbiter(new ArbiterConfig(1000, TIMEOUT, 16), out);

        arbiter.onPacket(Line.A, range(1000, 1002), 5);

        assertThat(out.sequences()).isEqualTo(seqs(1000, 1002));
    }
}
