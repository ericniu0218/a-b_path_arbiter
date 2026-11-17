package feedlab.service.run;

import feedlab.service.domain.FadeWindow;
import feedlab.service.domain.RunConfig;
import feedlab.service.domain.SimulationRun;
import feedlab.service.domain.SimulationRunRepository;
import feedlab.service.domain.TimelineSecond;
import feedlab.service.domain.TimelineSecondRepository;
import feedlab.sim.Main;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and reads runs. Each method is one transaction. */
@Service
public class RunService {

    private final SimulationRunRepository runs;
    private final TimelineSecondRepository timeline;
    private final InputFiles inputFiles;
    private final Clock clock;

    public RunService(
            SimulationRunRepository runs, TimelineSecondRepository timeline, InputFiles inputFiles, Clock clock) {
        this.runs = runs;
        this.timeline = timeline;
        this.inputFiles = inputFiles;
        this.clock = clock;
    }

    /** Checks the request can be carried out and stores it as a queued run. */
    @Transactional
    public UUID create(RunConfig config, List<FadeWindow> fades) {
        inputFiles.resolve(config.inputFile());
        long start = parseTime("start", config.startTime(), Long.MIN_VALUE);
        long end = parseTime("end", config.endTime(), Long.MAX_VALUE);
        if (end <= start) {
            throw new InvalidRunRequestException("end must be later than start");
        }
        SimulationRun run = new SimulationRun(UUID.randomUUID(), clock.instant(), config, fades);
        return runs.save(run).getId();
    }

    private static long parseTime(String field, String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Main.parseTimeOfDay(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidRunRequestException(field + ": " + e.getMessage());
        }
    }

    /** Reads a run and maps it while the transaction is open, so lazy collections can load. */
    @Transactional(readOnly = true)
    public <T> T get(UUID id, Function<SimulationRun, T> mapper) {
        return mapper.apply(runs.findById(id).orElseThrow(() -> new RunNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public <T> List<T> list(Function<SimulationRun, T> mapper) {
        return runs.findAllByOrderByCreatedAtDesc().stream().map(mapper).toList();
    }

    @Transactional(readOnly = true)
    public List<TimelineSecond> timeline(UUID id) {
        if (!runs.existsById(id)) {
            throw new RunNotFoundException(id);
        }
        return timeline.findByRunIdOrderBySecond(id);
    }
}
