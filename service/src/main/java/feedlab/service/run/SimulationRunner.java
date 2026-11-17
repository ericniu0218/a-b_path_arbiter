package feedlab.service.run;

import feedlab.service.domain.FadeWindow;
import feedlab.service.domain.RunConfig;
import feedlab.sim.ItchFileReader;
import feedlab.sim.Main;
import feedlab.sim.Packetizer;
import feedlab.sim.PathModel;
import feedlab.sim.Simulation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

/** Turns a stored run configuration into a simulator run. Holds no state and touches no database. */
@Component
public class SimulationRunner {

    private static final long BURST_WINDOW_NANOS = 1_000_000;

    private final InputFiles inputFiles;

    public SimulationRunner(InputFiles inputFiles) {
        this.inputFiles = inputFiles;
    }

    public Simulation.Result run(RunConfig config, List<FadeWindow> fades) throws IOException {
        Path file = inputFiles.resolve(config.inputFile());

        PathModel lineA = new PathModel(
                "wireless",
                config.aLatencyNanos(),
                config.aJitterNanos(),
                config.aLoss(),
                config.aBandwidthBps(),
                config.aMaxQueueNanos(),
                fades.stream()
                        .map(f -> new PathModel.Fade(f.startOffsetNanos(), f.endOffsetNanos(), f.loss()))
                        .toList());
        PathModel lineB = new PathModel(
                "fiber", config.bLatencyNanos(), config.bJitterNanos(), config.bLoss(), 0, 0, List.of());
        Simulation.Config simulation =
                new Simulation.Config(lineA, lineB, config.gapTimeoutNanos(), config.seed(), BURST_WINDOW_NANOS);

        long start = config.startTime() == null ? Long.MIN_VALUE : Main.parseTimeOfDay(config.startTime());
        long end = config.endTime() == null ? Long.MAX_VALUE : Main.parseTimeOfDay(config.endTime());
        long maxMessages = config.maxMessages() == null ? Long.MAX_VALUE : config.maxMessages();

        try (ItchFileReader reader = new ItchFileReader(file)) {
            return Simulation.run(
                    new Packetizer(Main.between(reader::next, start, end), "FEEDLAB", maxMessages), simulation);
        }
    }
}
