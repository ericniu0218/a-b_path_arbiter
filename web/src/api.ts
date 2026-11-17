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

export const api = {
  inputs: () => request<string[]>("/api/inputs"),
  runs: () => request<RunSummary[]>("/api/runs"),
  run: (id: string) => request<RunDetail>(`/api/runs/${encodeURIComponent(id)}`),
  timeline: (id: string) => request<TimelinePoint[]>(`/api/runs/${encodeURIComponent(id)}/timeline`),
  createRun: (body: RunRequest) =>
    request<RunDetail>("/api/runs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
};

export function isFinished(status: RunDetail["status"]): boolean {
  return status === "COMPLETED" || status === "FAILED";
}
