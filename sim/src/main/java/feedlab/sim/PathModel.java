package feedlab.sim;

import java.util.List;

/**
 * The parameters of one simulated path between the sender and the receiver.
 *
 * @param name label used in the report
 * @param latencyNanos fixed propagation and equipment delay
 * @param jitterNanos extra delay drawn uniformly from [0, jitterNanos)
 * @param lossProbability chance that a packet is lost outside any fade
 * @param bandwidthBps link capacity in bits per second; 0 means unlimited
 * @param maxQueueNanos a packet that would wait longer than this for the link is dropped
 * @param fades periods of raised loss, as offsets from the first packet
 */
public record PathModel(
        String name,
        long latencyNanos,
        long jitterNanos,
        double lossProbability,
        long bandwidthBps,
        long maxQueueNanos,
        List<Fade> fades) {

    /** A period of raised packet loss, such as rain fade on a microwave hop. */
    public record Fade(long startOffsetNanos, long endOffsetNanos, double lossProbability) {

        public Fade {
            if (endOffsetNanos <= startOffsetNanos) {
                throw new IllegalArgumentException("a fade must end after it starts");
            }
            if (lossProbability < 0 || lossProbability > 1) {
                throw new IllegalArgumentException("loss probability must be between 0 and 1");
            }
        }

        boolean covers(long offsetNanos) {
            return offsetNanos >= startOffsetNanos && offsetNanos < endOffsetNanos;
        }
    }

    public PathModel {
        if (latencyNanos < 0 || jitterNanos < 0 || bandwidthBps < 0 || maxQueueNanos < 0) {
            throw new IllegalArgumentException("path parameters must not be negative");
        }
        if (lossProbability < 0 || lossProbability > 1) {
            throw new IllegalArgumentException("loss probability must be between 0 and 1");
        }
        fades = List.copyOf(fades);
    }

    public boolean inFade(long offsetNanos) {
        for (Fade f : fades) {
            if (f.covers(offsetNanos)) {
                return true;
            }
        }
        return false;
    }

    public double lossAt(long offsetNanos) {
        double loss = lossProbability;
        for (Fade f : fades) {
            if (f.covers(offsetNanos)) {
                loss = Math.max(loss, f.lossProbability());
            }
        }
        return loss;
    }
}
