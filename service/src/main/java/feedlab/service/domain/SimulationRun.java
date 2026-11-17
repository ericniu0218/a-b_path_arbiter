package feedlab.service.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One requested simulation and, once it has finished, its results.
 *
 * <p>State moves one way: QUEUED, then RUNNING, then COMPLETED or FAILED.
 */
@Entity
@Table(name = "simulation_run")
public class SimulationRun {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Embedded
    private RunConfig config;

    @ElementCollection
    @CollectionTable(name = "run_fade", joinColumns = @JoinColumn(name = "run_id"))
    @OrderColumn(name = "position")
    private List<FadeWindow> fades = new ArrayList<>();

    @Embedded
    private RunResult result;

    @ElementCollection
    @CollectionTable(name = "latency_summary", joinColumns = @JoinColumn(name = "run_id"))
    private List<LatencySummary> latencySummaries = new ArrayList<>();

    protected SimulationRun() {}

    public SimulationRun(UUID id, Instant createdAt, RunConfig config, List<FadeWindow> fades) {
        this.id = id;
        this.createdAt = createdAt;
        this.status = RunStatus.QUEUED;
        this.config = config;
        this.fades.addAll(fades);
    }

    public void markRunning(Instant now) {
        requireStatus(RunStatus.QUEUED);
        status = RunStatus.RUNNING;
        startedAt = now;
    }

    public void complete(Instant now, RunResult result, List<LatencySummary> summaries) {
        requireStatus(RunStatus.RUNNING);
        status = RunStatus.COMPLETED;
        finishedAt = now;
        this.result = result;
        latencySummaries.clear();
        latencySummaries.addAll(summaries);
    }

    public void fail(Instant now, String message) {
        if (status == RunStatus.COMPLETED || status == RunStatus.FAILED) {
            throw new IllegalStateException("run " + id + " has already finished as " + status);
        }
        status = RunStatus.FAILED;
        finishedAt = now;
        errorMessage = message;
    }

    private void requireStatus(RunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("run " + id + " is " + status + ", expected " + expected);
        }
    }

    public UUID getId() {
        return id;
    }

    public RunStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public RunConfig getConfig() {
        return config;
    }

    public List<FadeWindow> getFades() {
        return List.copyOf(fades);
    }

    /** Null until the run completes. */
    public RunResult getResult() {
        return result;
    }

    public List<LatencySummary> getLatencySummaries() {
        return List.copyOf(latencySummaries);
    }
}
