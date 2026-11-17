import { useEffect, useState } from "react";
import { isDemo } from "./api";
import { NewRunForm } from "./components/NewRunForm";
import { RunDetailView } from "./components/RunDetailView";
import { RunList } from "./components/RunList";
import { useRuns } from "./hooks";

type Route = { page: "new" } | { page: "run"; id: string };

export function parseRoute(hash: string): Route {
  const match = /^#\/runs\/([0-9a-fA-F-]{36})$/.exec(hash);
  return match ? { page: "run", id: match[1] } : { page: "new" };
}

export function App() {
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash));
  const { runs, error } = useRuns();

  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);

  // A demo has no form to show, so open the first recorded run instead.
  const firstRunId = runs?.[0]?.id;
  useEffect(() => {
    if (isDemo && route.page === "new" && firstRunId) {
      window.location.hash = `#/runs/${firstRunId}`;
    }
  }, [route.page, firstRunId]);

  return (
    <div className="layout">
      <aside className="sidebar">
        <header>
          <h1>Protected Feed Lab</h1>
          <p className="muted">A/B line arbitration on real ITCH data</p>
        </header>
        {isDemo ? (
          <p className="demo-note">
            Demo: these runs were recorded from real Nasdaq data. Starting new runs needs the backend; see the project
            README.
          </p>
        ) : (
          <a className="primary link-button" href="#/new">
            New run
          </a>
        )}
        {error && !runs && <p className="error">Cannot reach the service: {error}</p>}
        {runs && <RunList runs={runs} selectedId={route.page === "run" ? route.id : null} />}
      </aside>
      <main>
        {route.page === "run" ? (
          <RunDetailView key={route.id} id={route.id} />
        ) : isDemo ? (
          <p className="muted">Loading…</p>
        ) : (
          <NewRunForm />
        )}
      </main>
    </div>
  );
}
