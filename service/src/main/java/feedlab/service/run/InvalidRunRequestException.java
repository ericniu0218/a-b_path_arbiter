package feedlab.service.run;

/** A run request that passes field validation but cannot be carried out. Maps to HTTP 400. */
public class InvalidRunRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidRunRequestException(String message) {
        super(message);
    }
}
