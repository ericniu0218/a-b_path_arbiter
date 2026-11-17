package feedlab.sim;

import feedlab.sim.ItchFileReader.ItchMessage;
import feedlab.sim.PathModel.Fade;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Command-line entry point. */
public final class Main {

    private static final String USAGE = """
            Usage:
              feedlab generate <out.itch> [--seconds 60] [--rate 20000] [--seed 1]
                  Write a small synthetic ITCH 5.0 file for trying the tool out.

              feedlab run <file.itch | file.gz> [options]
                  Replay the file over two simulated paths and print the report.

            Options for run (defaults in brackets):
              --start HH:MM:SS           skip messages stamped before this time of day [start of file]
              --end HH:MM:SS             stop at the first message stamped at or after this time [end of file]
              --max-messages N           stop after N messages [all]
              --fade START:LENGTH:LOSS   Line A fade, seconds from the first message and loss 0-1.
                                         Repeatable. Use --fade none for no fade. [20:10:0.3]
              --a-latency-us N           Line A one-way latency [3980]
              --a-jitter-us N            Line A jitter [2]
              --a-loss P                 Line A loss outside fades [1e-5]
              --a-mbps N                 Line A bandwidth, 0 = unlimited [100]
              --a-queue-ms N             Line A longest queue wait before a drop [2]
              --b-latency-us N           Line B one-way latency [6550]
              --b-jitter-us N            Line B jitter [1]
              --b-loss P                 Line B loss [1e-7]
              --gap-timeout-ms N         how long the arbiter waits for a hole to fill [5]
              --seed N                   random seed [42]
              --csv FILE                 also write a per-second timeline
            """;

    private Main() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.print(USAGE);
            System.exit(2);
        }
        try {
            switch (args[0]) {
                case "generate" -> generate(args);
                case "run" -> run(args);
                default -> {
                    System.err.print(USAGE);
                    System.exit(2);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.print(USAGE);
            System.exit(2);
        }
    }

    private static void generate(String[] args) throws IOException {
        Options o = Options.parse(args, 2);
        Path out = Path.of(args[1]);
        long written = SyntheticItchWriter.write(
                out, (int) o.number("seconds", 60), (int) o.number("rate", 20_000), (long) o.number("seed", 1));
        System.out.printf("Wrote %,d synthetic messages to %s%n", written, out);
    }

    private static void run(String[] args) throws IOException {
        Options o = Options.parse(args, 2);
        Path file = Path.of(args[1]);

        List<Fade> fades = new ArrayList<>();
        List<String> fadeSpecs = o.all("fade");
        if (fadeSpecs.isEmpty()) {
            fadeSpecs = List.of("20:10:0.3");
        }
        for (String spec : fadeSpecs) {
            if (!spec.equals("none")) {
                fades.add(parseFade(spec));
            }
        }

        PathModel wireless = new PathModel(
                "wireless",
                micros(o.number("a-latency-us", 3_980)),
                micros(o.number("a-jitter-us", 2)),
                o.number("a-loss", 1e-5),
                (long) (o.number("a-mbps", 100) * 1e6),
                (long) (o.number("a-queue-ms", 2) * 1e6),
                fades);
        PathModel fiber = new PathModel(
                "fiber",
                micros(o.number("b-latency-us", 6_550)),
                micros(o.number("b-jitter-us", 1)),
                o.number("b-loss", 1e-7),
                0,
                0,
                List.of());
        Simulation.Config config = new Simulation.Config(
                wireless, fiber, (long) (o.number("gap-timeout-ms", 5) * 1e6), (long) o.number("seed", 42), 1_000_000);
        long maxMessages = o.has("max-messages") ? (long) o.number("max-messages", 0) : Long.MAX_VALUE;
        String csv = o.text("csv");
        long startNanos = o.has("start") ? parseTimeOfDay(o.text("start")) : Long.MIN_VALUE;
        long endNanos = o.has("end") ? parseTimeOfDay(o.text("end")) : Long.MAX_VALUE;
        o.rejectUnknown();

        Simulation.Result result;
        try (ItchFileReader reader = new ItchFileReader(file)) {
            result = Simulation.run(new Packetizer(between(reader::next, startNanos, endNanos), "FEEDLAB", maxMessages), config);
        }
        System.out.print(Report.text(file.toString(), result));
        if (csv != null) {
            Files.writeString(Path.of(csv), Report.timelineCsv(result));
            System.out.println("\nTimeline written to " + csv);
        }
    }

    private static long micros(double value) {
        return (long) (value * 1_000);
    }

    /** Limits a message source to timestamps in [startNanos, endNanos). */
    public static Supplier<ItchMessage> between(Supplier<ItchMessage> source, long startNanos, long endNanos) {
        return () -> {
            ItchMessage m = source.get();
            while (m != null && m.timestampNanos() < startNanos) {
                m = source.get();
            }
            return m == null || m.timestampNanos() >= endNanos ? null : m;
        };
    }

    /** Parses HH:MM:SS (seconds may have a fraction) into nanoseconds since midnight. */
    public static long parseTimeOfDay(String text) {
        String[] parts = text.split(":");
        try {
            if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3_600_000_000_000L
                        + Long.parseLong(parts[1]) * 60_000_000_000L
                        + Math.round(Double.parseDouble(parts[2]) * 1e9);
            }
        } catch (NumberFormatException e) {
            // fall through to the error below
        }
        throw new IllegalArgumentException("expected a time as HH:MM:SS, got " + text);
    }

    static Fade parseFade(String spec) {
        String[] parts = spec.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("--fade expects START:LENGTH:LOSS, got " + spec);
        }
        try {
            double start = Double.parseDouble(parts[0]);
            double length = Double.parseDouble(parts[1]);
            return new Fade((long) (start * 1e9), (long) ((start + length) * 1e9), Double.parseDouble(parts[2]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--fade expects numbers, got " + spec);
        }
    }

    /** Minimal {@code --name value} option parser. */
    static final class Options {

        private final Map<String, List<String>> values = new HashMap<>();
        private final List<String> used = new ArrayList<>();

        static Options parse(String[] args, int from) {
            Options o = new Options();
            for (int i = from; i < args.length; i += 2) {
                if (!args[i].startsWith("--") || i + 1 >= args.length) {
                    throw new IllegalArgumentException("expected --option value, got " + args[i]);
                }
                o.values.computeIfAbsent(args[i].substring(2), k -> new ArrayList<>()).add(args[i + 1]);
            }
            return o;
        }

        boolean has(String name) {
            used.add(name);
            return values.containsKey(name);
        }

        List<String> all(String name) {
            used.add(name);
            return values.getOrDefault(name, List.of());
        }

        String text(String name) {
            List<String> v = all(name);
            return v.isEmpty() ? null : v.get(v.size() - 1);
        }

        double number(String name, double fallback) {
            String v = text(name);
            if (v == null) {
                return fallback;
            }
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("--" + name + " expects a number, got " + v);
            }
        }

        void rejectUnknown() {
            for (String name : values.keySet()) {
                if (!used.contains(name)) {
                    throw new IllegalArgumentException("unknown option --" + name);
                }
            }
        }
    }
}
