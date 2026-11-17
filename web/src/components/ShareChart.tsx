import { useState } from "react";
import { linearScale, nearestIndex } from "../chart";
import type { Band } from "./LineChart";

interface Props {
  title: string;
  xs: number[];
  /** Share of messages delivered from line A at each x, from 0 to 1. */
  shareA: number[];
  bands?: Band[];
  xLabel: string;
}

const W = 760;
const H = 200;
const M = { top: 12, right: 16, bottom: 30, left: 58 };

/** Stacked area: the part of each second's messages that came from line A, and the rest from line B. */
export function ShareChart({ title, xs, shareA, bands = [], xLabel }: Props) {
  const [hover, setHover] = useState<number | null>(null);
  if (xs.length === 0) {
    return <p className="muted">No data to chart.</p>;
  }

  const xMin = xs[0];
  const xMax = xs[xs.length - 1];
  const x = linearScale(xMin, xMax === xMin ? xMin + 1 : xMax, M.left, W - M.right);
  const y = linearScale(0, 1, H - M.bottom, M.top);
  const top = shareA.map((v, i) => `${x(xs[i]).toFixed(1)},${y(v).toFixed(1)}`);
  const areaA = [`${x(xs[0])},${y(0)}`, ...top, `${x(xs[xs.length - 1])},${y(0)}`].join(" ");
  const boundary = top.join(" ");

  function onMove(event: React.MouseEvent<SVGSVGElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const px = ((event.clientX - box.left) / box.width) * W;
    setHover(nearestIndex(xs, xMin + ((px - M.left) / (W - M.left - M.right)) * (xMax - xMin)));
  }

  return (
    <figure className="chart">
      <figcaption>{title}</figcaption>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={title} onMouseMove={onMove} onMouseLeave={() => setHover(null)}>
        <rect className="area-b" x={M.left} y={M.top} width={W - M.left - M.right} height={H - M.top - M.bottom} />
        <polygon className="area-a" points={areaA} />
        <polyline className="area-edge" points={boundary} />
        {bands.map((band) => (
          <rect key={band.label + band.from} className="band-outline" x={x(band.from)} y={M.top} width={Math.max(0, x(band.to) - x(band.from))} height={H - M.top - M.bottom} />
        ))}
        {[0, 0.5, 1].map((t) => (
          <text key={t} className="tick" x={M.left - 8} y={y(t)} textAnchor="end" dominantBaseline="middle">
            {Math.round(t * 100)}%
          </text>
        ))}
        <text className="axis-label" x={(M.left + W - M.right) / 2} y={H - 4} textAnchor="middle">
          {xLabel}
        </text>
        {hover !== null && <line className="cursor" x1={x(xs[hover])} x2={x(xs[hover])} y1={M.top} y2={H - M.bottom} />}
      </svg>
      <div className="legend" aria-live="polite">
        <span className="legend-item">
          <i className="swatch fill-a" />
          Line A (wireless){hover !== null && <strong> {(shareA[hover] * 100).toFixed(1)}%</strong>}
        </span>
        <span className="legend-item">
          <i className="swatch fill-b" />
          Line B (fiber){hover !== null && <strong> {((1 - shareA[hover]) * 100).toFixed(1)}%</strong>}
        </span>
        {hover !== null && <span className="muted">at {xs[hover]} s</span>}
      </div>
    </figure>
  );
}
