package feedlab.service.run;

import feedlab.service.domain.FadeWindow;
import feedlab.service.domain.RunConfig;
import feedlab.service.domain.SimulationRun;
import feedlab.service.domain.SimulationRunRepository;
import feedlab.service.domain.TimelineSecondRepository;
import feedlab.sim.Simulation;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carries one queued run through to COMPLETED or FAILED.
 *
 * <p>The simulation itself runs outside any database transaction: it can take a while and needs no
 * connection. Only the state changes before and after it are transactional.
 */
@Component
public class RunExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

    private record Job(RunConfig config, List<FadeWindow> fades) {}

    private final SimulationRunRepository runs;
    private final TimelineSecondRepository timeline;
    private final SimulationRunner runner;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RunExecutor(
            SimulationRunRepository runs,
            TimelineSecondRepository timeline,
            SimulationRunner runner,
            TransactionTemplate transaction,
            Clock clock) {
        this.runs = runs;
        this.timeline = timeline;
        this.runner = runner;
        this.transaction = transaction;
        this.clock = clock;
    }

    public void execute(UUID id) {
        Job job = transaction.execute(status -> {
            SimulationRun run = runs.findById(id).orElseThrow(() -> new RunNotFoundException(id));
            run.markRunning(clock.instant());
            return new Job(run.getConfig(), run.getFades());
        });

        try {
            Simulation.Result result = runner.run(job.config(), job.fades());
            transaction.executeWithoutResult(status -> {
                SimulationRun run = runs.findById(id).orElseThrow(() -> new RunNotFoundException(id));
                run.complete(clock.instant(), ResultMapper.headline(result), ResultMapper.latencySummaries(result));
                timeline.saveAll(ResultMapper.timeline(id, result));
            });
            log.info("run {} completed: {} messages", id, result.messages());
        } catch (Exception e) {
            log.warn("run {} failed", id, e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            transaction.executeWithoutResult(status -> runs.findById(id)
                    .ifPresent(run -> run.fail(clock.instant(), message)));
        }
    }
}
