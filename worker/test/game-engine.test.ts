import { describe, expect, it } from "vitest";
import { GameEngine, initialState, TURN_MILLIS } from "../src/game-engine";

function game() {
  let id = 0;
  return new GameEngine(initialState(), [], () => `turn-${++id}`);
}

describe("global game rules", () => {
  it("starts the first player at one with seven seconds", () => {
    const engine = game();
    engine.join("a", 100);
    expect(engine.state).toMatchObject({ phase: "active", number: 1, activeSessionId: "a", deadline: 100 + TURN_MILLIS });
  });

  it("cycles solo play and enforces boom rules", () => {
    const engine = game();
    engine.join("a", 0);
    for (let number = 1; number <= 8; number += 1) {
      const answer = number % 7 === 0 || String(number).includes("7")
        ? { type: "boom" as const }
        : { type: "number" as const, value: number };
      engine.answer("a", engine.state.turnId!, answer, number * 100);
      expect(engine.state.activeSessionId).toBe("a");
      expect(engine.state.number).toBe(number + 1);
    }
  });

  it("gives the newest newcomer the next turn then resumes join order", () => {
    const engine = game();
    engine.join("a", 0);
    engine.join("b", 1);
    engine.join("c", 2);
    engine.answer("a", engine.state.turnId!, { type: "number", value: 1 }, 3);
    expect(engine.state.activeSessionId).toBe("c");
    engine.answer("c", engine.state.turnId!, { type: "number", value: 2 }, 4);
    expect(engine.state.activeSessionId).toBe("a");
    engine.answer("a", engine.state.turnId!, { type: "number", value: 3 }, 5);
    expect(engine.state.activeSessionId).toBe("b");
  });

  it("ends on a wrong answer and requires each player to opt into the next round", () => {
    const engine = game();
    engine.join("a", 0);
    engine.join("b", 1);
    engine.answer("a", engine.state.turnId!, { type: "number", value: 1 }, 2);
    engine.answer("b", engine.state.turnId!, { type: "boom" }, 3);
    expect(engine.state).toMatchObject({
      phase: "gameOver", number: 2, gameOverReason: "wrongAnswer", failedSessionId: "b",
    });
    expect(engine.restart("a", 4).changed).toBe(true);
    expect(engine.state.number).toBe(1);
    expect(engine.state.activeSessionId).toBe("a");
    expect(engine.players.map((player) => player.sessionId)).toEqual(["a"]);
    expect(engine.restart("b", 5).changed).toBe(true);
    expect(engine.players.map((player) => player.sessionId)).toEqual(["a", "b"]);
    expect(engine.state.priorityNextSessionId).toBe("b");
    expect(engine.restart("a", 6).changed).toBe(false);
  });

  it("rejects spectators, stale turns, duplicates, and late answers", () => {
    const engine = game();
    engine.join("a", 0);
    engine.join("b", 1);
    const turn = engine.state.turnId!;
    expect(engine.answer("b", turn, { type: "number", value: 1 }, 2).changed).toBe(false);
    expect(engine.answer("a", "stale", { type: "number", value: 1 }, 2).changed).toBe(false);
    expect(engine.answer("a", turn, { type: "number", value: 1 }, TURN_MILLIS + 1).changed).toBe(false);
    expect(engine.answer("a", turn, { type: "number", value: 1 }, 2).changed).toBe(true);
    expect(engine.answer("a", turn, { type: "number", value: 1 }, 3).changed).toBe(false);
  });

  it("verifies timeout identity and deadline before ending a turn", () => {
    const engine = game();
    engine.join("a", 10);
    const turn = engine.state.turnId!;
    const deadline = engine.state.deadline!;
    expect(engine.timeout("other", deadline, deadline).changed).toBe(false);
    expect(engine.timeout(turn, deadline + 1, deadline + 1).changed).toBe(false);
    expect(engine.timeout(turn, deadline, deadline - 1).changed).toBe(false);
    expect(engine.timeout(turn, deadline, deadline).changed).toBe(true);
    expect(engine.state.gameOverReason).toBe("timeout");
    expect(engine.state.failedSessionId).toBe("a");
  });

  it("hands an active player's unchanged number to the next player", () => {
    const engine = game();
    engine.join("a", 0);
    engine.join("b", 1);
    engine.answer("a", engine.state.turnId!, { type: "number", value: 1 }, 2);
    expect(engine.state.activeSessionId).toBe("b");
    engine.leave("b", 10);
    expect(engine.state).toMatchObject({ phase: "active", number: 2, activeSessionId: "a", deadline: 10 + TURN_MILLIS });
  });

  it("does not duplicate sessions and ends when the last player exits", () => {
    const engine = game();
    expect(engine.join("a", 0).changed).toBe(true);
    expect(engine.join("a", 1).changed).toBe(false);
    expect(engine.players).toHaveLength(1);
    engine.leave("a", 2);
    expect(engine.state).toMatchObject({
      phase: "gameOver", number: 1, gameOverReason: "noPlayers", failedSessionId: null,
    });
    expect(engine.players).toHaveLength(0);
  });

  it("starts at one after orphaned players are removed on reconnect", () => {
    const engine = game();
    engine.join("old", 0);
    engine.answer("old", engine.state.turnId!, { type: "number", value: 1 }, 1);
    expect(engine.state.number).toBe(2);

    engine.leave("old", 2);
    engine.join("new", 3);

    expect(engine.state).toMatchObject({ number: 1, activeSessionId: "new", phase: "active" });
  });

  it("ranks player averages at game over and resets them on restart", () => {
    const engine = game();
    engine.join("a", 0);
    engine.join("b", 1);
    engine.answer("a", engine.state.turnId!, { type: "number", value: 1 }, 1_000, 1_000);
    engine.answer("b", engine.state.turnId!, { type: "number", value: 2 }, 3_000, 2_000);
    engine.answer("a", engine.state.turnId!, { type: "number", value: 3 }, 4_000, 3_000);
    engine.answer("b", engine.state.turnId!, { type: "boom" }, 5_000, 1_000);

    expect(engine.responseRankings()).toEqual([
      { sessionId: "b", rank: 1, averageMillis: 1_500 },
      { sessionId: "a", rank: 2, averageMillis: 2_000 },
    ]);

    engine.restart("a", 6_000);
    expect(engine.responseRankings()).toEqual([]);
  });

  it("counts a timeout as a seven-second response", () => {
    const engine = game();
    engine.join("a", 0);
    engine.timeout(engine.state.turnId!, engine.state.deadline!, engine.state.deadline!);
    expect(engine.responseRankings()).toEqual([
      { sessionId: "a", rank: 1, averageMillis: TURN_MILLIS },
    ]);
  });
});
