import { formatCount } from "../format";
import type { RunSummary } from "../types";
import { StatusBadge } from "./StatusBadge";

interface Props {
  runs: RunSummary[];
  selectedId: string | null;
}

function windowLabel(run: RunSummary): string {
  if (run.start && run.end) return `${run.start}–${run.end}`;
  if (run.start) return `from ${run.start}`;
  if (run.end) return `until ${run.end}`;
  return "whole file";
}

export function RunList({ runs, selectedId }: Props) {
  if (runs.length === 0) {
    return <p className="muted pad">No runs yet.</p>;
  }
  return (
    <ul className="run-list">
      {runs.map((run) => (
        <li key={run.id}>
          <a href={`#/runs/${run.id}`} aria-current={run.id === selectedId ? "page" : undefined}>
            <span className="run-title">{run.inputFile}</span>
            <span className="run-meta">
              {windowLabel(run)}
              {run.messages !== null && ` · ${formatCount(run.messages)} msgs`}
            </span>
            <span className="run-meta">
              <StatusBadge status={run.status} />
              {run.messagesLost !== null && run.messagesLost > 0 && ` ${formatCount(run.messagesLost)} lost`}
              {" · "}
              {new Date(run.createdAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
            </span>
          </a>
        </li>
      ))}
    </ul>
  );
}
