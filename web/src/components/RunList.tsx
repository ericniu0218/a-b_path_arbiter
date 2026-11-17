import { formatCompact } from "../format";
import { scenarioTitle } from "../scenario";
import type { RunSummary } from "../types";

interface Props {
  runs: RunSummary[];
  selectedId: string | null;
}

const STATUS_NOTE = { QUEUED: "Waiting…", RUNNING: "Running…", FAILED: "Failed", COMPLETED: "" } as const;

export function RunList({ runs, selectedId }: Props) {
  if (runs.length === 0) {
    return <p className="muted pad">No runs yet.</p>;
  }
  return (
    <ul className="run-list">
      {runs.map((run) => {
        const note = STATUS_NOTE[run.status];
        return (
          <li key={run.id}>
            <a href={`#/runs/${run.id}`} aria-current={run.id === selectedId ? "page" : undefined}>
              <span className="run-title">{scenarioTitle(run.config)}</span>
              <span className="run-meta">
                {note && <strong className={run.status === "FAILED" ? "bad" : "pending"}>{note} </strong>}
                {run.messages !== null && `${formatCompact(run.messages)} messages · `}
                {new Date(run.createdAt).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}
              </span>
            </a>
          </li>
        );
      })}
    </ul>
  );
}
