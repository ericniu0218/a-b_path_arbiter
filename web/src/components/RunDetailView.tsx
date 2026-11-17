import { downsample, shareFromA } from "../chart";
import {
  formatCompact,
  formatCount,
  formatLossShare,
  formatMbps,
  formatMicros,
  formatProbability,
  formatShare,
  formatSpan,
} from "../format";
import { useRun } from "../hooks";
import { scenarioTitle } from "../scenario";
import type { Latency, RunDetail, Source } from "../types";
import { LatencyTable } from "./LatencyTable";
import { LineChart } from "./LineChart";
import { ShareChart } from "./ShareChart";
import { StatusBadge } from "./StatusBadge";

const MAX_CHART_POINTS = 600;

function find(rows: Latency[], segment: "CLEAR" | "FADE", source: Source): Latency | undefined {
  return rows.find((r) => r.segment === segment && r.source === source);
}

/** The part of the run the summary is about: the fade if there was one, otherwise the whole run. */
function keySegment(run: RunDetail): "CLEAR" | "FADE" {
  return run.config.lineA.fades.length > 0 ? "FADE" : "CLEAR";
}

/** The answer in plain words: what combining the lines saved, and what it cost. */
export function verdict(run: RunDetail): { main: string; detail: string | null } | null {
  const results = run.results;
  if (!results) return null;
  const main =
    results.messagesLost === 0
      ? "Using both lines, no messages were lost."
      : `Using both lines, ${formatCount(results.messagesLost)} of ${formatCount(results.messages)} messages were lost.`;

  const segment = keySegment(run);
  const wireless = find(results.latency, segment, "LINE_A");
  const combined = find(results.latency, segment, "ARBITRATED");
  if (!wireless || !combined) return { main, detail: null };

  const extra = combined.p50Micros - wireless.p50Micros;
  const delay =
    extra < 50
      ? `The typical delay stayed at ${formatMicros(combined.p50Micros)}.`
      : `The cost: the typical delay was ${formatMicros(combined.p50Micros)} instead of ${formatMicros(wireless.p50Micros)}.`;
  if (wireless.messagesLost === 0) return { main, detail: delay };

  const who = segment === "FADE" ? "During the fade, the wireless line alone" : "The wireless line alone";
  return {
    main,
    detail: `${who} would have lost ${formatLossShare(wireless.messagesLost, wireless.messagesSent)} of its messages. ${delay}`,
  };
}

