package feedlab.core.arbiter;

/**
 * Receives the arbitrated stream. Callbacks arrive in strictly increasing sequence order: every
 * sequence number is reported exactly once, either as a message or inside one gap.
 *
 * <p>Implementations must not call back into the arbiter.
 */
public interface ArbiterListener {

    /**
     * @param sequence the message's sequence number
     * @param payload the message bytes
     * @param winner the line whose copy was delivered
     * @param nowNanos the time the message was released downstream
     */
    void onMessage(long sequence, byte[] payload, Line winner, long nowNanos);

    /**
     * Messages {@code fromSequence} (inclusive) to {@code toSequence} (exclusive) were not received
     * on either line before the gap timeout. A production feed handler would re-request them here.
     */
    void onGap(long fromSequence, long toSequence, long nowNanos);
}
