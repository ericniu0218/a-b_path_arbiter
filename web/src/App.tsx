import { useEffect, useState } from "react";
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

  return (
    <div className="layout">
      <aside className="sidebar">
        <header>
          <h1>Protected Feed Lab</h1>
          <p className="muted">A/B line arbitration on real ITCH data</p>
        </header>
        <a className="primary link-button" href="#/new">
          New run
        </a>
        {error && !runs && <p className="error">Cannot reach the service: {error}</p>}
        {runs && <RunList runs={runs} selectedId={route.page === "run" ? route.id : null} />}
      </aside>
      <main>{route.page === "new" ? <NewRunForm /> : <RunDetailView key={route.id} id={route.id} />}</main>
    </div>
  );
}
