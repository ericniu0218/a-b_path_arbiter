package feedlab.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * The scenario a run was asked to simulate, in the units the simulator uses. Line A is the fast,
 * narrow, lossy path; line B is the slow, clean one.
 */
@Embeddable
public record RunConfig(
        @Column(name = "input_file", nullable = false, length = 512) String inputFile,
        @Column(name = "start_time", length = 32) String startTime,
        @Column(name = "end_time", length = 32) String endTime,
        @Column(name = "max_messages") Long maxMessages,
        @Column(name = "a_latency_nanos", nullable = false) long aLatencyNanos,
        @Column(name = "a_jitter_nanos", nullable = false) long aJitterNanos,
        @Column(name = "a_loss", nullable = false) double aLoss,
        @Column(name = "a_bandwidth_bps", nullable = false) long aBandwidthBps,
        @Column(name = "a_max_queue_nanos", nullable = false) long aMaxQueueNanos,
        @Column(name = "b_latency_nanos", nullable = false) long bLatencyNanos,
        @Column(name = "b_jitter_nanos", nullable = false) long bJitterNanos,
        @Column(name = "b_loss", nullable = false) double bLoss,
        @Column(name = "gap_timeout_nanos", nullable = false) long gapTimeoutNanos,
        @Column(name = "seed", nullable = false) long seed) {}
