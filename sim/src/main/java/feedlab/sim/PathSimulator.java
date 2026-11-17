package feedlab.sim;

import java.util.OptionalLong;
import java.util.SplittableRandom;

/**
 * Decides, for each packet sent, whether it arrives on this path and when.
 *
 * <p>The model has three parts, applied in order:
 *
 * <ol>
 *   <li><b>Capacity.</b> The link sends one packet at a time at {@code bandwidthBps}. A packet that
 *       finds the link busy waits; if the wait would exceed {@code maxQueueNanos} it is dropped.
 *       This is what turns a microburst into delay and loss on a narrow link.
 *   <li><b>Loss.</b> Each transmitted packet is lost with the path's loss probability, which is
 *       higher during a fade.
 *   <li><b>Delay.</b> Survivors arrive after the fixed latency plus a small random jitter.
 * </ol>
 *
 * All randomness comes from a seeded generator, so a run is repeatable.
 */
public final class PathSimulator {

    private final PathModel model;
    private final SplittableRandom rng;

    private long originNanos = Long.MIN_VALUE;
    private long linkFreeAtNanos = Long.MIN_VALUE;

    private long packetsSent;
    private long packetsLost;
    private long packetsDroppedAtQueue;
    private long maxQueueDelayNanos;

    public PathSimulator(PathModel model, long seed) {
        this.model = model;
        this.rng = new SplittableRandom(seed);
    }

    /**
     * @return the arrival time at the receiver, or empty if the packet does not arrive. Packets must
     *     be offered in non-decreasing send-time order.
     */
    public OptionalLong transmit(long sendNanos, int wireBytes) {
        if (originNanos == Long.MIN_VALUE) {
            originNanos = sendNanos;
        }
        packetsSent++;

        long departNanos = sendNanos;
        if (model.bandwidthBps() > 0) {
            long startNanos = Math.max(sendNanos, linkFreeAtNanos);
            long queueDelay = startNanos - sendNanos;
            if (queueDelay > model.maxQueueNanos()) {
                packetsDroppedAtQueue++;
                return OptionalLong.empty();
            }
            maxQueueDelayNanos = Math.max(maxQueueDelayNanos, queueDelay);
            long transmitNanos = wireBytes * 8L * 1_000_000_000L / model.bandwidthBps();
            departNanos = startNanos + transmitNanos;
            linkFreeAtNanos = departNanos;
        }

        // Draw both random numbers for every transmitted packet so one path's outcome for a packet
        // does not shift the random stream of later packets differently between scenarios.
        double lossDraw = rng.nextDouble();
        long jitter = model.jitterNanos() > 0 ? rng.nextLong(model.jitterNanos()) : 0;
        if (lossDraw < model.lossAt(sendNanos - originNanos)) {
            packetsLost++;
            return OptionalLong.empty();
        }
        return OptionalLong.of(departNanos + model.latencyNanos() + jitter);
    }

    public PathModel model() {
        return model;
    }

    public long packetsSent() {
        return packetsSent;
    }

    /** Packets lost in transit (baseline loss and fades). */
    public long packetsLost() {
        return packetsLost;
    }

    /** Packets dropped because the link was saturated. */
    public long packetsDroppedAtQueue() {
        return packetsDroppedAtQueue;
    }

    public long maxQueueDelayNanos() {
        return maxQueueDelayNanos;
    }
}
