package feedlab.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import feedlab.sim.PathModel.Fade;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class PathSimulatorTest {

    private static PathModel path(long latency, long jitter, double loss, long bps, long maxQueue, List<Fade> fades) {
        return new PathModel("test", latency, jitter, loss, bps, maxQueue, fades);
    }

    @Test
    void aCleanUnlimitedPathAddsExactlyItsLatency() {
        PathSimulator sim = new PathSimulator(path(4_000_000, 0, 0, 0, 0, List.of()), 1);

        assertThat(sim.transmit(1_000, 1_000)).hasValue(4_001_000);
        assertThat(sim.packetsLost()).isZero();
    }

    @Test
    void jitterStaysWithinItsBound() {
        PathSimulator sim = new PathSimulator(path(1_000, 50, 0, 0, 0, List.of()), 1);

        for (int i = 0; i < 1_000; i++) {
            long arrival = sim.transmit(i * 10L, 100).orElseThrow();
            assertThat(arrival - i * 10L).isBetween(1_000L, 1_049L);
        }
    }

    @Test
    void aBusyLinkQueuesPacketsBehindEachOther() {
        // 1 Mbps: a 1250-byte packet (10,000 bits) takes 10 ms to send.
        PathSimulator sim = new PathSimulator(path(0, 0, 0, 1_000_000, 100_000_000, List.of()), 1);

        assertThat(sim.transmit(0, 1_250)).hasValue(10_000_000);
        assertThat(sim.transmit(0, 1_250)).hasValue(20_000_000);
        assertThat(sim.transmit(25_000_000, 1_250)).hasValue(35_000_000); // link idle again
        assertThat(sim.maxQueueDelayNanos()).isEqualTo(10_000_000);
    }

    @Test
    void aPacketThatWouldWaitTooLongIsDropped() {
        PathSimulator sim = new PathSimulator(path(0, 0, 0, 1_000_000, 15_000_000, List.of()), 1);

        sim.transmit(0, 1_250); // link busy until 10 ms
        sim.transmit(0, 1_250); // waits 10 ms, busy until 20 ms
        OptionalLong third = sim.transmit(0, 1_250); // would wait 20 ms

        assertThat(third).isEmpty();
        assertThat(sim.packetsDroppedAtQueue()).isEqualTo(1);
        assertThat(sim.packetsLost()).isZero();
    }

    @Test
    void lossRisesDuringAFadeAndOnlyThen() {
        Fade fade = new Fade(1_000_000, 2_000_000, 0.5);
        PathSimulator sim = new PathSimulator(path(0, 0, 0, 0, 0, List.of(fade)), 7);

        int lostBefore = 0;
        int lostDuring = 0;
        int lostAfter = 0;
        for (int i = 0; i < 30_000; i++) {
            long t = i * 100L; // 0 to 3 ms
            boolean lost = sim.transmit(t, 100).isEmpty();
            if (lost && t < 1_000_000) {
                lostBefore++;
            } else if (lost && t < 2_000_000) {
                lostDuring++;
            } else if (lost) {
                lostAfter++;
            }
        }

        assertThat(lostBefore).isZero();
        assertThat(lostAfter).isZero();
        assertThat(lostDuring / 10_000.0).isCloseTo(0.5, within(0.03));
    }

    @Test
    void fadeOffsetsAreMeasuredFromTheFirstPacket() {
        Fade fade = new Fade(0, 1_000, 1.0);
        PathSimulator sim = new PathSimulator(path(0, 0, 0, 0, 0, List.of(fade)), 1);

        assertThat(sim.transmit(34_200_000_000_000L, 100)).isEmpty();
        assertThat(sim.transmit(34_200_000_000_500L, 100)).isEmpty();
        assertThat(sim.transmit(34_200_000_001_000L, 100)).isPresent();
    }

    @Test
    void theSameSeedGivesTheSameOutcomes() {
        PathModel model = path(1_000, 100, 0.2, 0, 0, List.of());
        PathSimulator first = new PathSimulator(model, 99);
        PathSimulator second = new PathSimulator(model, 99);

        for (int i = 0; i < 1_000; i++) {
            assertThat(second.transmit(i, 100)).isEqualTo(first.transmit(i, 100));
        }
    }
}
