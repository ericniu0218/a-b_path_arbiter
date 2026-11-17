package feedlab.service.run;

import feedlab.service.domain.RunStatus;
import feedlab.service.domain.SimulationRun;
import feedlab.service.domain.SimulationRunRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Hands queued runs to the background worker, and cleans up runs orphaned by a restart. */
@Component
public class RunLauncher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RunLauncher.class);

    private final TaskExecutor runTaskExecutor;
    private final RunExecutor executor;
    private final SimulationRunRepository runs;
    private final Clock clock;

    public RunLauncher(TaskExecutor runTaskExecutor, RunExecutor executor, SimulationRunRepository runs, Clock clock) {
        this.runTaskExecutor = runTaskExecutor;
        this.executor = executor;
        this.runs = runs;
        this.clock = clock;
    }

    /** Call only after the transaction that created the run has committed. */
    public void launch(UUID id) {
        runTaskExecutor.execute(() -> executor.execute(id));
    }

    /** The queue lives in memory, so a run that was queued or running when the process stopped will never finish. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SimulationRun> orphans = runs.findByStatusIn(List.of(RunStatus.QUEUED, RunStatus.RUNNING));
        for (SimulationRun run : orphans) {
            run.fail(clock.instant(), "interrupted by a service restart");
        }
        if (!orphans.isEmpty()) {
            log.warn("marked {} interrupted run(s) as failed", orphans.size());
        }
    }
}
