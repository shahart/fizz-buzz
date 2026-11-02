import worker from "../src/index";
import { describe, expect, it } from "vitest";

const SESSION_ID = "00000000-0000-4000-8000-000000000001";
const BASE_URL = "https://example.test";
const forwardedGameRequests: Request[] = [];
const env = {
  ASSETS: {
    fetch: async (request: Request) => new Response(`asset:${new URL(request.url).pathname}`),
  },
  GLOBAL_GAME: {
    getByName: (name: string) => {
      if (name !== "global") throw new Error(`Unexpected game object: ${name}`);
      return {
        fetch: async (request: Request) => {
          forwardedGameRequests.push(request);
          return new Response("websocket forwarded");
        },
      };
    },
  },
} as unknown as Env;

function gameRequest(options: {
  method?: string;
  upgrade?: boolean;
  sessionId?: string | null;
  nickname?: string | null;
} = {}): Request {
  const url = new URL("/game", BASE_URL);
  if (options.sessionId !== null) url.searchParams.set("sessionId", options.sessionId ?? SESSION_ID);
  if (options.nickname !== null) url.searchParams.set("nickname", options.nickname ?? "😀");
  return new Request(url, {
    method: options.method ?? "GET",
    headers: options.upgrade ? { Upgrade: "websocket" } : undefined,
  });
}

describe("web HTTP routes", () => {
  it("reports healthy from the browser-facing health endpoint", async () => {
    const response = await worker.fetch(new Request(`${BASE_URL}/health`), env);

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("application/json");
    await expect(response.json()).resolves.toEqual({ status: "ok" });
  });

  it.each(["/", "/composeResources/values/strings.json", "/nested/client/route"]) (
    "forwards static and client routes (%s) to the web asset binding",
    async (path) => {
      const response = await worker.fetch(new Request(`${BASE_URL}${path}`), env);

      expect(response.status).toBe(200);
      await expect(response.text()).resolves.toBe(`asset:${path}`);
    },
  );

  it("requires a websocket upgrade for the game endpoint", async () => {
    const response = await worker.fetch(gameRequest(), env);

    expect(response.status).toBe(426);
    await expect(response.text()).resolves.toBe("Expected a WebSocket upgrade");
  });

  it("rejects non-GET methods even when they request an upgrade", async () => {
    const response = await worker.fetch(gameRequest({ method: "POST", upgrade: true }), env);

    expect(response.status).toBe(426);
  });

  it.each([
    ["missing", null],
    ["malformed", "not-a-uuid"],
  ])("rejects a %s session ID before opening a socket", async (_description, sessionId) => {
    const response = await worker.fetch(gameRequest({ upgrade: true, sessionId }), env);

    expect(response.status).toBe(400);
    await expect(response.text()).resolves.toBe("A valid sessionId is required");
  });

  it.each([
    ["free-text", "Shahar"],
    ["multiple emoji", "😀😀"],
  ])("rejects a %s nickname before opening a socket", async (_description, nickname) => {
    const response = await worker.fetch(gameRequest({ upgrade: true, nickname }), env);

    expect(response.status).toBe(400);
    await expect(response.text()).resolves.toBe("A valid emoji nickname is required");
  });

  it("uses the default emoji nickname when none is provided", async () => {
    forwardedGameRequests.length = 0;
    const response = await worker.fetch(gameRequest({ upgrade: true, nickname: null }), env);

    expect(response.status).toBe(200);
    expect(forwardedGameRequests).toHaveLength(1);
    expect(new URL(forwardedGameRequests[0].url).searchParams.get("sessionId")).toBe(SESSION_ID);
    expect(new URL(forwardedGameRequests[0].url).searchParams.has("nickname")).toBe(false);
  });
});
