package feedlab.sim;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;

/**
 * Writes a small ITCH 5.0-framed file of Add Order messages so the project runs without the
 * multi-gigabyte Nasdaq sample.
 *
 * <p>This is made-up traffic: a Poisson stream at a steady rate with occasional tight bursts. It is
 * good for exercising the pipeline and for tests. Any conclusion about burst behaviour should come
 * from the real file.
 */
public final class SyntheticItchWriter {

    private static final long MARKET_OPEN_NANOS = 34_200_000_000_000L; // 09:30:00
    private static final int ADD_ORDER_LENGTH = 36;

    private SyntheticItchWriter() {}

    /** @return the number of messages written */
    public static long write(Path file, int seconds, int messagesPerSecond, long seed) throws IOException {
        SplittableRandom rng = new SplittableRandom(seed);
        long written = 0;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            long t = MARKET_OPEN_NANOS;
            long end = t + seconds * 1_000_000_000L;
            double burstChance = 2.0 / messagesPerSecond; // about two bursts per second
            while (true) {
                t += (long) (-Math.log(1 - rng.nextDouble()) * 1_000_000_000L / messagesPerSecond);
                if (t >= end) {
                    break;
                }
                writeAddOrder(out, t, ++written, rng);
                if (rng.nextDouble() < burstChance) {
                    int size = 200 + rng.nextInt(1_800);
                    long step = (100_000 + rng.nextInt(900_000)) / size;
                    for (int i = 0; i < size && t + step < end; i++) {
                        t += step;
                        writeAddOrder(out, t, ++written, rng);
                    }
                }
            }
        }
        return written;
    }

    private static void writeAddOrder(DataOutputStream out, long timestamp, long orderRef, SplittableRandom rng)
            throws IOException {
        ByteBuffer m = ByteBuffer.allocate(ADD_ORDER_LENGTH);
        m.put((byte) 'A');
        m.putShort((short) 1); // stock locate
        m.putShort((short) 0); // tracking number
        m.put((byte) (timestamp >>> 40));
        m.put((byte) (timestamp >>> 32));
        m.putInt((int) timestamp);
        m.putLong(orderRef);
        m.put((byte) (rng.nextBoolean() ? 'B' : 'S'));
        m.putInt(100 * (1 + rng.nextInt(10)));
        m.put("TEST    ".getBytes(StandardCharsets.US_ASCII));
        m.putInt(1_000_000 + rng.nextInt(10_000));
        out.writeShort(ADD_ORDER_LENGTH);
        out.write(m.array());
    }
}
