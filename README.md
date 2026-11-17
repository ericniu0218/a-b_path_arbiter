# Protected Feed Lab

Replay market data over two simulated paths, a fast wireless path that sometimes fades and a
slower fiber path that does not, and measure what A/B line arbitration buys you.

Market data providers such as Quincy Data offer "protected" feeds: the same data sent over both a
wireless and a wired path. This project is my attempt to understand that idea by building a small
version of the receiving side and measuring it. It is built from public specifications only. It is
not a description of how any real provider's system works.

## The questions

1. How much latency does the arbitrated stream give up, compared with the fast path alone?
2. When the fast path fades, how much of the stream does the slow path end up carrying?
3. Is the output gap-free whenever either path delivered the data?
4. How bursty is the feed, and how often do bursts exceed what a narrow link can carry?

## How it works

```
ITCH 5.0 file ──> Packetizer ──┬──> Path A (wireless: fast, lossy, narrow) ──┐
                (MoldUDP64)    └──> Path B (fiber: slow, clean, wide) ───────┴──> Arbiter ──> metrics
```

- **Input.** Any Nasdaq TotalView-ITCH 5.0 file. Only the 6-byte timestamp in the common message
  header is parsed. Message bodies pass through untouched.
- **Packetizer.** Plays the exchange: groups messages into MoldUDP64 packets (10-byte session,
  8-byte sequence number, 2-byte count) and sends heartbeats when idle.
- **Path simulator.** Each path has a fixed latency, jitter, a loss rate, optional fade windows
  with a higher loss rate, and an optional bandwidth limit with a bounded queue.
- **Arbiter.** Takes each message from whichever line delivers it first, drops the duplicate,
  buffers out-of-order packets, and waits a bounded time for the other line to fill a hole before
  reporting a gap.
- **Virtual clock.** Nothing reads the wall clock. Time is the exchange timestamp plus the delays
  the path models add. A run is exact, repeatable from its seed, and replays as fast as the CPU
  allows.

## Run it

Needs a JDK 21 or newer. Everything else comes through the Gradle wrapper.

```bash
./gradlew build                     # compile and run all tests
./gradlew :sim:installDist          # build the command-line tool

sim/build/install/sim/bin/sim generate sample.itch
sim/build/install/sim/bin/sim run sample.itch --csv timeline.csv
```

`generate` writes a small synthetic file so the tool runs without a download. For real traffic,
Nasdaq publishes full-day sample files (3.5 to 16 GB each, gzipped) at
<https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/>. The reader takes `.gz` files directly, and
`--max-messages` limits a run to the start of the file.

Run `sim` with no arguments for the full list of options: latencies, loss, bandwidth, fade
windows, gap timeout, time-of-day range and seed.

## Results

Input: real Nasdaq TotalView-ITCH 5.0 data for 30 December 2019, the first ten minutes of the
regular session (09:30 to 09:40), which is 11.8 million messages in 7.0 million packets. The path
parameters (3.98 ms wireless, 6.55 ms fiber, 100 Mbps, loss rates) are illustrative round numbers
I chose, not measurements of any real network. All runs use seed 42 and are exactly repeatable.

### 1. Baseline: a two-minute fade at 30% loss

| | p50 | p99 | Messages lost |
|---|---|---|---|
| **Clear** — wireless alone | 3.99 ms | 6.04 ms | 3.20% |
| **Clear** — fiber alone | 6.55 ms | 6.55 ms | 0 |
| **Clear** — arbitrated | 3.99 ms | 6.55 ms | 0 |
| **Fade** — wireless alone | 3.99 ms | 5.95 ms | 30.5% |
| **Fade** — arbitrated | 6.51 ms | 6.55 ms | 0 |

Arbitration removed all loss: zero gaps across 11.8 million messages, although the wireless path
alone lost 3.2% in clear conditions and 30.5% in the fade.

### 2. How much loss before the fast path stops helping?

Wireless loss applied to the whole window, with unlimited bandwidth to isolate the effect.

| Wireless loss | Arbitrated p50 | Arbitrated p99 | Share won by wireless |
|---|---|---|---|
| 0.01% | 3.98 ms | 3.98 ms | 99.99% |
| 0.1% | 3.98 ms | 6.20 ms | 99.90% |
| 1% | 3.98 ms | 6.55 ms | 99.0% |
| 2% | 4.69 ms | 6.55 ms | 98.0% |
| 5% | 5.91 ms | 6.55 ms | 95.0% |
| 10% | 6.28 ms | 6.55 ms | 90.0% |
| 30% | 6.51 ms | 6.55 ms | 70.0% |

