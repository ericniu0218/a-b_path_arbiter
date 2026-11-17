package feedlab.core.mold;

/** Thrown when bytes cannot be decoded as a MoldUDP64 packet. */
public final class MalformedPacketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedPacketException(String message) {
        super(message);
    }
}
