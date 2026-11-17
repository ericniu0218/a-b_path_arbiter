import { describe, expect, it } from "vitest";
import { downsample, linearScale, nearestIndex, niceTicks, shareFromA } from "./chart";
import type { TimelinePoint } from "./types";

const point = (second: number, over: Partial<TimelinePoint> = {}): TimelinePoint => ({
  second, delivered: 10, winsA: 6, winsB: 4, lost: 0, p50Micros: 4000, p99Micros: 6000, ...over,
});

describe("linearScale", () => {
  it("maps the ends of the domain to the ends of the range", () => {
    const s = linearScale(0, 10, 100, 0);
    expect(s(0)).toBe(100);
    expect(s(10)).toBe(0);
    expect(s(5)).toBe(50);
  });
  it("maps a zero-width domain to the middle instead of dividing by zero", () => {
    expect(linearScale(3, 3, 0, 100)(3)).toBe(50);
  });
});

describe("niceTicks", () => {
  it("starts at zero and ends at or above the maximum with round steps", () => {
    expect(niceTicks(6.55, 5)).toEqual([0, 2, 4, 6, 8]);
    expect(niceTicks(100, 5)).toEqual([0, 20, 40, 60, 80, 100]);
  });
  it("copes with fractions and with nothing to show", () => {
    expect(niceTicks(0.3, 3)).toEqual([0, 0.1, 0.2, 0.3]);
    expect(niceTicks(0)).toEqual([0, 1]);
    expect(niceTicks(Number.NaN)).toEqual([0, 1]);
  });
});

describe("downsample", () => {
  it("leaves a short timeline alone", () => {
    const points = [point(0), point(1)];
    expect(downsample(points, 10)).toBe(points);
  });
  it("sums counts, averages the median and keeps the worst p99 in each bucket", () => {
    const points = [
      point(0, { p50Micros: 4000, p99Micros: 6000, lost: 1 }),
      point(1, { p50Micros: 6000, p99Micros: 9000, lost: 2 }),
      point(2, { p50Micros: 5000, p99Micros: 5000 }),
      point(3, { p50Micros: 5000, p99Micros: 5500 }),
    ];
    const out = downsample(points, 2);
    expect(out).toHaveLength(2);
    expect(out[0]).toMatchObject({ second: 0, delivered: 20, lost: 3, p50Micros: 5000, p99Micros: 9000 });
    expect(out[1]).toMatchObject({ second: 2, p99Micros: 5500 });
  });
  it("never produces more than the requested number of points", () => {
    const points = Array.from({ length: 1001 }, (_, i) => point(i));
    expect(downsample(points, 100).length).toBeLessThanOrEqual(100);
  });
});

describe("nearestIndex", () => {
  const xs = [0, 10, 20, 30];
  it("finds the closest point, and clamps outside the range", () => {
    expect(nearestIndex(xs, 12)).toBe(1);
    expect(nearestIndex(xs, 16)).toBe(2);
    expect(nearestIndex(xs, -50)).toBe(0);
    expect(nearestIndex(xs, 99)).toBe(3);
    expect(nearestIndex([], 1)).toBe(-1);
  });
});

describe("shareFromA", () => {
  it("is the wireless fraction of delivered messages, and 1 when none were delivered", () => {
    expect(shareFromA({ winsA: 3, winsB: 1 })).toBe(0.75);
    expect(shareFromA({ winsA: 0, winsB: 0 })).toBe(1);
  });
});
