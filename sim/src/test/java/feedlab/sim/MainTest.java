package feedlab.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feedlab.sim.ItchFileReader.ItchMessage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void parsesATimeOfDayIntoNanosecondsSinceMidnight() {
        assertThat(Main.parseTimeOfDay("09:30:00")).isEqualTo(34_200_000_000_000L);
        assertThat(Main.parseTimeOfDay("00:00:01.5")).isEqualTo(1_500_000_000L);
    }

    @Test
    void rejectsAMalformedTimeOfDay() {
        assertThatThrownBy(() -> Main.parseTimeOfDay("9.30")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Main.parseTimeOfDay("a:b:c")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsOnlyMessagesInsideTheTimeWindow() {
        Iterator<ItchMessage> it = List.of(
                        new ItchMessage(5, new byte[0]),
                        new ItchMessage(10, new byte[0]),
                        new ItchMessage(15, new byte[0]),
                        new ItchMessage(20, new byte[0]),
                        new ItchMessage(25, new byte[0]))
                .iterator();
        Supplier<ItchMessage> windowed = Main.between(() -> it.hasNext() ? it.next() : null, 10, 20);

        List<Long> kept = new ArrayList<>();
        ItchMessage m;
        while ((m = windowed.get()) != null) {
            kept.add(m.timestampNanos());
        }

        assertThat(kept).containsExactly(10L, 15L);
    }

    @Test
    void parsesAFadeAsStartLengthAndLoss() {
        PathModel.Fade fade = Main.parseFade("20:10:0.3");

        assertThat(fade.startOffsetNanos()).isEqualTo(20_000_000_000L);
        assertThat(fade.endOffsetNanos()).isEqualTo(30_000_000_000L);
        assertThat(fade.lossProbability()).isEqualTo(0.3);
    }
}
