package feedlab.service.web;

import feedlab.service.run.InputFiles;
import feedlab.service.run.RunLauncher;
import feedlab.service.run.RunService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api")
public class RunController {

    private final RunService runs;
    private final RunLauncher launcher;
    private final InputFiles inputFiles;

    public RunController(RunService runs, RunLauncher launcher, InputFiles inputFiles) {
        this.runs = runs;
        this.launcher = launcher;
        this.inputFiles = inputFiles;
    }

    /** Queues a run and returns at once. Poll the Location until the status is COMPLETED or FAILED. */
    @PostMapping("/runs")
    public ResponseEntity<RunViews.Detail> create(@Valid @RequestBody RunRequest request) {
        UUID id = runs.create(request.toConfig(), request.toFades());
        RunViews.Detail queued = runs.get(id, RunViews.Detail::of);
        launcher.launch(id); // after create() has committed, so the worker can see the row
        return ResponseEntity.accepted()
                .location(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(id)
                        .toUri())
                .body(queued);
    }

    @GetMapping("/runs")
    public List<RunViews.Summary> list() {
        return runs.list(RunViews.Summary::of);
    }

    @GetMapping("/runs/{id}")
    public RunViews.Detail get(@PathVariable UUID id) {
        return runs.get(id, RunViews.Detail::of);
    }

    @GetMapping("/runs/{id}/timeline")
    public List<RunViews.TimelinePoint> timeline(@PathVariable UUID id) {
        return runs.timeline(id).stream().map(RunViews.TimelinePoint::of).toList();
    }

    /** The files a run may use as input. */
    @GetMapping("/inputs")
    public List<String> inputs() {
        return inputFiles.list();
    }
}
