package feedlab.sim;

/**
 * Measures how bursty the sent stream is by cutting it into fixed windows (1 ms by default) and
 * comparing the bits sent in each window with what a link of a given capacity could carry in that
 * time.
 *
 * <p>Average bit rate hides this. A feed that averages a few megabits per second can still offer
 * hundreds of megabits per second for a millisecond, and that is what a narrow link has to absorb.
 */
public final class BurstAnalyzer {

    public record Result(
            long windowNanos,
            long capacityBps,
            long activeWindows,
            long windowsOverCapacity,
            long peakWindowBits,
            long peakWindowMessages,
            long totalBits,
            long totalMessages) {

        public double peakBps() {
            return peakWindowBits * 1e9 / windowNanos;
        }

        public double peakMessagesPerSecond() {
            return peakWindowMessages * 1e9 / windowNanos;
        }
    }

    private final long windowNanos;
    private final long capacityBps;
    private final long capacityBitsPerWindow;

    private long currentWindow = Long.MIN_VALUE;
    private long windowBits;
    private long windowMessages;

    private long activeWindows;
    private long windowsOverCapacity;
    private long peakWindowBits;
    private long peakWindowMessages;
    private long totalBits;
    private long totalMessages;

    public BurstAnalyzer(long windowNanos, long capacityBps) {
        this.windowNanos = windowNanos;
        this.capacityBps = capacityBps;
        this.capacityBitsPerWindow = (long) (capacityBps * (windowNanos / 1e9));
    }

    public void onPacket(long sendNanos, int wireBytes, int messages) {
        long window = Math.floorDiv(sendNanos, windowNanos);
        if (window != currentWindow) {
            closeWindow();
            currentWindow = window;
        }
        windowBits += wireBytes * 8L;
        windowMessages += messages;
    }

    private void closeWindow() {
        if (windowBits == 0) {
            return;
        }
        activeWindows++;
        if (capacityBps > 0 && windowBits > capacityBitsPerWindow) {
            windowsOverCapacity++;
        }
        peakWindowBits = Math.max(peakWindowBits, windowBits);
        peakWindowMessages = Math.max(peakWindowMessages, windowMessages);
        totalBits += windowBits;
        totalMessages += windowMessages;
        windowBits = 0;
        windowMessages = 0;
    }

    public Result finish() {
        closeWindow();
        return new Result(
                windowNanos,
                capacityBps,
                activeWindows,
                windowsOverCapacity,
                peakWindowBits,
                peakWindowMessages,
                totalBits,
                totalMessages);
    }
}
