import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { parseRoute } from "../App";
import { verdict } from "./RunDetailView";
import { LatencyTable } from "./LatencyTable";
import { LineChart } from "./LineChart";
import { RunList } from "./RunList";
import { toRequest } from "./NewRunForm";
import type { Latency, RunConfig, RunDetail, RunSummary } from "../types";

afterEach(cleanup);

const row = (segment: Latency["segment"], source: Latency["source"], over: Partial<Latency> = {}): Latency => ({
  segment, source, messagesSent: 1000, messagesLost: 0, messagesWon: null,
  p50Micros: 3991.6, p99Micros: 6553.6, p999Micros: 6553.6, maxMicros: 6553.6, ...over,
});

const rows: Latency[] = [
  row("CLEAR", "LINE_A", { messagesLost: 32 }),
  row("CLEAR", "LINE_B"),
  row("CLEAR", "ARBITRATED"),
  row("FADE", "LINE_A", { messagesLost: 305, p50Micros: 3990 }),
  row("FADE", "LINE_B"),
  row("FADE", "ARBITRATED", { p50Micros: 6512.6 }),
];

describe("LatencyTable", () => {
  it("shows each segment with the three streams in plain words", () => {
    render(<LatencyTable rows={rows} />);
    expect(screen.getByText(/During the fade/)).toBeTruthy();
    expect(screen.getByText(/Outside the fade/)).toBeTruthy();
    expect(screen.getAllByText("Both lines combined")).toHaveLength(2);
    const wireless = screen.getAllByRole("row").find((r) => within(r).queryByText("Wireless alone") && r.textContent?.includes("32"));
    expect(wireless?.textContent).toContain("3.20%"); // 32 of 1000 lost
    expect(screen.getAllByText("6.51 ms").length).toBeGreaterThan(0);
  });
  it("calls the only segment the whole run when there was no fade", () => {
    render(<LatencyTable rows={rows.filter((r) => r.segment === "CLEAR")} />);
    expect(screen.getByText(/Whole run/)).toBeTruthy();
    expect(screen.queryByText(/During the fade/)).toBeNull();
  });
});

const fadeConfig: RunConfig = {
  inputFile: "f", start: null, end: null, maxMessages: null, gapTimeoutMillis: 5, seed: 42,
  lineA: { latencyMicros: 3980, jitterMicros: 2, loss: 1e-5, bandwidthMbps: 100, maxQueueMillis: 2, fades: [{ startSeconds: 240, lengthSeconds: 120, loss: 0.3 }] },
  lineB: { latencyMicros: 6550, jitterMicros: 1, loss: 1e-7 },
};

describe("RunList", () => {
  const run: RunSummary = {
    id: "7ec7951f-fc40-4564-a9ac-b7f8e148f34e", status: "COMPLETED", createdAt: "2026-11-17T08:00:00Z",
    finishedAt: null, inputFile: "day.itch", start: "09:30:00", end: "09:40:00", messages: 11769925, messagesLost: 0,
    config: fadeConfig,
  };
  it("links each run and marks the selected one", () => {
    render(<RunList runs={[run]} selectedId={run.id} />);
    const link = screen.getByRole("link");
    expect(link.getAttribute("href")).toBe(`#/runs/${run.id}`);
    expect(link.getAttribute("aria-current")).toBe("page");
    expect(link.textContent).toContain("Fade: 30% loss for 2 min");
    expect(link.textContent).toContain("11.8 million messages");
  });
  it("flags a run that is still going or has failed", () => {
    render(<RunList runs={[{ ...run, status: "RUNNING", messages: null }, { ...run, id: "b".repeat(36), status: "FAILED" }]} selectedId={null} />);
    expect(screen.getByText(/Running…/)).toBeTruthy();
    expect(screen.getByText(/Failed/)).toBeTruthy();
  });
  it("says so when there are no runs", () => {
    render(<RunList runs={[]} selectedId={null} />);
    expect(screen.getByText("No runs yet.")).toBeTruthy();
  });
});

