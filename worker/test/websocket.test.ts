import { exports } from "cloudflare:workers";
import { describe, expect, it } from "vitest";

const A = "00000000-0000-4000-8000-000000000001";
const B = "00000000-0000-4000-8000-000000000002";

async function connect(sessionId: string, nickname = "😀"): Promise<WebSocket> {
  const response = await exports.default.fetch(
    `https://example.test/game?sessionId=${sessionId}&nickname=${encodeURIComponent(nickname)}`,
    {
      headers: { Upgrade: "websocket" },
    },
  );
  expect(response.status).toBe(101);
  const socket = response.webSocket!;
  socket.accept();
  return socket;
}

function nextMessage(socket: WebSocket): Promise<Record<string, unknown>> {
  return new Promise((resolve) => socket.addEventListener("message", (event) => resolve(JSON.parse(String(event.data))), { once: true }));
}

async function nextUpdate(socket: WebSocket): Promise<{
  snapshot: Record<string, unknown>;
  roster: Record<string, unknown>;
}> {
  const snapshot = await nextMessage(socket);
  const roster = await nextMessage(socket);
  expect(snapshot.type).toBe("snapshot");
  expect(roster).toMatchObject({ type: "roster", revision: snapshot.revision });
  return { snapshot, roster };
}

describe("WebSocket integration", () => {
  it("broadcasts identical authoritative turns and active-player exclusivity", async () => {
    const a = await connect(A, "🦊");
    const first = await nextUpdate(a);
    expect(first.snapshot).toMatchObject({ phase: "active", number: 1, activeSessionId: A, connectedPlayers: 1 });
    expect(first.roster.players).toEqual([{ sessionId: A, nickname: "🦊" }]);

    const pong = nextMessage(a);
    a.send(JSON.stringify({ type: "ping", id: "latency-1" }));
    await expect(pong).resolves.toEqual({ type: "pong", id: "latency-1" });

    const aUpdate = nextUpdate(a);
    const b = await connect(B, "🐼");
    const [updateA, updateB] = await Promise.all([aUpdate, nextUpdate(b)]);
    const seenByA = updateA.snapshot;
    const seenByB = updateB.snapshot;
    expect(seenByA).toMatchObject({ revision: seenByB.revision, connectedPlayers: 2, activeSessionId: A });
    expect(updateA.roster.players).toEqual([
      { sessionId: A, nickname: "🦊" },
      { sessionId: B, nickname: "🐼" },
    ]);

    const turnId = seenByA.turnId as string;
    const nextA = nextUpdate(a);
    const nextB = nextUpdate(b);
    a.send(JSON.stringify({ type: "answer", turnId, responseTimeMillis: 1_200, answer: { type: "number", value: 1 } }));
    const [advancedUpdateA, advancedUpdateB] = await Promise.all([nextA, nextB]);
    const advancedA = advancedUpdateA.snapshot;
    const advancedB = advancedUpdateB.snapshot;
    expect(advancedA).toMatchObject({ revision: advancedB.revision, number: 2, activeSessionId: B });

    const failedA = nextUpdate(a);
    const failedB = nextUpdate(b);
    b.send(JSON.stringify({
      type: "answer",
      turnId: advancedB.turnId,
      responseTimeMillis: 2_400,
      answer: { type: "boom" },
    }));
    const [failureUpdateA, failureUpdateB] = await Promise.all([failedA, failedB]);
    const seenFailureByA = failureUpdateA.snapshot;
    const seenFailureByB = failureUpdateB.snapshot;
    expect(seenFailureByA).toMatchObject({
      revision: seenFailureByB.revision,
      phase: "gameOver",
      gameOverReason: "wrongAnswer",
      failedSessionId: B,
      responseRankings: [
        { sessionId: A, nickname: "🦊", rank: 1, averageMillis: 1_200 },
        { sessionId: B, nickname: "🐼", rank: 2, averageMillis: 2_400 },
      ],
    });

    const restartedA = nextUpdate(a);
    a.send(JSON.stringify({ type: "restart" }));
    const afterRestartA = await restartedA;
    expect(afterRestartA.snapshot).toMatchObject({
      phase: "active",
      number: 1,
      activeSessionId: A,
      connectedPlayers: 1,
    });
    expect(afterRestartA.roster.players).toEqual([{ sessionId: A, nickname: "🦊" }]);

    const joinedA = nextUpdate(a);
    const joinedB = nextUpdate(b);
    b.send(JSON.stringify({ type: "restart" }));
    const [afterBUpdateA, afterBUpdateB] = await Promise.all([joinedA, joinedB]);
    const afterBOptedInA = afterBUpdateA.snapshot;
    const afterBOptedInB = afterBUpdateB.snapshot;
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

  it("updates roster nicknames and disconnects while preserving duplicate emoji entries", async () => {
    const a = await connect(A, "🚀");
    await nextUpdate(a);
    const aJoinUpdate = nextUpdate(a);
    const b = await connect(B, "🚀");
    const [, bInitial] = await Promise.all([aJoinUpdate, nextUpdate(b)]);
    expect(bInitial.roster.players).toEqual([
      { sessionId: A, nickname: "🚀" },
      { sessionId: B, nickname: "🚀" },
    ]);

    const bNicknameUpdate = nextUpdate(b);
    const replacement = await connect(A, "🦄");
    const [seenByB, seenByReplacement] = await Promise.all([bNicknameUpdate, nextUpdate(replacement)]);
    expect(seenByB.roster.players).toEqual([
      { sessionId: A, nickname: "🦄" },
      { sessionId: B, nickname: "🚀" },
    ]);
    expect(seenByReplacement.roster.players).toEqual(seenByB.roster.players);

    const afterDisconnect = nextUpdate(b);
    replacement.close();
    await expect(afterDisconnect).resolves.toMatchObject({
      roster: { players: [{ sessionId: B, nickname: "🚀" }] },
    });
    b.close();
  });

  it("rejects free-text nicknames", async () => {
    const response = await exports.default.fetch(
      `https://example.test/game?sessionId=${A}&nickname=Shahar`,
      { headers: { Upgrade: "websocket" } },
    );
    expect(response.status).toBe(400);
  });
});
