import { formatCompact, formatCount, formatLossShare, formatMicros } from "../format";
import type { Latency, Segment, Source } from "../types";

const SOURCE_LABEL: Record<Source, string> = {
  LINE_A: "Wireless alone",
  LINE_B: "Fiber alone",
  ARBITRATED: "Both lines combined",
};
const SOURCES: Source[] = ["LINE_A", "LINE_B", "ARBITRATED"];

/** Every number behind the summary: delay and loss for each stream, inside and outside the fade. */
export function LatencyTable({ rows }: { rows: Latency[] }) {
  const hasFade = rows.some((r) => r.segment === "FADE");
  const segments = (["FADE", "CLEAR"] as Segment[]).filter((s) => rows.some((r) => r.segment === s));
  const heading = (segment: Segment) => (segment === "FADE" ? "During the fade" : hasFade ? "Outside the fade" : "Whole run");

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Stream</th>
            <th scope="col" className="num">Typical delay</th>
            <th scope="col" className="num">Slowest 1%</th>
            <th scope="col" className="num">Worst</th>
            <th scope="col" className="num">Messages lost</th>
          </tr>
        </thead>
        {segments.map((segment) => {
          const group = SOURCES.map((s) => rows.find((r) => r.segment === segment && r.source === s)).filter(
            (r): r is Latency => r !== undefined,
          );
          const sent = group[0]?.messagesSent ?? 0;
          return (
            <tbody key={segment}>
              <tr className="group">
                <th colSpan={5} scope="rowgroup">
                  {heading(segment)} · {formatCompact(sent)} messages sent
                </th>
              </tr>
              {group.map((r) => (
                <tr key={r.source} className={r.source === "ARBITRATED" ? "emphasis" : undefined}>
                  <th scope="row">{SOURCE_LABEL[r.source]}</th>
                  <td className="num">{formatMicros(r.p50Micros)}</td>
                  <td className="num">{formatMicros(r.p99Micros)}</td>
                  <td className="num">{formatMicros(r.maxMicros)}</td>
                  <td className="num">
                    {formatCount(r.messagesLost)} <small>({formatLossShare(r.messagesLost, r.messagesSent)})</small>
                  </td>
                </tr>
              ))}
            </tbody>
          );
        })}
      </table>
    </div>
  );
}
