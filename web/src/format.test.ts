import { describe, expect, it } from "vitest";
import { formatCount, formatDuration, formatMbps, formatMicros, formatProbability, formatShare } from "./format";

describe("format", () => {
  it("formats microseconds as ms from one millisecond up", () => {
    expect(formatMicros(3991.6)).toBe("3.99 ms");
    expect(formatMicros(820.4)).toBe("820 µs");
  });
  it("formats counts with separators", () => {
    expect(formatCount(11769925)).toBe("11,769,925");
  });
  it("formats a share, and a dash when there is nothing to divide", () => {
    expect(formatShare(1, 4, 1)).toBe("25.0%");
    expect(formatShare(0, 0)).toBe("–");
  });
  it("formats probabilities as percentages without trailing zeros", () => {
    expect(formatProbability(0.3)).toBe("30%");
    expect(formatProbability(1e-5)).toBe("0.001%");
    expect(formatProbability(0.02)).toBe("2%");
    expect(formatProbability(0)).toBe("0%");
  });
  it("formats durations", () => {
    expect(formatDuration(45)).toBe("45s");
    expect(formatDuration(601)).toBe("10m 01s");
    expect(formatDuration(23400)).toBe("6h 30m");
  });
  it("formats bandwidth, with 0 meaning unlimited", () => {
    expect(formatMbps(0)).toBe("unlimited");
    expect(formatMbps(100)).toBe("100 Mbps");
    expect(formatMbps(12.5)).toBe("12.5 Mbps");
  });
});
