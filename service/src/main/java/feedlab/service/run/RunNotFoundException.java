package feedlab.service.run;

import java.util.UUID;

/** Maps to HTTP 404. */
public class RunNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RunNotFoundException(UUID id) {
        super("no run with id " + id);
    }
}
