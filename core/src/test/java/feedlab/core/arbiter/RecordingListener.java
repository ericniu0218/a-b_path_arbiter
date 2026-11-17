package feedlab.core.arbiter;

import java.util.ArrayList;
import java.util.List;

/** Test double that records everything the arbiter emits. */
public final class RecordingListener implements ArbiterListener {

    public record Delivered(long sequence, byte[] payload, Line winner, long atNanos) {}

    public record Gap(long from, long to, long atNanos) {}

    public final List<Delivered> messages = new ArrayList<>();
    public final List<Gap> gaps = new ArrayList<>();
    /** Sequence ranges in callback order, messages as [seq, seq+1) and gaps as [from, to). */
    public final List<long[]> rangesInOrder = new ArrayList<>();

    @Override
    public void onMessage(long sequence, byte[] payload, Line winner, long nowNanos) {
        messages.add(new Delivered(sequence, payload, winner, nowNanos));
        rangesInOrder.add(new long[] {sequence, sequence + 1});
    }

    @Override
    public void onGap(long fromSequence, long toSequence, long nowNanos) {
        gaps.add(new Gap(fromSequence, toSequence, nowNanos));
        rangesInOrder.add(new long[] {fromSequence, toSequence});
    }

    public List<Long> sequences() {
        return messages.stream().map(Delivered::sequence).toList();
    }
}
