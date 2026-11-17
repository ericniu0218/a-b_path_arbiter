package feedlab.service;

import static org.assertj.core.api.Assertions.assertThat;

import feedlab.service.domain.FadeWindow;
import feedlab.service.domain.RunConfig;
import feedlab.service.web.RunRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunRequestTest {

    @Test
    void aRequestWithOnlyAFileGetsTheDocumentedDefaults() {
        RunRequest request = new RunRequest("day.itch", null, null, null, null, null, null, null);

        RunConfig config = request.toConfig();

        assertThat(config.inputFile()).isEqualTo("day.itch");
        assertThat(config.aLatencyNanos()).isEqualTo(3_980_000);
        assertThat(config.aBandwidthBps()).isEqualTo(100_000_000);
        assertThat(config.aMaxQueueNanos()).isEqualTo(2_000_000);
        assertThat(config.bLatencyNanos()).isEqualTo(6_550_000);
        assertThat(config.gapTimeoutNanos()).isEqualTo(5_000_000);
        assertThat(config.seed()).isEqualTo(42);
        assertThat(request.toFades()).isEmpty();
    }

    @Test
    void givenFieldsOverrideDefaultsAndTheRestStayDefault() {
        RunRequest.LineA lineA = new RunRequest.LineA(4_000.0, null, 0.01, 0.0, null, null);
        RunRequest request = new RunRequest("day.itch", "09:30:00", "09:31:00", 500L, lineA, null, 2.5, 7L);

        RunConfig config = request.toConfig();

        assertThat(config.aLatencyNanos()).isEqualTo(4_000_000);
        assertThat(config.aJitterNanos()).isEqualTo(2_000);
        assertThat(config.aLoss()).isEqualTo(0.01);
        assertThat(config.aBandwidthBps()).isZero();
        assertThat(config.gapTimeoutNanos()).isEqualTo(2_500_000);
        assertThat(config.seed()).isEqualTo(7);
        assertThat(config.maxMessages()).isEqualTo(500);
    }

    @Test
    void aFadeIsConvertedFromStartAndLengthToStartAndEndOffsets() {
        RunRequest.LineA lineA =
                new RunRequest.LineA(null, null, null, null, null, List.of(new RunRequest.Fade(20.0, 10.0, 0.3)));
        RunRequest request = new RunRequest("day.itch", null, null, null, lineA, null, null, null);

        assertThat(request.toFades()).containsExactly(new FadeWindow(20_000_000_000L, 30_000_000_000L, 0.3));
    }
}