function Comparison({ run }: { run: RunDetail }) {
  const results = run.results!;
  const segment = keySegment(run);
  const fade = run.config.lineA.fades[0];
  const cards: { source: Source; label: string; note: string }[] = [
    { source: "LINE_A", label: "Wireless alone", note: "fast, but loses packets" },
    { source: "LINE_B", label: "Fiber alone", note: "slower, reliable" },
    { source: "ARBITRATED", label: "Both lines combined", note: "what the receiver gets" },
  ];
  return (
    <section aria-labelledby="comparison-heading">
      <h3 id="comparison-heading">{segment === "FADE" && fade ? `During the fade (${formatSpan(fade.lengthSeconds)})` : "Over the whole run"}</h3>
      <div className="cards">
        {cards.map(({ source, label, note }) => {
          const row = find(results.latency, segment, source);
          if (!row) return null;
          return (
            <div key={source} className={`card${source === "ARBITRATED" ? " card-main" : ""}`}>
              <h4>{label}</h4>
              <p className="card-note">{note}</p>
              <dl>
                <div>
                  <dt>Messages lost</dt>
                  <dd>{formatLossShare(row.messagesLost, row.messagesSent)}</dd>
                </div>
                <div>
                  <dt>Typical delay</dt>
                  <dd>{formatMicros(row.p50Micros)}</dd>
                </div>
              </dl>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function Totals({ run }: { run: RunDetail }) {
  const r = run.results!;
  const limited = run.config.lineA.bandwidthMbps > 0;
  return (
    <dl className="facts">
      <div>
        <dt>Messages replayed</dt>
        <dd>
          {formatCount(r.messages)} in {formatCount(r.dataPackets)} packets
        </dd>
      </div>
      <div>
        <dt>Gaps after combining</dt>
        <dd>{r.gaps}</dd>
      </div>
      <div>
        <dt>Busiest millisecond</dt>
        <dd>
          {r.peakMbps.toFixed(0)} Mbps, about {r.averageMbps > 0 ? (r.peakMbps / r.averageMbps).toFixed(0) : "–"}× the{" "}
          {r.averageMbps.toFixed(1)} Mbps average
        </dd>
      </div>
      <div>
        <dt>Milliseconds over the wireless limit</dt>
        <dd>{limited ? formatShare(r.burstWindowsOverCapacity, r.burstWindowsActive, 2) : "no limit set"}</dd>
      </div>
    </dl>
  );
}

function Settings({ run }: { run: RunDetail }) {
  const { config } = run;
  const a = config.lineA;
  const b = config.lineB;
  return (
    <dl className="facts">
      <div>
        <dt>Data</dt>
        <dd>
          {config.inputFile}, {config.start ?? "start of file"} to {config.end ?? "end of file"}
        </dd>
      </div>
      <div>
        <dt>Wireless line</dt>
        <dd>
          {formatMicros(a.latencyMicros)} delay, {formatMbps(a.bandwidthMbps)}, {formatProbability(a.loss)} loss
          {a.fades.map((f) => (
            <span key={f.startSeconds}>
              ; fade from {f.startSeconds} s for {formatSpan(f.lengthSeconds)} at {formatProbability(f.loss)} loss
            </span>
          ))}
        </dd>
      </div>
      <div>
        <dt>Fiber line</dt>
        <dd>
          {formatMicros(b.latencyMicros)} delay, {formatProbability(b.loss)} loss
        </dd>
      </div>
      <div>
        <dt>Wait for a missing packet</dt>
        <dd>{config.gapTimeoutMillis} ms before giving up</dd>
      </div>
      <div>
        <dt>Random seed</dt>
        <dd>{config.seed}</dd>
      </div>
    </dl>
  );
}

export function RunDetailView({ id }: { id: string }) {
  const { run, timeline, error } = useRun(id);

  if (error && !run) {
    return (
      <p className="error" role="alert">
        {error}
      </p>
    );
  }
  if (!run) {
    return <p className="muted">Loading…</p>;
  }

  const results = run.results;
  const fades = run.config.lineA.fades;
  const points = timeline ? downsample(timeline, MAX_CHART_POINTS) : null;
  const bands = fades.map((f) => ({ from: f.startSeconds, to: f.startSeconds + f.lengthSeconds, label: "fade" }));
  const xs = points?.map((p) => p.second) ?? [];
  const said = verdict(run);

  return (
    <article>
      <header className="detail-header">
        <div>
          <h2>{scenarioTitle(run.config)}</h2>
          <p className="muted">
            {results
              ? `${formatCompact(results.messages)} real Nasdaq messages replayed over two simulated lines`
              : run.config.inputFile}
          </p>
        </div>
        <StatusBadge status={run.status} />
      </header>

      {(run.status === "QUEUED" || run.status === "RUNNING") && (
        <p className="muted" role="status">
          {run.status === "QUEUED" ? "Waiting for the worker…" : "Replaying the file…"}
        </p>
      )}

      {run.status === "FAILED" && (
        <p className="error" role="alert">
          This run failed: {run.error ?? "no reason recorded"}
        </p>
      )}

      {said && (
        <section className="verdict" aria-label="Result">
          <p className="verdict-main">{said.main}</p>
          {said.detail && <p className="verdict-detail">{said.detail}</p>}
        </section>
      )}

      {results && <Comparison run={run} />}

      {run.status === "COMPLETED" && !points && <p className="muted">Loading chart…</p>}

      {points && (
        <section aria-labelledby="chart-heading">
          <h3 id="chart-heading">Typical delay, second by second</h3>
          <LineChart
            title="Typical delay of the combined stream"
            xs={xs}
            series={[{ label: "typical delay", className: "s-median", values: points.map((p) => p.p50Micros / 1000) }]}
            references={[
              { value: run.config.lineA.latencyMicros / 1000, label: `wireless alone, ${formatMicros(run.config.lineA.latencyMicros)}` },
              { value: run.config.lineB.latencyMicros / 1000, label: `fiber alone, ${formatMicros(run.config.lineB.latencyMicros)}` },
            ]}
            bands={bands}
            formatY={(v) => `${v} ms`}
            formatHover={(v) => `${v.toFixed(2)} ms`}
            xLabel="seconds into the run"
          />
          {fades.length > 0 && (
            <p className="muted note">
              While the fade lasts, the combined stream has to wait for the slower fiber line to fill in what wireless
              lost, so its delay moves up toward the fiber line.
            </p>
          )}
        </section>
      )}

      {results && (
        <details className="more">
          <summary>More detail</summary>
          <h3>All the numbers</h3>
          <LatencyTable rows={results.latency} />
          <p className="muted note">
            "Typical delay" is the median. "Slowest 1%" is the delay that 99% of messages beat. "Alone" rows time the
            packets that arrived on that line and count the messages that never did.
          </p>
          {points && (
            <>
              <h3>Which line delivered each message</h3>
              <ShareChart
                title="Share of messages delivered from each line"
                xs={xs}
                shareA={points.map(shareFromA)}
                bands={bands}
                xLabel="seconds into the run"
              />
            </>
          )}
          <h3>Totals</h3>
          <Totals run={run} />
          <h3>Settings</h3>
          <Settings run={run} />
        </details>
      )}
    </article>
  );
}
