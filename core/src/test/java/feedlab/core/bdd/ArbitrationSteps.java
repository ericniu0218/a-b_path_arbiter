package feedlab.core.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import feedlab.core.arbiter.Arbiter;
import feedlab.core.arbiter.ArbiterConfig;
import feedlab.core.arbiter.Line;
import feedlab.core.arbiter.Packets;
import feedlab.core.arbiter.RecordingListener;
import feedlab.core.mold.MoldPacket;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.LongStream;

public class ArbitrationSteps {

    private record Event(long time, int order, Line line, MoldPacket packet) {}

    private long gapTimeoutNanos;
    private final List<Event> events = new ArrayList<>();
    private long advanceTo = Long.MIN_VALUE;
    private RecordingListener out;

    private static long nanos(double millis) {
        return Math.round(millis * 1_000_000);
    }

    /** Runs the scenario once, on the first assertion, with arrivals fed in time order. */
    private RecordingListener output() {
        if (out == null) {
            out = new RecordingListener();
            Arbiter arbiter = new Arbiter(ArbiterConfig.withGapTimeout(gapTimeoutNanos), out);
            events.sort(Comparator.comparingLong(Event::time).thenComparingInt(Event::order));
            for (Event e : events) {
                arbiter.onPacket(e.line(), e.packet(), e.time());
            }
            if (advanceTo != Long.MIN_VALUE) {
                arbiter.advanceTo(advanceTo);
            }
        }
        return out;
    }

    @Given("an arbiter with a gap timeout of {double} ms")
    public void anArbiterWithAGapTimeoutOf(double millis) {
        gapTimeoutNanos = nanos(millis);
    }

    @When("line {word} delivers messages {long} to {long} at {double} ms")
    public void lineDeliversMessages(String line, long first, long last, double millis) {
        events.add(new Event(nanos(millis), events.size(), Line.valueOf(line), Packets.range(first, last)));
    }

    @When("time advances to {double} ms")
    public void timeAdvancesTo(double millis) {
        advanceTo = nanos(millis);
    }

    @Then("the output contains messages {long} to {long} with no gap")
    public void theOutputContainsMessagesWithNoGap(long first, long last) {
        assertThat(output().gaps).isEmpty();
        assertThat(output().sequences()).isEqualTo(LongStream.rangeClosed(first, last).boxed().toList());
    }

    @Then("the output contains {int} messages")
    public void theOutputContainsMessages(int count) {
        assertThat(output().messages).hasSize(count);
    }

    @Then("line {word} won messages {long} to {long}")
    public void lineWonMessages(String line, long first, long last) {
        Line expected = Line.valueOf(line);
        assertThat(output().messages)
                .filteredOn(m -> m.sequence() >= first && m.sequence() <= last)
                .hasSize((int) (last - first + 1))
                .allMatch(m -> m.winner() == expected);
    }

    @Then("message {long} was released at {double} ms")
    public void messageWasReleasedAt(long sequence, double millis) {
        assertThat(output().messages)
                .filteredOn(m -> m.sequence() == sequence)
                .singleElement()
                .matches(m -> m.atNanos() == nanos(millis));
    }

    @Then("a gap is reported for messages {long} to {long} at {double} ms")
    public void aGapIsReported(long first, long last, double millis) {
        assertThat(output().gaps).containsExactly(new RecordingListener.Gap(first, last + 1, nanos(millis)));
    }

    @Then("each message was released exactly once")
    public void eachMessageWasReleasedExactlyOnce() {
        List<Long> sequences = output().sequences();
        assertThat(new HashSet<>(sequences)).hasSameSizeAs(sequences);
    }
}
