import type { TimelinePoint } from "./types";

/** Maps a value in [d0, d1] to a position in [r0, r1]. A zero-width domain maps to the middle. */
export function linearScale(d0: number, d1: number, r0: number, r1: number): (v: number) => number {
  const span = d1 - d0;
  return (v) => (span === 0 ? (r0 + r1) / 2 : r0 + ((v - d0) / span) * (r1 - r0));
}

/** Evenly spaced round tick values from 0 up to the first one at or above {@code max}. */
export function niceTicks(max: number, count = 5): number[] {
  if (!(max > 0) || !Number.isFinite(max)) {
    return [0, 1];
  }
  const rough = max / count;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const normal = rough / magnitude;
  const step = (normal <= 1 ? 1 : normal <= 2 ? 2 : normal <= 5 ? 5 : 10) * magnitude;
  const ticks: number[] = [];
  for (let i = 0; ; i++) {
    const value = Number((i * step).toPrecision(12));
    ticks.push(value);
    if (value >= max) break;
  }
  return ticks;
}

/**
 * Reduces a timeline to at most {@code maxPoints} points so a long run still draws quickly.
 * Counts are summed, the median is averaged, and the p99 keeps the worst value in the bucket, so
 * a spike is never smoothed away.
 */
export function downsample(points: TimelinePoint[], maxPoints: number): TimelinePoint[] {
  if (points.length <= maxPoints || maxPoints < 1) {
    return points;
  }
  const size = Math.ceil(points.length / maxPoints);
  const out: TimelinePoint[] = [];
  for (let i = 0; i < points.length; i += size) {
    const bucket = points.slice(i, i + size);
    out.push({
      second: bucket[0].second,
      delivered: sum(bucket, (p) => p.delivered),
      winsA: sum(bucket, (p) => p.winsA),
      winsB: sum(bucket, (p) => p.winsB),
      lost: sum(bucket, (p) => p.lost),
      p50Micros: sum(bucket, (p) => p.p50Micros) / bucket.length,
      p99Micros: Math.max(...bucket.map((p) => p.p99Micros)),
    });
  }
  return out;
}

function sum<T>(items: T[], pick: (item: T) => number): number {
  let total = 0;
  for (const item of items) total += pick(item);
  return total;
}

/** Index of the point whose x is nearest to {@code x}; points must be sorted by x. */
export function nearestIndex(xs: number[], x: number): number {
  if (xs.length === 0) return -1;
  let lo = 0;
  let hi = xs.length - 1;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (xs[mid] < x) lo = mid + 1;
    else hi = mid;
  }
  return lo > 0 && Math.abs(xs[lo - 1] - x) <= Math.abs(xs[lo] - x) ? lo - 1 : lo;
}

/** The share of arbitrated messages that came from line A, from 0 to 1; 1 when nothing was delivered. */
export function shareFromA(p: Pick<TimelinePoint, "winsA" | "winsB">): number {
  const total = p.winsA + p.winsB;
  return total === 0 ? 1 : p.winsA / total;
}
