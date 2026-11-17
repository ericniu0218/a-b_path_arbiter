package feedlab.sim;

import static org.assertj.core.api.Assertions.assertThat;

import feedlab.core.arbiter.Line;
import feedlab.sim.PathModel.Fade;
import feedlab.sim.Simulation.Result;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end runs over a small synthetic file: reader, packetizer, both paths, arbiter, metrics. */
class SimulationTest {

    private static final long WIRELESS_LATENCY = 4_000_000;
    private static final long FIBER_LATENCY = 6_500_000;

    /** The histograms keep three significant digits, so values read back are within 0.1%. */
    private static final Percentage HISTOGRAM_PRECISION = Percentage.withPercentage(0.1);

    @TempDir
    static Path dir;

    private static Path file;
    private static long messagesInFile;

    @BeforeAll
    static void writeInput() throws IOException {
        file = dir.resolve("synthetic.itch");
        messagesInFile = SyntheticItchWriter.write(file, 6, 5_000, 3);
    }

    private static PathModel wireless(double loss, long bps, List<Fade> fades) {
        return new PathModel("wireless", WIRELESS_LATENCY, 0, loss, bps, 2_000_000, fades);
    }

    private static PathModel fiber(double loss) {
        return new PathModel("fiber", FIBER_LATENCY, 0, loss, 0, 0, List.of());
    }

    private static Result run(PathModel a, PathModel b, long gapTimeout) throws IOException {
        try (ItchFileReader reader = new ItchFileReader(file)) {
            return Simulation.run(
                    new Packetizer(reader::next, "TEST", Long.MAX_VALUE),
                    new Simulation.Config(a, b, gapTimeout, 42, 1_000_000));
        }
    }

    @Test
    void readsEveryMessageInTheFile() throws IOException {
        Result r = run(wireless(0, 0, List.of()), fiber(0), 5_000_000);

        assertThat(r.messages()).isEqualTo(messagesInFile);
        assertThat(r.clear().messagesDelivered()).isEqualTo(messagesInFile);
    }

    @Test
    void withTwoHealthyPathsTheFasterOneWinsEverythingAtItsOwnLatency() throws IOException {
        Result r = run(wireless(0, 0, List.of()), fiber(0), 5_000_000);

        assertThat(r.clear().wins(Line.A)).isEqualTo(messagesInFile);
        assertThat(r.clear().wins(Line.B)).isZero();
        assertThat(r.clear().arbitratedLatency().getMinValue()).isCloseTo(WIRELESS_LATENCY, HISTOGRAM_PRECISION);
        assertThat(r.clear().arbitratedLatency().getMaxValue()).isCloseTo(WIRELESS_LATENCY, HISTOGRAM_PRECISION);
        assertThat(r.gaps()).isZero();
    }

    @Test
    void theSlowPathCoversEveryLossOnTheFastPathWhenTheTimeoutIsLongEnough() throws IOException {
        List<Fade> fades = List.of(new Fade(2_000_000_000L, 4_000_000_000L, 0.3));

        Result r = run(wireless(0, 0, fades), fiber(0), 5_000_000);

        assertThat(r.fade().lineMessagesLost(Line.A)).isPositive();
        assertThat(r.fade().messagesLost()).isZero();
        assertThat(r.clear().messagesLost()).isZero();
        assertThat(r.fade().messagesDelivered() + r.clear().messagesDelivered()).isEqualTo(messagesInFile);
        assertThat(r.fade().wins(Line.B)).isPositive();
        // Nothing in the fade is slower than the fiber path, and clear traffic is unaffected.
        assertThat(r.fade().arbitratedLatency().getMaxValue()).isCloseTo(FIBER_LATENCY, HISTOGRAM_PRECISION);
        assertThat(r.clear().arbitratedLatency().getValueAtPercentile(50)).isCloseTo(WIRELESS_LATENCY, HISTOGRAM_PRECISION);
    }

    @Test
    void aTimeoutShorterThanThePathDifferenceLosesWhatTheSlowPathWouldHaveCovered() throws IOException {
        List<Fade> fades = List.of(new Fade(2_000_000_000L, 4_000_000_000L, 0.3));

        Result r = run(wireless(0, 0, fades), fiber(0), 1_000_000); // path difference is 2.5 ms

        assertThat(r.fade().messagesLost()).isPositive();
        assertThat(r.gaps()).isPositive();
    }

    @Test
    void aNarrowLinkDropsBurstsThatTheOtherPathThenCovers() throws IOException {
        Result r = run(wireless(0, 20_000_000, List.of()), fiber(0), 5_000_000);

        assertThat(r.pathA().packetsDroppedAtQueue()).isPositive();
        assertThat(r.burst().windowsOverCapacity()).isPositive();
        assertThat(r.clear().messagesLost()).isZero();
        assertThat(r.clear().wins(Line.B)).isPositive();
    }

    @Test
    void messagesLostOnBothPathsAreReportedAsGapsAndTheBooksBalance() throws IOException {
        Result r = run(wireless(0.2, 0, List.of()), fiber(0.2), 5_000_000);

        long delivered = r.clear().messagesDelivered();
        long lost = r.clear().messagesLost();
        assertThat(lost).isPositive();
        assertThat(delivered + lost).isEqualTo(messagesInFile);
        assertThat(r.timeline().stream().mapToLong(row -> row.delivered() + row.lost()).sum()).isEqualTo(messagesInFile);
        assertThat(r.timeline().stream().mapToLong(Simulation.TimelineRow::winsA).sum()).isEqualTo(r.clear().wins(Line.A));
    }

    @Test
    void theSameSeedReproducesTheRunExactly() throws IOException {
        List<Fade> fades = List.of(new Fade(1_000_000_000L, 3_000_000_000L, 0.4));
        PathModel a = new PathModel("wireless", WIRELESS_LATENCY, 2_000, 1e-3, 50_000_000, 2_000_000, fades);
        PathModel b = new PathModel("fiber", FIBER_LATENCY, 1_000, 1e-4, 0, 0, List.of());

        String first = Report.text("x", run(a, b, 5_000_000)) + Report.timelineCsv(run(a, b, 5_000_000));
        String second = Report.text("x", run(a, b, 5_000_000)) + Report.timelineCsv(run(a, b, 5_000_000));

        assertThat(second).isEqualTo(first);
    }
}