This is the result I found most interesting. **The median speed advantage collapses between 1%
and 2% loss, even though the wireless copy still wins 98% of messages.** The cause is head-of-line
blocking. The output is strictly in order, so one lost packet holds back everything behind it
until fiber delivers the missing piece 2.57 ms later. At the open this feed sends a packet about
every 85 microseconds, so a 2.57 ms stall delays roughly 30 packets. Once losses come more often
than that, the stream is stalled most of the time. The tail goes first: p99 is already at fiber
latency at 0.1% loss.

"Share won" is therefore a misleading health metric on its own. Latency percentiles tell the real
story.

### 3. Bursts against a narrow link

No fade. Only the wireless bandwidth changes.

| Wireless bandwidth | 1 ms windows over capacity | Wireless-alone loss | Arbitrated p99 |
|---|---|---|---|
| 50 Mbps | 1.49% | 6.49% | 6.55 ms |
| 100 Mbps | 0.40% | 2.76% | 6.55 ms |
| 200 Mbps | 0.12% | 0.32% | 6.48 ms |
| 500 Mbps | 0 | 0.001% | 4.02 ms |

The feed **averaged 10.8 Mbps but peaked at 421 Mbps within a single millisecond**, about 39 times
the average. A link sized for the average, or even ten times the average, drops packets in every
burst, and those drops then trigger the head-of-line stalls above. This is the full-depth feed for
every Nasdaq symbol. I assume a real wireless service carries a much smaller selection of the
data, partly for this reason, but I have not confirmed that.

### Reproduce

```bash
mkdir -p data
curl -r 0-314572799 -o data/12302019.NASDAQ_ITCH50.part.gz "https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/12302019.NASDAQ_ITCH50.gz"
```

That fetches the first 300 MB, which covers 03:04 to 09:45. Stay inside that range with `--end`.

```bash
sim/build/install/sim/bin/sim run data/12302019.NASDAQ_ITCH50.part.gz --start 09:30:00 --end 09:40:00 --fade 240:120:0.3
```

For table 2 use `--fade 0:700:LOSS --a-mbps 0`. For table 3 use `--fade none --a-mbps N`.

## The service

A Spring Boot service wraps the simulator so runs can be requested over HTTP, stored in Postgres
and compared later. The simulator is unchanged, and the service never reimplements any of it.

```bash
docker compose up --build            # Postgres, the service and the dashboard; set FEEDLAB_PORT or FEEDLAB_WEB_PORT if 8080 or 3000 is taken
curl localhost:8080/api/inputs       # files in ./data that a run may use

curl -X POST localhost:8080/api/runs -H 'Content-Type: application/json' -d '{
  "inputFile": "12302019.NASDAQ_ITCH50.part.gz",
  "start": "09:30:00", "end": "09:40:00",
  "lineA": { "fades": [ { "startSeconds": 240, "lengthSeconds": 120, "loss": 0.3 } ] }
}'
```

| Endpoint | Purpose |
|---|---|
| `POST /api/runs` | Queue a run. Returns `202` with a `Location`; only `inputFile` is required. |
| `GET /api/runs/{id}` | Status, the scenario with defaults filled in, and the results once `COMPLETED`. |
| `GET /api/runs/{id}/timeline` | One row per second of exchange time, for charting. |
| `GET /api/runs` | All runs, newest first. |
| `GET /api/inputs` | Input files available. |

Through the API, the baseline scenario above returns the same numbers as the command line,
including the same 639,971 messages lost on line A during the fade and zero gaps after
arbitration.

How it is built:

- **Asynchronous.** A run is stored as `QUEUED` and handed to a single background worker. The
  caller polls. The simulation itself runs outside any database transaction.
- **A run is a small state machine.** `QUEUED`, `RUNNING`, then `COMPLETED` or `FAILED`, and the
  entity refuses any other move. A run that was in flight when the service stopped is marked
  `FAILED` on the next start, because the queue is in memory.
- **Schema is owned by Flyway.** Hibernate only validates it. Results are stored as headline
  numbers, latency percentiles per stream and segment, and a per-second timeline in its own table.
- **Input files are confined to one directory.** `../` and absolute paths are rejected.
- **Errors are RFC 9457 problem details** with a `400` or `404`, not stack traces.

