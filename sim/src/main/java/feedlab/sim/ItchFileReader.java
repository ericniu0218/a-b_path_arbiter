package feedlab.sim;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Streams messages from a Nasdaq TotalView-ITCH 5.0 file.
 *
 * <p>The file is a sequence of records: a 2-byte big-endian length, then the message. Every ITCH
 * 5.0 message starts with a common header, and this reader only needs the timestamp from it:
 *
 * <pre>
 * offset  length  field
 * 0       1       message type
 * 1       2       stock locate
 * 3       2       tracking number
 * 5       6       timestamp, nanoseconds since midnight
 * </pre>
 *
 * The message body is carried through untouched, so no per-type parsing is needed. Files ending in
 * {@code .gz} are decompressed on the fly.
 */
public final class ItchFileReader implements AutoCloseable {

    /** One ITCH message and the exchange timestamp from its header. */
    public record ItchMessage(long timestampNanos, byte[] data) {}

    private static final int HEADER_LENGTH = 11;

    private final DataInputStream in;

    public ItchFileReader(Path file) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(file), 1 << 16);
        this.in = new DataInputStream(
                file.getFileName().toString().endsWith(".gz")
                        ? new BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16)
                        : raw);
    }

    /** Returns the next message, or {@code null} at the end of the file. */
    public ItchMessage next() {
        try {
            int high = in.read();
            if (high < 0) {
                return null;
            }
            int low = in.read();
            if (low < 0) {
                throw new EOFException("file ends inside a record length");
            }
            int length = (high << 8) | low;
            if (length < HEADER_LENGTH) {
                throw new IOException("record of " + length + " bytes is too short to be an ITCH 5.0 message");
            }
            byte[] data = new byte[length];
            in.readFully(data);
            return new ItchMessage(timestampOf(data), data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static long timestampOf(byte[] message) {
        long t = 0;
        for (int i = 5; i < HEADER_LENGTH; i++) {
            t = (t << 8) | (message[i] & 0xFF);
        }
        return t;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
