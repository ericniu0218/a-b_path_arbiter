package feedlab.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A period of raised loss on line A, as offsets from the first message of the run. */
@Embeddable
public record FadeWindow(
        @Column(name = "start_offset_nanos", nullable = false) long startOffsetNanos,
        @Column(name = "end_offset_nanos", nullable = false) long endOffsetNanos,
        @Column(name = "loss", nullable = false) double loss) {}