describe("LineChart", () => {
  it("draws one polyline per series and a labelled reference line", () => {
    const { container } = render(
      <LineChart
        title="Latency" xs={[0, 1, 2]} xLabel="s" formatY={(v) => `${v} ms`} formatHover={(v) => String(v)}
        series={[{ label: "median", className: "s-median", values: [4, 4, 5] }, { label: "p99", className: "s-p99", values: [6, 6, 7] }]}
        references={[{ value: 6.55, label: "fiber alone" }]}
        bands={[{ from: 1, to: 2, label: "fade" }]}
      />,
    );
    expect(container.querySelectorAll("polyline")).toHaveLength(2);
    expect(screen.getByText("fiber alone")).toBeTruthy();
    expect(screen.getByText("fade")).toBeTruthy();
    expect(screen.getByRole("img", { name: "Latency" })).toBeTruthy();
  });
  it("says so instead of drawing an empty chart", () => {
    render(<LineChart title="t" xs={[]} xLabel="s" series={[]} formatY={String} formatHover={String} />);
    expect(screen.getByText("No data to chart.")).toBeTruthy();
  });
});

describe("verdict", () => {
  const base = {
    id: "x", status: "COMPLETED", createdAt: "", startedAt: null, finishedAt: null, error: null,
    config: fadeConfig,
    results: { messages: 1000, dataPackets: 500, durationSeconds: 600, gaps: 0, messagesLost: 0, averageMbps: 10, peakMbps: 400, burstWindowsOverCapacity: 1, burstWindowsActive: 100, maxBufferedPackets: 3, latency: rows },
  } as RunDetail;

  it("says nothing was lost, what wireless alone would have lost, and the cost in delay", () => {
    const v = verdict(base)!;
    expect(v.main).toBe("Using both lines, no messages were lost.");
    expect(v.detail).toContain("During the fade, the wireless line alone would have lost 30.5% of its messages");
    expect(v.detail).toContain("typical delay was 6.51 ms instead of 3.99 ms");
  });
  it("reports losses when there are some", () => {
    expect(verdict({ ...base, results: { ...base.results!, messagesLost: 5 } })!.main).toBe(
      "Using both lines, 5 of 1,000 messages were lost.",
    );
  });
  it("says the delay stayed put when combining added almost nothing", () => {
    const flat = rows.map((r) => (r.segment === "FADE" && r.source === "ARBITRATED" ? { ...r, p50Micros: 3995 } : r));
    expect(verdict({ ...base, results: { ...base.results!, latency: flat } })!.detail).toContain("stayed at 4.00 ms");
  });
  it("has nothing to say before there are results", () => {
    expect(verdict({ ...base, results: null })).toBeNull();
  });
});

describe("toRequest", () => {
  const fields = {
    inputFile: "day.itch", start: "09:30:00", end: "", loss: "0.001", mbps: "100", fade: true,
    fadeStart: "240", fadeLength: "120", fadeLoss: "30", gapTimeout: "5", seed: "42",
  };
  it("converts percentages to fractions and builds the fade", () => {
    const r = toRequest(fields);
    expect(r.lineA?.loss).toBeCloseTo(1e-5);
    expect(r.lineA?.fades).toEqual([{ startSeconds: 240, lengthSeconds: 120, loss: 0.3 }]);
    expect(r.start).toBe("09:30:00");
    expect(r.end).toBeUndefined();
  });
  it("sends no fade when the box is unticked", () => {
    expect(toRequest({ ...fields, fade: false }).lineA?.fades).toBeUndefined();
  });
  it("names the field that is not a number", () => {
    expect(() => toRequest({ ...fields, mbps: "fast" })).toThrow("Bandwidth must be a number");
    expect(() => toRequest({ ...fields, seed: "" })).toThrow("Seed must be a number");
  });
});

describe("parseRoute", () => {
  it("recognises a run link and falls back to the new-run page otherwise", () => {
    expect(parseRoute("#/runs/7ec7951f-fc40-4564-a9ac-b7f8e148f34e")).toEqual({ page: "run", id: "7ec7951f-fc40-4564-a9ac-b7f8e148f34e" });
    expect(parseRoute("")).toEqual({ page: "new" });
    expect(parseRoute("#/runs/not-an-id")).toEqual({ page: "new" });
  });
});
