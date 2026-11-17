import { formatCount, formatMicros, formatShare } from "../format";
import type { Latency, Segment, Source } from "../types";

const SOURCE_LABEL: Record<Source, string> = {
  LINE_A: "Wireless alone",
  LINE_B: "Fiber alone",
  ARBITRATED: "Arbitrated A+B",
};
const SOURCES: Source[] = ["LINE_A", "LINE_B", "ARBITRATED"];

interface Props {
  rows: Latency[];
}

/** Latency percentiles and loss, for clear conditions and for the fade. */
export function LatencyTable({ rows }: Props) {
  const segments = (["CLEAR", "FADE"] as Segment[]).filter((s) => rows.some((r) => r.segment === s));
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th scope="col">Stream</th>
            <th scope="col" className="num">p50</th>
            <th scope="col" className="num">p99</th>
            <th scope="col" className="num">p99.9</th>
            <th scope="col" className="num">Max</th>
            <th scope="col" className="num">Messages lost</th>
            <th scope="col" className="num">Lost</th>
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
                <th colSpan={7} scope="rowgroup">
                  {segment === "CLEAR" ? "Clear conditions" : "During the fade"} · {formatCount(sent)} messages sent
                </th>
              </tr>
              {group.map((r) => (
                <tr key={r.source} className={r.source === "ARBITRATED" ? "emphasis" : undefined}>
                  <th scope="row">{SOURCE_LABEL[r.source]}</th>
                  <td className="num">{formatMicros(r.p50Micros)}</td>
                  <td className="num">{formatMicros(r.p99Micros)}</td>
                  <td className="num">{formatMicros(r.p999Micros)}</td>
                  <td className="num">{formatMicros(r.maxMicros)}</td>
                  <td className="num">{formatCount(r.messagesLost)}</td>
                  <td className="num">{formatShare(r.messagesLost, r.messagesSent, 3)}</td>
                </tr>
              ))}
            </tbody>
          );
        })}
      </table>
    </div>
  );
}
