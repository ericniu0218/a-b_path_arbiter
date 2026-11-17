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
const PRESETS: { title: string; description: string; fields: Partial<Fields> }[] = [
  {
    title: "Two-minute fade",
    description: "The wireless line drops 30% of packets for two minutes.",
    fields: { fade: true, fadeStart: "240", fadeLength: "120", fadeLoss: "30", loss: "0.001", mbps: "100" },
  },
  {
    title: "Steady 2% loss",
    description: "The wireless line loses 2% of packets the whole time.",
    fields: { fade: true, fadeStart: "0", fadeLength: "700", fadeLoss: "2", loss: "0.001", mbps: "0" },
  },
  {
    title: "Narrow link",
    description: "No fade, but the wireless link is limited to 50 Mbps.",
    fields: { fade: false, loss: "0.001", mbps: "50" },
  },
];

export type { Fields };
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
  const [fields, setFields] = useState<Fields>({ ...DEFAULTS, ...PRESETS[0].fields });
  const [preset, setPreset] = useState<number | null>(0);
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

  // Editing any field by hand means the settings no longer match a preset.
  const set = <K extends keyof Fields>(key: K, value: Fields[K]) => {
    setPreset(null);
    setFields((f) => ({ ...f, [key]: value }));
  };

  function choose(index: number) {
    setPreset(index);
    setFields((f) => ({ ...f, ...PRESETS[index].fields }));
  }

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
      <h2>Start a run</h2>
      <p className="muted">
        Replay real market data over a fast wireless line and a slower fiber line, and see what combining them gives
        you. The line settings are illustrative, not measurements of a real network.
      </p>

      <fieldset className="plain">
        <legend>What should go wrong on the wireless line?</legend>
        <div className="presets">
          {PRESETS.map((p, i) => (
            <button key={p.title} type="button" className="preset" aria-pressed={preset === i} onClick={() => choose(i)}>
              <strong>{p.title}</strong>
              <span>{p.description}</span>
            </button>
          ))}
        </div>
      </fieldset>

      <label htmlFor="inputFile">
        Data file
        <select id="inputFile" value={fields.inputFile} onChange={(e) => set("inputFile", e.target.value)} required>
          {inputs === null && <option>Loading…</option>}
          {inputs?.length === 0 && <option value="">No files in the data directory</option>}
          {inputs?.map((f) => (
            <option key={f}>{f}</option>
          ))}
        </select>
      </label>

      <details className="more">
        <summary>Advanced settings</summary>
        <div className="advanced">
          <div className="row">
            {text("start", "Start time", "start", "HH:MM:SS; blank for the start of the file")}
            {text("end", "End time", "end", "blank for the end of the file")}
          </div>
          <div className="row">
            {text("loss", "Wireless loss, %", "loss", "outside any fade")}
            {text("mbps", "Wireless limit, Mbps", "mbps", "0 means no limit")}
          </div>
          <label className="check">
            <input type="checkbox" checked={fields.fade} onChange={(e) => set("fade", e.target.checked)} />
            Include a fade
          </label>
          {fields.fade && (
            <div className="row">
              {text("fadeStart", "Fade starts at, s", "fadeStart")}
              {text("fadeLength", "Fade lasts, s", "fadeLength")}
              {text("fadeLoss", "Loss during fade, %", "fadeLoss")}
            </div>
          )}
          <div className="row">
            {text("gapTimeout", "Wait for a missing packet, ms", "gapTimeout", "must exceed the delay gap between lines")}
            {text("seed", "Random seed", "seed", "same seed, same result")}
          </div>
        </div>
      </details>

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
