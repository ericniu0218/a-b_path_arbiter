import { useEffect, useState } from "react";
import { api } from "../api";
import type { RunRequest } from "../types";

interface Fields {
  inputFile: string;
  start: string;
  end: string;
  loss: string;
  mbps: string;
  fade: boolean;
  fadeStart: string;
  fadeLength: string;
  fadeLoss: string;
  gapTimeout: string;
  seed: string;
}

const DEFAULTS: Fields = {
  inputFile: "",
  start: "09:30:00",
  end: "09:40:00",
  loss: "0.001",
  mbps: "100",
  fade: true,
  fadeStart: "240",
  fadeLength: "120",
  fadeLoss: "30",
  gapTimeout: "5",
  seed: "42",
};

/** The scenarios behind the README's tables, one click each. */
const PRESETS: { label: string; fields: Partial<Fields> }[] = [
  { label: "Two-minute fade at 30% loss", fields: { fade: true, fadeStart: "240", fadeLength: "120", fadeLoss: "30", loss: "0.001", mbps: "100" } },
  { label: "2% loss throughout", fields: { fade: true, fadeStart: "0", fadeLength: "700", fadeLoss: "2", loss: "0.001", mbps: "0" } },
  { label: "Narrow 50 Mbps link, no fade", fields: { fade: false, loss: "0.001", mbps: "50" } },
];

export function toRequest(f: Fields): RunRequest {
  const number = (name: string, text: string): number => {
    const value = Number(text);
    if (text.trim() === "" || !Number.isFinite(value)) {
      throw new Error(`${name} must be a number`);
    }
    return value;
  };
  const request: RunRequest = {
    inputFile: f.inputFile,
    lineA: { loss: number("Wireless loss", f.loss) / 100, bandwidthMbps: number("Bandwidth", f.mbps) },
    gapTimeoutMillis: number("Gap timeout", f.gapTimeout),
    seed: number("Seed", f.seed),
  };
  if (f.start.trim()) request.start = f.start.trim();
  if (f.end.trim()) request.end = f.end.trim();
  if (f.fade) {
    request.lineA!.fades = [
      {
        startSeconds: number("Fade start", f.fadeStart),
        lengthSeconds: number("Fade length", f.fadeLength),
        loss: number("Fade loss", f.fadeLoss) / 100,
      },
    ];
  }
  return request;
}

export function NewRunForm() {
  const [fields, setFields] = useState<Fields>(DEFAULTS);
  const [inputs, setInputs] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    api
      .inputs()
      .then((files) => {
        setInputs(files);
        setFields((f) => (f.inputFile || files.length === 0 ? f : { ...f, inputFile: files[0] }));
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  const set = <K extends keyof Fields>(key: K, value: Fields[K]) => setFields((f) => ({ ...f, [key]: value }));

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const run = await api.createRun(toRequest(fields));
      window.location.hash = `#/runs/${run.id}`;
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSubmitting(false);
    }
  }

  const text = (id: string, label: string, key: keyof Fields, hint?: string) => (
    <label htmlFor={id}>
      {label}
      <input id={id} value={String(fields[key])} onChange={(e) => set(key, e.target.value as never)} inputMode="decimal" />
      {hint && <small>{hint}</small>}
    </label>
  );

  return (
    <form onSubmit={submit} className="form">
      <h2>New run</h2>
      <p className="muted">
        Replays an ITCH file over two simulated lines and stores the result. The path settings are illustrative, not
        measurements of any real network.
      </p>

      <div className="presets" role="group" aria-label="Presets">
        {PRESETS.map((p) => (
          <button key={p.label} type="button" className="chip" onClick={() => setFields((f) => ({ ...f, ...p.fields }))}>
            {p.label}
          </button>
        ))}
      </div>

      <fieldset>
        <legend>Input</legend>
        <label htmlFor="inputFile">
          File
          <select id="inputFile" value={fields.inputFile} onChange={(e) => set("inputFile", e.target.value)} required>
            {inputs === null && <option>Loading…</option>}
            {inputs?.length === 0 && <option value="">No files in the data directory</option>}
            {inputs?.map((f) => (
              <option key={f}>{f}</option>
            ))}
          </select>
        </label>
        <div className="row">
          {text("start", "Start", "start", "HH:MM:SS, blank for the start of the file")}
          {text("end", "End", "end", "blank for the end of the file")}
        </div>
      </fieldset>

      <fieldset>
        <legend>Line A, wireless</legend>
        <div className="row">
          {text("loss", "Loss, %", "loss", "outside any fade")}
          {text("mbps", "Bandwidth, Mbps", "mbps", "0 is unlimited")}
        </div>
        <label className="check">
          <input type="checkbox" checked={fields.fade} onChange={(e) => set("fade", e.target.checked)} />
          Include a fade
        </label>
        {fields.fade && (
          <div className="row">
            {text("fadeStart", "Fade start, s", "fadeStart", "from the first message")}
            {text("fadeLength", "Fade length, s", "fadeLength")}
            {text("fadeLoss", "Fade loss, %", "fadeLoss")}
          </div>
        )}
      </fieldset>

      <fieldset>
        <legend>Arbiter</legend>
        <div className="row">
          {text("gapTimeout", "Gap timeout, ms", "gapTimeout", "must exceed the latency gap between lines")}
          {text("seed", "Seed", "seed", "same seed, same result")}
        </div>
      </fieldset>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" className="primary" disabled={submitting || !fields.inputFile}>
        {submitting ? "Starting…" : "Run simulation"}
      </button>
    </form>
  );
}
