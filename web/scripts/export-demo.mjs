// Records a few scenarios from a running service into public/demo/, for the no-backend demo build.
//
//   FEEDLAB_API=http://localhost:8080 npm run export-demo
//
// The service must be running with a real input file available. Only the aggregated results and the
// per-second timeline are stored, never any market data messages.
import { mkdir, rm, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const api = process.env.FEEDLAB_API ?? "http://localhost:8080";
const out = join(dirname(fileURLToPath(import.meta.url)), "..", "public", "demo");

const WINDOW = { start: "09:30:00", end: "09:40:00" };
const SCENARIOS = [
  { lineA: { fades: [{ startSeconds: 240, lengthSeconds: 120, loss: 0.3 }] } },
  { lineA: { loss: 0.00001, bandwidthMbps: 0, fades: [{ startSeconds: 0, lengthSeconds: 700, loss: 0.02 }] } },
  { lineA: { bandwidthMbps: 50 } },
];

async function json(path, init) {
  const response = await fetch(`${api}${path}`, init);
  if (!response.ok) throw new Error(`${path}: ${response.status} ${await response.text()}`);
  return response.json();
}

async function record(inputFile, scenario) {
  const created = await json("/api/runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ inputFile, ...WINDOW, ...scenario }),
  });
  for (let waited = 0; ; waited += 1) {
    const run = await json(`/api/runs/${created.id}`);
    if (run.status === "COMPLETED") {
      return { run, timeline: await json(`/api/runs/${created.id}/timeline`) };
    }
    if (run.status === "FAILED") throw new Error(`run ${created.id} failed: ${run.error}`);
    if (waited > 300) throw new Error(`run ${created.id} did not finish in 5 minutes`);
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
}

const inputs = await json("/api/inputs");
const inputFile = inputs.find((name) => name.includes("NASDAQ_ITCH50")) ?? inputs[0];
if (!inputFile) throw new Error("the service has no input files");
console.log(`recording ${SCENARIOS.length} runs from ${inputFile}`);

await rm(out, { recursive: true, force: true });
await mkdir(join(out, "runs"), { recursive: true });

const summaries = [];
for (const scenario of SCENARIOS) {
  const { run, timeline } = await record(inputFile, scenario);
  await writeFile(join(out, "runs", `${run.id}.json`), JSON.stringify(run));
  await writeFile(join(out, "runs", `${run.id}.timeline.json`), JSON.stringify(timeline));
  summaries.push({
    id: run.id,
    status: run.status,
    createdAt: run.createdAt,
    finishedAt: run.finishedAt,
    inputFile: run.config.inputFile,
    start: run.config.start,
    end: run.config.end,
    messages: run.results.messages,
    messagesLost: run.results.messagesLost,
  });
  console.log(`  ${run.id}: ${run.results.messages} messages, ${run.results.messagesLost} lost`);
}
await writeFile(join(out, "runs.json"), JSON.stringify(summaries));
console.log(`wrote ${out}`);
