package feedlab.core.arbiter;

/**
 * @param firstSequence the sequence number of the first message of the session
 * @param gapTimeoutNanos how long the arbiter waits for the other line to fill a hole before it
 *     reports the hole as a gap and moves on. This must be longer than the latency difference
 *     between the two lines, or the slower line can never rescue a loss on the faster one.
 * @param maxBufferedPackets bound on out-of-order packets held while waiting for a hole to fill.
 *     When the bound is exceeded the arbiter gives up on the oldest hole immediately.
 */
public record ArbiterConfig(long firstSequence, long gapTimeoutNanos, int maxBufferedPackets) {

    public ArbiterConfig {
        if (firstSequence < 0) {
            throw new IllegalArgumentException("firstSequence must not be negative");
        }
        if (gapTimeoutNanos < 0) {
            throw new IllegalArgumentException("gapTimeoutNanos must not be negative");
        }
        if (maxBufferedPackets < 1) {
            throw new IllegalArgumentException("maxBufferedPackets must be at least 1");
        }
    }

    public static ArbiterConfig withGapTimeout(long gapTimeoutNanos) {
        return new ArbiterConfig(1, gapTimeoutNanos, 65_536);
    }
}
