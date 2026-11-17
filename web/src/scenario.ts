import { formatMbps, formatProbability, formatSpan } from "./format";
import type { RunConfig } from "./types";

/** A short plain-language name for what a run simulated, such as "Fade: 30% loss for 2 min". */
export function scenarioTitle(config: RunConfig): string {
  const a = config.lineA;
  if (a.fades.length > 0) {
    const f = a.fades[0];
    const loss = `${formatProbability(f.loss)} loss`;
    const text = f.startSeconds === 0 ? `${loss} from the start` : `${loss} for ${formatSpan(f.lengthSeconds)}`;
    return `Fade: ${text}${a.fades.length > 1 ? ` (+${a.fades.length - 1} more)` : ""}`;
  }
  return a.bandwidthMbps > 0 ? `${formatMbps(a.bandwidthMbps)} wireless link, no fade` : "No fade";
}
