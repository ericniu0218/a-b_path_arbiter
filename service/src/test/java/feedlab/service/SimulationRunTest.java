package feedlab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feedlab.service.domain.RunResult;
import feedlab.service.domain.RunStatus;
import feedlab.service.domain.SimulationRun;
import feedlab.service.web.RunRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationRunTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final RunResult RESULT = new RunResult(10L, 5L, 1L, 0L, 0L, 1.0, 2.0, 0L, 1L, 0);

    private static SimulationRun queued() {
        RunRequest request = new RunRequest("day.itch", null, null, null, null, null, null, null);
        return new SimulationRun(UUID.randomUUID(), T0, request.toConfig(), List.of());
    }

    @Test
    void aNewRunIsQueuedWithNoResult() {
        SimulationRun run = queued();

        assertThat(run.getStatus()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.getResult()).isNull();
    }

    @Test
    void aRunMovesFromQueuedToRunningToCompleted() {
        SimulationRun run = queued();

        run.markRunning(T0.plusSeconds(1));
        run.complete(T0.plusSeconds(2), RESULT, List.of());

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getStartedAt()).isEqualTo(T0.plusSeconds(1));
        assertThat(run.getFinishedAt()).isEqualTo(T0.plusSeconds(2));
        assertThat(run.getResult()).isEqualTo(RESULT);
    }

    @Test
    void aRunCannotCompleteWithoutHavingStarted() {
        assertThatThrownBy(() -> queued().complete(T0, RESULT, List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRunCannotStartTwice() {
        SimulationRun run = queued();
        run.markRunning(T0);

        assertThatThrownBy(() -> run.markRunning(T0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aQueuedOrRunningRunCanFailButAFinishedOneCannot() {
        SimulationRun run = queued();
        run.fail(T0, "boom");

        assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("boom");
        assertThatThrownBy(() -> run.fail(T0, "again")).isInstanceOf(IllegalStateException.class);
    }
}
