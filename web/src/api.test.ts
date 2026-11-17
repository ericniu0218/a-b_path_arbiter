import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApi } from "./api";

afterEach(() => vi.unstubAllGlobals());

function stubFetch(body: unknown, init: { ok?: boolean; status?: number } = {}) {
  const fetchMock = vi.fn(async (_path: string, _init?: RequestInit) => ({
    ok: init.ok ?? true,
    status: init.status ?? 200,
    statusText: "",
    json: async () => body,
  }));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("live api", () => {
  it("calls the service under /api", async () => {
    const fetchMock = stubFetch([]);
    await createApi(false).runs();
    await createApi(false).timeline("a b");
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(["/api/runs", "/api/runs/a%20b/timeline"]);
  });

  it("posts a new run as JSON", async () => {
    const fetchMock = stubFetch({ id: "x" });
    await createApi(false).createRun({ inputFile: "day.itch" });
    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/runs");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({ inputFile: "day.itch" });
  });

  it("turns a problem-detail response into an ApiError carrying its text", async () => {
    stubFetch({ detail: "no such input file: x" }, { ok: false, status: 400 });
    await expect(createApi(false).runs()).rejects.toMatchObject({ message: "no such input file: x", status: 400 });
  });
});

describe("demo api", () => {
  it("reads static files under the base path", async () => {
    const fetchMock = stubFetch([]);
    const demo = createApi(true, "/lab");
    await demo.runs();
    await demo.run("abc");
    await demo.timeline("abc");
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      "/lab/demo/runs.json",
      "/lab/demo/runs/abc.json",
      "/lab/demo/runs/abc.timeline.json",
    ]);
  });

  it("has no input files and refuses to start runs", async () => {
    stubFetch([]);
    const demo = createApi(true);
    await expect(demo.inputs()).resolves.toEqual([]);
    await expect(demo.createRun({ inputFile: "x" })).rejects.toBeInstanceOf(ApiError);
  });
});
