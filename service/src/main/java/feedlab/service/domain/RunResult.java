package feedlab.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Headline numbers of a completed run. All columns are null until the run completes. */
@Embeddable
public record RunResult(
        @Column(name = "messages") Long messages,
        @Column(name = "data_packets") Long dataPackets,
        @Column(name = "duration_nanos") Long durationNanos,
        @Column(name = "gaps") Long gaps,
        @Column(name = "messages_lost") Long messagesLost,
        @Column(name = "average_bps") Double averageBps,
        @Column(name = "peak_bps") Double peakBps,
        @Column(name = "burst_windows_over") Long burstWindowsOver,
        @Column(name = "burst_windows_active") Long burstWindowsActive,
        @Column(name = "max_buffered_packets") Integer maxBufferedPackets) {}
