import { useId, useState } from "react";
import { linearScale, nearestIndex, niceTicks } from "../chart";

export interface Series {
  label: string;
  /** CSS class that sets the stroke colour, defined in styles.css. */
  className: string;
  values: number[];
}

export interface Band {
  from: number;
  to: number;
  label: string;
}

export interface Reference {
  value: number;
  label: string;
}

interface Props {
  title: string;
  xs: number[];
  series: Series[];
  bands?: Band[];
  references?: Reference[];
  /** Highest y value to leave room for; the axis grows to include the data and references. */
  yMin?: number;
  formatY: (v: number) => string;
  formatHover: (v: number) => string;
  xLabel: string;
}

const W = 760;
const H = 260;
const M = { top: 12, right: 16, bottom: 34, left: 58 };

/** A line chart drawn in SVG. Hover shows the values at the nearest point. */
export function LineChart({ title, xs, series, bands = [], references = [], formatY, formatHover, xLabel }: Props) {
  const descriptionId = useId();
  const [hover, setHover] = useState<number | null>(null);

  if (xs.length === 0) {
    return <p className="muted">No data to chart.</p>;
  }

  const dataMax = Math.max(...series.flatMap((s) => s.values), ...references.map((r) => r.value));
  const ticks = niceTicks(dataMax, 5);
  const yTop = ticks[ticks.length - 1];
  const xMin = xs[0];
  const xMax = xs[xs.length - 1];
  const x = linearScale(xMin, xMax === xMin ? xMin + 1 : xMax, M.left, W - M.right);
  const y = linearScale(0, yTop, H - M.bottom, M.top);
  const xTicks = niceTicks(xMax - xMin, 6).map((t) => t + xMin);

  function onMove(event: React.MouseEvent<SVGSVGElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const px = ((event.clientX - box.left) / box.width) * W;
    const value = xMin + ((px - M.left) / (W - M.left - M.right)) * (xMax - xMin);
    setHover(nearestIndex(xs, value));
  }

  return (
    <figure className="chart">
      <figcaption>{title}</figcaption>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-labelledby={descriptionId}
        onMouseMove={onMove}
        onMouseLeave={() => setHover(null)}
      >
        <title id={descriptionId}>{title}</title>

        {bands.map((band) => (
          <g key={band.label + band.from}>
            <rect className="band" x={x(band.from)} y={M.top} width={Math.max(0, x(band.to) - x(band.from))} height={H - M.top - M.bottom} />
            <text className="band-label" x={x(band.from) + 6} y={M.top + 12}>
              {band.label}
            </text>
          </g>
        ))}

        {ticks.map((t) => (
          <g key={t}>
            <line className="grid" x1={M.left} x2={W - M.right} y1={y(t)} y2={y(t)} />
            <text className="tick" x={M.left - 8} y={y(t)} textAnchor="end" dominantBaseline="middle">
              {formatY(t)}
            </text>
          </g>
        ))}
        {xTicks
          .filter((t) => t <= xMax)
          .map((t) => (
            <text key={t} className="tick" x={x(t)} y={H - M.bottom + 16} textAnchor="middle">
              {t}
            </text>
          ))}
        <text className="axis-label" x={(M.left + W - M.right) / 2} y={H - 4} textAnchor="middle">
          {xLabel}
        </text>

        {references.map((r) => (
          <g key={r.label}>
            <line className="reference" x1={M.left} x2={W - M.right} y1={y(r.value)} y2={y(r.value)} />
            <text className="reference-label" x={W - M.right - 4} y={y(r.value) - 4} textAnchor="end">
              {r.label}
            </text>
          </g>
        ))}

        {series.map((s) => (
          <polyline
            key={s.label}
            className={`line ${s.className}`}
            points={s.values.map((v, i) => `${x(xs[i]).toFixed(1)},${y(v).toFixed(1)}`).join(" ")}
          />
        ))}

        {hover !== null && (
          <g>
            <line className="cursor" x1={x(xs[hover])} x2={x(xs[hover])} y1={M.top} y2={H - M.bottom} />
            {series.map((s) => (
              <circle key={s.label} className={`dot ${s.className}`} cx={x(xs[hover])} cy={y(s.values[hover])} r={3.5} />
            ))}
          </g>
        )}
      </svg>

      <div className="legend" aria-live="polite">
        {series.map((s) => (
          <span key={s.label} className="legend-item">
            <i className={`swatch ${s.className}`} />
            {s.label}
            {hover !== null && <strong> {formatHover(s.values[hover])}</strong>}
          </span>
        ))}
        {hover !== null && <span className="muted">at {xs[hover]} s</span>}
      </div>
    </figure>
  );
}
