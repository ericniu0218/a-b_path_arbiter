import { describe, expect, it } from "vitest";
import { scenarioTitle } from "./scenario";
import type { RunConfig } from "./types";

const config = (over: Partial<RunConfig["lineA"]>): RunConfig => ({
  inputFile: "f", start: null, end: null, maxMessages: null, gapTimeoutMillis: 5, seed: 1,
  lineA: { latencyMicros: 3980, jitterMicros: 2, loss: 1e-5, bandwidthMbps: 100, maxQueueMillis: 2, fades: [], ...over },
  lineB: { latencyMicros: 6550, jitterMicros: 1, loss: 1e-7 },
});

describe("scenarioTitle", () => {
  it("names a fade by its loss and length", () => {
    expect(scenarioTitle(config({ fades: [{ startSeconds: 240, lengthSeconds: 120, loss: 0.3 }] }))).toBe("Fade: 30% loss for 2 min");
  });
  it("says 'from the start' when the fade begins at zero", () => {
    expect(scenarioTitle(config({ fades: [{ startSeconds: 0, lengthSeconds: 700, loss: 0.02 }] }))).toBe("Fade: 2% loss from the start");
  });
  it("mentions extra fades", () => {
    const fades = [{ startSeconds: 10, lengthSeconds: 30, loss: 0.5 }, { startSeconds: 100, lengthSeconds: 30, loss: 0.5 }];
    expect(scenarioTitle(config({ fades }))).toBe("Fade: 50% loss for 30 s (+1 more)");
  });
  it("describes a bandwidth-limited run with no fade", () => {
    expect(scenarioTitle(config({ bandwidthMbps: 50 }))).toBe("50 Mbps wireless link, no fade");
  });
  it("falls back to 'No fade' when nothing is limited", () => {
    expect(scenarioTitle(config({ bandwidthMbps: 0 }))).toBe("No fade");
  });
});
