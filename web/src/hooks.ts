import { useCallback, useEffect, useRef, useState } from "react";
import { api, isFinished } from "./api";
import type { RunDetail, RunSummary, TimelinePoint } from "./types";

/** Calls {@code load} now and then every {@code intervalMs}, ignoring results that arrive after unmount. */
export function usePolling<T>(load: () => Promise<T>, intervalMs: number, deps: unknown[], stop?: (v: T) => boolean) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loadRef = useRef(load);
  loadRef.current = load;
  const stopRef = useRef(stop);
  stopRef.current = stop;

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    setData(null);
    setError(null);

    async function tick() {
      try {
        const value = await loadRef.current();
        if (cancelled) return;
        setData(value);
        setError(null);
        if (stopRef.current?.(value)) return;
      } catch (e) {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : String(e));
      }
      timer = setTimeout(tick, intervalMs);
    }
    void tick();

    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return { data, error };
}

export function useRuns(): { runs: RunSummary[] | null; error: string | null } {
  const { data, error } = usePolling(api.runs, 3000, []);
  return { runs: data, error };
}

/** A run, refreshed every second until it finishes, plus its timeline once it has completed. */
export function useRun(id: string): {
  run: RunDetail | null;
  timeline: TimelinePoint[] | null;
  error: string | null;
} {
  const { data: run, error } = usePolling(() => api.run(id), 1000, [id], (r) => isFinished(r.status));
  const [timeline, setTimeline] = useState<TimelinePoint[] | null>(null);
  const completed = run?.status === "COMPLETED";

  useEffect(() => {
    setTimeline(null);
  }, [id]);

  const loadTimeline = useCallback(async () => {
    setTimeline(await api.timeline(id));
  }, [id]);

  useEffect(() => {
    if (completed) void loadTimeline();
  }, [completed, loadTimeline]);

  return { run, timeline, error };
}