What I chose not to build: there is no Kafka. In this design nothing needs a broker. The two
paths are simulated on one virtual clock inside the process, and putting Kafka between them
would add real queueing delay and ordering effects that the model is meant to control. Kafka
would earn its place if the service consumed a live feed from several sources.

## The dashboard

A React and TypeScript single-page app (Vite, no UI or chart libraries) for launching runs and
reading them. With `docker compose up --build` it is at <http://localhost:3000>.

- **New run form** with presets for the three scenarios in the results above.
- **Run view**: the scenario, a one-sentence summary built from the stored numbers, headline
  totals, the latency table, and two charts over time.
  - *Arbitrated latency per second* (median and p99) with the fade shaded and the wireless-only
    and fiber-only latencies as reference lines. In the baseline run the median jumps from the
    wireless line to the fiber line for exactly the two minutes of the fade.
  - *Which line's copy was delivered*: the wireless share of each second, as a stacked area.
- Runs refresh while they are queued or running. Long runs are downsampled to about 600 points,
  keeping the worst p99 in each bucket so spikes survive.
- Charts are hand-written SVG with hover read-outs, light and dark themes, and a layout that works
  on a phone.

To develop it against a running service:

```bash
cd web && npm install
FEEDLAB_API=http://localhost:8080 npm run dev
npm test                             # 27 tests
```

## Design decisions

- **The arbiter is a plain library with no clock, no threads and no I/O.** Every call carries the
  current time, so the same input always gives the same output. That is what makes it testable
  with properties and usable under a virtual clock.
- **The gap timeout must exceed the latency difference between the paths.** If it is shorter, the
  slow path can never rescue a loss on the fast path. There is a BDD scenario and an end-to-end
  test for exactly this.
- **Strict ordering is a choice with a cost.** It is what causes the fall-back to fiber latency
  during a fade. A consumer that can tolerate out-of-order messages across independent
  instruments could do better. I have not built that.
- **Gaps are reported, not repaired.** A real MoldUDP64 receiver would re-request the missing
  range from the exchange. Here a gap is just counted.

## Tests

79 tests, run by `./gradlew build`. The 9 integration tests start Postgres in Docker. The dashboard adds 27 more (`cd web && npm test`): chart maths, formatting, and component behaviour.

- **Unit tests** for the packet codec, the arbiter, the packetizer and the path model.
- **Property-based tests** (jqwik) drive the arbiter with thousands of random runs of loss, delay
  and reordering and check invariants: every sequence number is reported exactly once and in
  order; nothing is lost if either line delivered it; with clean lines each message is released
  the instant its first copy arrives; the same input gives the same output.
- **BDD scenarios** (Cucumber) in
  [arbitration.feature](core/src/test/resources/features/arbitration.feature) describe the
  behaviour in plain language.
- **End-to-end tests** run the whole pipeline on a generated file and check that the totals
  balance.
- **Service integration tests** start the real application against a real Postgres
  (Testcontainers), run a simulation through the HTTP API, and read it back. They cover failed
  runs, bad input, path traversal, same-seed reproducibility and restart recovery.

## Limits

This is an offline analysis tool, not a feed handler.

- It is a simulation in Java. It says nothing about the nanosecond-level performance of real
  arbitration, which is done in C++ or on FPGAs with kernel bypass and hardware timestamps.
- The path model is simple: independent random loss, uniform jitter, one queue. Real microwave
  fades are correlated and come with modulation changes, not just loss.
- Latency is measured from one virtual clock. Measuring it across two real sites needs
  synchronized clocks, which is its own hard problem.
- The default latencies, bandwidth and loss rates are placeholders I chose.

## Layout

| Module | What it holds |
|---|---|
| [core](core) | MoldUDP64 codec and the arbiter. No runtime dependencies. |
| [sim](sim) | ITCH reader, packetizer, path simulator, metrics, report and CLI. |
| [service](service) | Spring Boot API, JPA entities, Flyway schema. |
| [web](web) | React dashboard. |

## Next

1. Run a full trading day, not just the first ten minutes.
2. Compare two runs side by side in the dashboard.
3. Compare arbitration strategies, for example allowing out-of-order delivery across independent
   instruments to avoid the head-of-line stalls described above.

## How I used AI

I built this with an AI coding assistant. The README should say which design decisions, tests and
checks are mine once I have reviewed and reworked the code myself.
