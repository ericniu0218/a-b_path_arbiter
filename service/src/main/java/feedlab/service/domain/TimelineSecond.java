package feedlab.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.UUID;

/** One second of a run's arbitrated output. Kept apart from the run so a chart can load it alone. */
@Entity
@Table(name = "timeline_second")
public class TimelineSecond {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "timeline_second_seq")
    @SequenceGenerator(name = "timeline_second_seq", sequenceName = "timeline_second_seq", allocationSize = 50)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "second", nullable = false)
    private long second;

    @Column(nullable = false)
    private long delivered;

    @Column(name = "wins_a", nullable = false)
    private long winsA;

    @Column(name = "wins_b", nullable = false)
    private long winsB;

    @Column(nullable = false)
    private long lost;

    @Column(name = "p50_nanos", nullable = false)
    private long p50Nanos;

    @Column(name = "p99_nanos", nullable = false)
    private long p99Nanos;

    protected TimelineSecond() {}

    public TimelineSecond(
            UUID runId, long second, long delivered, long winsA, long winsB, long lost, long p50Nanos, long p99Nanos) {
        this.runId = runId;
        this.second = second;
        this.delivered = delivered;
        this.winsA = winsA;
        this.winsB = winsB;
        this.lost = lost;
        this.p50Nanos = p50Nanos;
        this.p99Nanos = p99Nanos;
    }

    public long getSecond() {
        return second;
    }

    public long getDelivered() {
        return delivered;
    }

    public long getWinsA() {
        return winsA;
    }

    public long getWinsB() {
        return winsB;
    }

    public long getLost() {
        return lost;
    }

    public long getP50Nanos() {
        return p50Nanos;
    }

    public long getP99Nanos() {
        return p99Nanos;
    }
}
