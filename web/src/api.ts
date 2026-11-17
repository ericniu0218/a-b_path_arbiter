import type { RunDetail, RunRequest, RunSummary, TimelinePoint } from "./types";

/** An error from the service, carrying the problem-detail text when there is one. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface Api {
  inputs(): Promise<string[]>;
  runs(): Promise<RunSummary[]>;
  run(id: string): Promise<RunDetail>;
  timeline(id: string): Promise<TimelinePoint[]>;
  createRun(body: RunRequest): Promise<RunDetail>;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, init);
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`.trim();
    try {
      const problem = (await response.json()) as { detail?: string; title?: string };
      message = problem.detail ?? problem.title ?? message;
    } catch {
      // The body was not JSON; keep the status line.
    }
    throw new ApiError(message, response.status);
  }
  return (await response.json()) as T;
}

/** Talks to the Spring Boot service under /api. */
function liveApi(): Api {
  return {
    inputs: () => request<string[]>("/api/inputs"),
    runs: () => request<RunSummary[]>("/api/runs"),
    run: (id) => request<RunDetail>(`/api/runs/${encodeURIComponent(id)}`),
    timeline: (id) => request<TimelinePoint[]>(`/api/runs/${encodeURIComponent(id)}/timeline`),
    createRun: (body) =>
      request<RunDetail>("/api/runs", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
  };
}

/**
 * Reads pre-recorded runs from static JSON under {@code <base>demo/}, so the dashboard can be hosted
 * with no backend. Files are written by {@code npm run export-demo}.
 */
function demoApi(base: string): Api {
  const root = `${base}demo`;
  return {
    inputs: async () => [],
    runs: () => request<RunSummary[]>(`${root}/runs.json`),
    run: (id) => request<RunDetail>(`${root}/runs/${encodeURIComponent(id)}.json`),
    timeline: (id) => request<TimelinePoint[]>(`${root}/runs/${encodeURIComponent(id)}.timeline.json`),
    createRun: () => Promise.reject(new ApiError("This is a demo with recorded runs; starting new runs is disabled.", 405)),
  };
}

export function createApi(demo: boolean, base = "/"): Api {
  return demo ? demoApi(base.endsWith("/") ? base : `${base}/`) : liveApi();
}

/** True when the app was built with VITE_DEMO=true. */
export const isDemo = import.meta.env.VITE_DEMO === "true";

export const api: Api = createApi(isDemo, import.meta.env.BASE_URL);

export function isFinished(status: RunDetail["status"]): boolean {
  return status === "COMPLETED" || status === "FAILED";
}
