/** "3.99 ms", or "820 µs" below a millisecond. */
export function formatMicros(micros: number): string {
  if (micros >= 1000) {
    return `${(micros / 1000).toFixed(2)} ms`;
  }
  return `${Math.round(micros)} µs`;
}

export function formatCount(n: number): string {
  return n.toLocaleString("en-US");
}

/** Share of a whole as a percentage with a fixed number of decimals; "–" when there is no whole. */
export function formatShare(part: number, whole: number, digits = 2): string {
  return whole === 0 ? "–" : `${((100 * part) / whole).toFixed(digits)}%`;
}

/** A probability (0 to 1) as a percentage without trailing zeros: 0.3 gives "30%", 1e-5 gives "0.001%". */
export function formatProbability(p: number): string {
  const percent = p * 100;
  const text = percent >= 1 ? percent.toFixed(2) : percent.toPrecision(2);
  return `${text.replace(/\.?0+$/, "")}%`;
}

export function formatDuration(seconds: number): string {
  const whole = Math.round(seconds);
  const h = Math.floor(whole / 3600);
  const m = Math.floor((whole % 3600) / 60);
  const s = whole % 60;
  if (h > 0) return `${h}h ${String(m).padStart(2, "0")}m`;
  if (m > 0) return `${m}m ${String(s).padStart(2, "0")}s`;
  return `${s}s`;
}

export function formatMbps(mbps: number): string {
  return mbps === 0 ? "unlimited" : `${mbps >= 100 ? mbps.toFixed(0) : Number(mbps.toFixed(1))} Mbps`;
}

/** "30 s", "2 min", "1 h 30 min" for a length of time given in seconds. */
export function formatSpan(seconds: number): string {
  const s = Math.round(seconds);
  if (s < 60) return `${s} s`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  return m % 60 === 0 ? `${h} h` : `${h} h ${m % 60} min`;
}

/** "11.8 million" from a million up, otherwise the number with separators. */
export function formatCompact(n: number): string {
  return n >= 1_000_000 ? `${(n / 1_000_000).toFixed(1)} million` : formatCount(n);
}

/** Lost messages as a short percentage: "0%", "<0.01%", "3.20%", "30.5%". */
export function formatLossShare(lost: number, sent: number): string {
  if (sent === 0) return "–";
  if (lost === 0) return "0%";
  const percent = (100 * lost) / sent;
  if (percent < 0.01) return "<0.01%";
  return percent < 10 ? `${percent.toFixed(2)}%` : `${percent.toFixed(1)}%`;
}
