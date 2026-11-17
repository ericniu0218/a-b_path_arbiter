/** The JSON shapes returned by the service. Mirrors RunViews.java. */

export type RunStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED";
export type Segment = "CLEAR" | "FADE";
export type Source = "LINE_A" | "LINE_B" | "ARBITRATED";

export interface RunSummary {
  id: string;
  status: RunStatus;
  createdAt: string;
  finishedAt: string | null;
  inputFile: string;
  start: string | null;
  end: string | null;
  messages: number | null;
  messagesLost: number | null;
}

export interface Fade {
  startSeconds: number;
  lengthSeconds: number;
  loss: number;
}

export interface RunConfig {
  inputFile: string;
  start: string | null;
  end: string | null;
  maxMessages: number | null;
  lineA: {
    latencyMicros: number;
    jitterMicros: number;
    loss: number;
    bandwidthMbps: number;
    maxQueueMillis: number;
    fades: Fade[];
  };
  lineB: { latencyMicros: number; jitterMicros: number; loss: number };
  gapTimeoutMillis: number;
  seed: number;
}

export interface Latency {
  segment: Segment;
  source: Source;
  messagesSent: number;
  messagesLost: number;
  messagesWon: number | null;
  p50Micros: number;
  p99Micros: number;
  p999Micros: number;
  maxMicros: number;
}

export interface RunResults {
  messages: number;
  dataPackets: number;
  durationSeconds: number;
  gaps: number;
  messagesLost: number;
  averageMbps: number;
  peakMbps: number;
  burstWindowsOverCapacity: number;
  burstWindowsActive: number;
  maxBufferedPackets: number;
  latency: Latency[];
}

export interface RunDetail {
  id: string;
  status: RunStatus;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
  config: RunConfig;
  results: RunResults | null;
}

export interface TimelinePoint {
  second: number;
  delivered: number;
  winsA: number;
  winsB: number;
  lost: number;
  p50Micros: number;
  p99Micros: number;
}

/** What the new-run form sends. Only inputFile is required by the service. */
export interface RunRequest {
  inputFile: string;
  start?: string;
  end?: string;
  maxMessages?: number;
  lineA?: {
    loss?: number;
    bandwidthMbps?: number;
    fades?: Fade[];
  };
  gapTimeoutMillis?: number;
  seed?: number;
}
