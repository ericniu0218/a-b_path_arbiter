package feedlab.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Latency percentiles and loss for one stream in one segment of a run.
 *
 * @param messagesWon messages delivered from this line's copy; null for the arbitrated stream
 */
@Embeddable
public record LatencySummary(
        @Enumerated(EnumType.STRING) @Column(name = "segment", nullable = false, length = 8) Segment segment,
        @Enumerated(EnumType.STRING) @Column(name = "source", nullable = false, length = 16) Source source,
        @Column(name = "messages_sent", nullable = false) long messagesSent,
        @Column(name = "messages_lost", nullable = false) long messagesLost,
        @Column(name = "messages_won") Long messagesWon,
        @Column(name = "p50_nanos", nullable = false) long p50Nanos,
        @Column(name = "p99_nanos", nullable = false) long p99Nanos,
        @Column(name = "p999_nanos", nullable = false) long p999Nanos,
        @Column(name = "max_nanos", nullable = false) long maxNanos) {}
