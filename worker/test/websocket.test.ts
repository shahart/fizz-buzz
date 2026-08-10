import { exports } from "cloudflare:workers";
import { describe, expect, it } from "vitest";

const A = "00000000-0000-4000-8000-000000000001";
const B = "00000000-0000-4000-8000-000000000002";

async function connect(sessionId: string): Promise<WebSocket> {
  const response = await exports.default.fetch(`https://example.test/game?sessionId=${sessionId}`, {
    headers: { Upgrade: "websocket" },
  });
  expect(response.status).toBe(101);
  const socket = response.webSocket!;
  socket.accept();
  return socket;
}

function nextMessage(socket: WebSocket): Promise<Record<string, unknown>> {
  return new Promise((resolve) => socket.addEventListener("message", (event) => resolve(JSON.parse(String(event.data))), { once: true }));
}

describe("WebSocket integration", () => {
  it("broadcasts identical authoritative turns and active-player exclusivity", async () => {
    const a = await connect(A);
    const first = await nextMessage(a);
    expect(first).toMatchObject({ phase: "active", number: 1, activeSessionId: A, connectedPlayers: 1 });

    const pong = nextMessage(a);
    a.send(JSON.stringify({ type: "ping", id: "latency-1" }));
    await expect(pong).resolves.toEqual({ type: "pong", id: "latency-1" });

    const aUpdate = nextMessage(a);
    const b = await connect(B);
    const [seenByA, seenByB] = await Promise.all([aUpdate, nextMessage(b)]);
    expect(seenByA).toMatchObject({ revision: seenByB.revision, connectedPlayers: 2, activeSessionId: A });

    const turnId = seenByA.turnId as string;
    const nextA = nextMessage(a);
    const nextB = nextMessage(b);
    a.send(JSON.stringify({ type: "answer", turnId, responseTimeMillis: 1_200, answer: { type: "number", value: 1 } }));
    const [advancedA, advancedB] = await Promise.all([nextA, nextB]);
    expect(advancedA).toMatchObject({ revision: advancedB.revision, number: 2, activeSessionId: B });

    const failedA = nextMessage(a);
    const failedB = nextMessage(b);
    b.send(JSON.stringify({
      type: "answer",
      turnId: advancedB.turnId,
      responseTimeMillis: 2_400,
      answer: { type: "boom" },
    }));
    const [seenFailureByA, seenFailureByB] = await Promise.all([failedA, failedB]);
    expect(seenFailureByA).toMatchObject({
      revision: seenFailureByB.revision,
      phase: "gameOver",
      gameOverReason: "wrongAnswer",
      failedSessionId: B,
      responseRankings: [
        { sessionId: A, rank: 1, averageMillis: 1_200 },
        { sessionId: B, rank: 2, averageMillis: 2_400 },
      ],
    });

    const restartedA = nextMessage(a);
    a.send(JSON.stringify({ type: "restart" }));
    await expect(restartedA).resolves.toMatchObject({
      phase: "active",
      number: 1,
      activeSessionId: A,
      connectedPlayers: 1,
    });

    const joinedA = nextMessage(a);
    const joinedB = nextMessage(b);
    b.send(JSON.stringify({ type: "restart" }));
    const [afterBOptedInA, afterBOptedInB] = await Promise.all([joinedA, joinedB]);
    expect(afterBOptedInA).toMatchObject({
      revision: afterBOptedInB.revision,
      phase: "active",
      number: 1,
      activeSessionId: A,
      connectedPlayers: 2,
    });
    a.close();
    b.close();
  });
});
