import { downsample, shareFromA } from "../chart";
import { formatCount, formatDuration, formatMbps, formatMicros, formatProbability, formatShare } from "../format";
import { useRun } from "../hooks";
import type { Latency, RunDetail, Source } from "../types";
import { LatencyTable } from "./LatencyTable";
import { LineChart } from "./LineChart";
import { ShareChart } from "./ShareChart";
import { StatusBadge } from "./StatusBadge";

const MAX_CHART_POINTS = 600;

function find(rows: Latency[], segment: "CLEAR" | "FADE", source: Source): Latency | undefined {
  return rows.find((r) => r.segment === segment && r.source === source);
}

/** One factual sentence about what arbitration did, built from the stored numbers. */
export function headline(run: RunDetail): string | null {
  const results = run.results;
  if (!results) return null;
  const lost = `Arbitration delivered ${formatCount(results.messages - results.messagesLost)} of ${formatCount(results.messages)} messages`;
  const gaps = results.gaps === 0 ? "with no gaps" : `with ${formatCount(results.gaps)} gap${results.gaps === 1 ? "" : "s"}`;
  const segment = run.config.lineA.fades.length > 0 ? "FADE" : "CLEAR";
  const a = find(results.latency, segment, "LINE_A");
  const arb = find(results.latency, segment, "ARBITRATED");
  if (!a || !arb) return `${lost}, ${gaps}.`;
  const where = segment === "FADE" ? "During the fade" : "Overall";
  return (
    `${lost}, ${gaps}. ${where}, wireless alone lost ${formatShare(a.messagesLost, a.messagesSent, 1)} of its messages; ` +
    `the arbitrated median was ${formatMicros(arb.p50Micros)} and p99 ${formatMicros(arb.p99Micros)}.`
  );
}

function Scenario({ run }: { run: RunDetail }) {
  const { config } = run;
  const a = config.lineA;
  const b = config.lineB;
  return (
    <dl className="scenario">
      <div>
        <dt>Line A, wireless</dt>
        <dd>
          {formatMicros(a.latencyMicros)} · {formatMbps(a.bandwidthMbps)} · loss {formatProbability(a.loss)}
          {a.fades.map((f) => (
            <span key={f.startSeconds} className="fade-tag">
              fade {f.startSeconds}–{f.startSeconds + f.lengthSeconds} s at {formatProbability(f.loss)} loss
            </span>
          ))}
        </dd>
      </div>
      <div>
        <dt>Line B, fiber</dt>
        <dd>
          {formatMicros(b.latencyMicros)} · loss {formatProbability(b.loss)}
        </dd>
      </div>
      <div>
        <dt>Arbiter</dt>
        <dd>
          gap timeout {config.gapTimeoutMillis} ms · seed {config.seed}
        </dd>
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

  return (
    <article>
      <header className="detail-header">
        <div>
          <h2>{run.config.inputFile}</h2>
          <p className="muted">
            {run.config.start ?? "start of file"} to {run.config.end ?? "end of file"}
          </p>
        </div>
        <StatusBadge status={run.status} />
      </header>

      <Scenario run={run} />

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

      {results && (
        <>
          <p className="headline">{headline(run)}</p>

          <section className="stats" aria-label="Run totals">
            <div className="stat">
              <span className="stat-value">{formatCount(results.messages)}</span>
              <span className="stat-label">messages in {formatCount(results.dataPackets)} packets</span>
            </div>
            <div className="stat">
              <span className="stat-value">{formatCount(results.messagesLost)}</span>
              <span className="stat-label">lost after arbitration · {results.gaps} gaps</span>
            </div>
            <div className="stat">
              <span className="stat-value">
                {results.peakMbps.toFixed(0)} <small>Mbps peak</small>
              </span>
              <span className="stat-label">
                average {results.averageMbps.toFixed(1)} Mbps ·{" "}
                {results.averageMbps > 0 ? `${(results.peakMbps / results.averageMbps).toFixed(0)}×` : "–"} burst
              </span>
            </div>
            <div className="stat">
              <span className="stat-value">
                {run.config.lineA.bandwidthMbps === 0
                  ? "–"
                  : formatShare(results.burstWindowsOverCapacity, results.burstWindowsActive, 2)}
              </span>
              <span className="stat-label">
                {run.config.lineA.bandwidthMbps === 0
                  ? "line A has unlimited bandwidth in this run"
                  : "of 1 ms windows exceed line A's capacity"}
              </span>
            </div>
          </section>

          <h3>Latency, sender to receiver</h3>
          <LatencyTable rows={results.latency} />
          <p className="muted note">
            "Alone" rows time the packets that arrived on that line and count the messages that never did. Duration{" "}
            {formatDuration(results.durationSeconds)} of exchange time.
          </p>
        </>
      )}

      {run.status === "COMPLETED" && !points && <p className="muted">Loading timeline…</p>}

      {points && (
        <>
          <h3>Over time</h3>
          <LineChart
            title="Arbitrated latency per second (median and p99)"
            xs={xs}
            series={[
              { label: "median", className: "s-median", values: points.map((p) => p.p50Micros / 1000) },
              { label: "p99", className: "s-p99", values: points.map((p) => p.p99Micros / 1000) },
            ]}
            references={[
              { value: run.config.lineA.latencyMicros / 1000, label: "wireless alone" },
              { value: run.config.lineB.latencyMicros / 1000, label: "fiber alone" },
            ]}
            bands={bands}
            formatY={(v) => `${v} ms`}
            formatHover={(v) => `${v.toFixed(2)} ms`}
            xLabel="seconds since the first message"
          />
          <ShareChart
            title="Which line's copy was delivered, per second"
            xs={xs}
            shareA={points.map(shareFromA)}
            bands={bands}
            xLabel="seconds since the first message"
          />
          <p className="muted note">
            {points.length < (timeline?.length ?? 0) &&
              `Long run: each point covers ${Math.ceil((timeline?.length ?? 0) / points.length)} seconds. `}
            The latency chart keeps the worst p99 in each bucket, so spikes stay visible.
          </p>
        </>
      )}
    </article>
  );
}
