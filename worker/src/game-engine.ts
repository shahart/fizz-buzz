export const TURN_MILLIS = 7_000;

export type Phase = "active" | "gameOver";
export type GameOverReason = "wrongAnswer" | "timeout" | "noPlayers";

export interface Player {
  sessionId: string;
  joinOrder: number;
  responseTimeTotalMillis: number;
  responseCount: number;
}

export interface ResponseRanking {
  sessionId: string;
  rank: number;
  averageMillis: number;
}

export interface GameState {
  revision: number;
  phase: Phase;
  number: number;
  turnId: string | null;
  activeSessionId: string | null;
  deadline: number | null;
  gameOverReason: GameOverReason | null;
  failedSessionId: string | null;
  priorityNextSessionId: string | null;
  lastJoinOrder: number;
}

export type Answer =
  | { type: "boom" }
  | { type: "number"; value: number }
  | { type: "invalid" };

export interface Mutation {
  changed: boolean;
  alarm: number | null;
}

export function initialState(): GameState {
  return {
    revision: 0,
    phase: "gameOver",
    number: 1,
    turnId: null,
    activeSessionId: null,
    deadline: null,
    gameOverReason: "noPlayers",
    failedSessionId: null,
    priorityNextSessionId: null,
    lastJoinOrder: 0,
  };
}

export function isBoomNumber(value: number): boolean {
  return value % 7 === 0 || String(value).includes("7");
}

export class GameEngine {
  constructor(
    readonly state: GameState,
    readonly players: Player[],
    private readonly newTurnId: () => string = () => crypto.randomUUID(),
  ) {}

  join(sessionId: string, now: number): Mutation {
    if (this.players.some((player) => player.sessionId === sessionId)) {
      return { changed: false, alarm: this.state.deadline };
    }

    this.state.lastJoinOrder += 1;
    this.players.push({
      sessionId,
      joinOrder: this.state.lastJoinOrder,
      responseTimeTotalMillis: 0,
      responseCount: 0,
    });
    if (this.players.length === 1) {
      this.startTurn(sessionId, 1, now);
    } else {
      if (this.state.phase === "active") this.state.priorityNextSessionId = sessionId;
      this.state.revision += 1;
    }
    return { changed: true, alarm: this.state.deadline };
  }

  answer(sessionId: string, turnId: string, answer: Answer, now: number, responseTimeMillis?: number): Mutation {
    if (
      this.state.phase !== "active" ||
      this.state.activeSessionId !== sessionId ||
      this.state.turnId !== turnId ||
      this.state.deadline === null ||
      now > this.state.deadline
    ) {
      return { changed: false, alarm: this.state.deadline };
    }

    this.recordResponse(sessionId, responseTimeMillis ?? now - (this.state.deadline - TURN_MILLIS));

    const correct = answer.type === "boom"
      ? isBoomNumber(this.state.number)
      : answer.type === "number"
        ? !isBoomNumber(this.state.number) && answer.value === this.state.number
        : false;

    if (!correct) {
      this.endGame("wrongAnswer", sessionId);
      return { changed: true, alarm: null };
    }

    const next = this.nextPlayer(sessionId);
    this.startTurn(next, this.state.number + 1, now);
    return { changed: true, alarm: this.state.deadline };
  }

  restart(sessionId: string, now: number): Mutation {
    if (this.state.phase === "active") {
      return this.hasPlayer(sessionId)
        ? { changed: false, alarm: this.state.deadline }
        : this.join(sessionId, now);
    }
    if (!this.hasPlayer(sessionId) || this.players.length === 0) {
      return { changed: false, alarm: this.state.deadline };
    }
    const restartingPlayer = this.players.find((player) => player.sessionId === sessionId)!;
    restartingPlayer.responseTimeTotalMillis = 0;
    restartingPlayer.responseCount = 0;
    this.players.splice(0, this.players.length, restartingPlayer);
    this.state.priorityNextSessionId = null;
    this.startTurn(sessionId, 1, now);
    return { changed: true, alarm: this.state.deadline };
  }

  leave(sessionId: string, now: number): Mutation {
    const index = this.players.findIndex((player) => player.sessionId === sessionId);
    if (index < 0) return { changed: false, alarm: this.state.deadline };

    const wasActive = this.state.activeSessionId === sessionId;
    this.players.splice(index, 1);
    if (this.state.priorityNextSessionId === sessionId) this.state.priorityNextSessionId = null;

    if (this.players.length === 0) {
      this.endGame("noPlayers");
    } else if (wasActive && this.state.phase === "active") {
      this.startTurn(this.nextPlayerAfterRemoved(index), this.state.number, now);
    } else {
      this.state.revision += 1;
    }
    return { changed: true, alarm: this.state.deadline };
  }

  timeout(turnId: string, deadline: number, now: number): Mutation {
    if (
      this.state.phase !== "active" ||
      this.state.turnId !== turnId ||
      this.state.deadline !== deadline
    ) {
      return { changed: false, alarm: this.state.deadline };
    }
    if (now < deadline) return { changed: false, alarm: deadline };
    if (this.state.activeSessionId !== null) this.recordResponse(this.state.activeSessionId, TURN_MILLIS);
    this.endGame("timeout", this.state.activeSessionId);
    return { changed: true, alarm: null };
  }

  private startTurn(sessionId: string, number: number, now: number): void {
    this.state.phase = "active";
    this.state.number = number;
    this.state.turnId = this.newTurnId();
    this.state.activeSessionId = sessionId;
    this.state.deadline = now + TURN_MILLIS;
    this.state.gameOverReason = null;
    this.state.failedSessionId = null;
    this.state.revision += 1;
  }

  private endGame(reason: GameOverReason, failedSessionId: string | null = null): void {
    this.state.phase = "gameOver";
    if (reason === "noPlayers") this.state.number = 1;
    this.state.turnId = null;
    this.state.activeSessionId = null;
    this.state.deadline = null;
    this.state.gameOverReason = reason;
    this.state.failedSessionId = failedSessionId;
    this.state.priorityNextSessionId = null;
    this.state.revision += 1;
  }

  private nextPlayer(currentSessionId: string): string {
    const priority = this.state.priorityNextSessionId;
    if (priority !== null && priority !== currentSessionId && this.hasPlayer(priority)) {
      this.state.priorityNextSessionId = null;
      return priority;
    }
    const ordered = this.orderedPlayers();
    const current = ordered.findIndex((player) => player.sessionId === currentSessionId);
    return ordered[(current + 1) % ordered.length].sessionId;
  }

  private nextPlayerAfterRemoved(removedIndex: number): string {
    const priority = this.state.priorityNextSessionId;
    if (priority !== null && this.hasPlayer(priority)) {
      this.state.priorityNextSessionId = null;
      return priority;
    }
    const ordered = this.orderedPlayers();
    return ordered[removedIndex % ordered.length].sessionId;
  }

  private orderedPlayers(): Player[] {
    return [...this.players].sort((left, right) => left.joinOrder - right.joinOrder);
  }

  private hasPlayer(sessionId: string): boolean {
    return this.players.some((player) => player.sessionId === sessionId);
  }

  responseRankings(): ResponseRanking[] {
    const ranked = this.players
      .filter((player) => player.responseCount > 0)
      .sort((left, right) => {
        const comparison = left.responseTimeTotalMillis * right.responseCount -
          right.responseTimeTotalMillis * left.responseCount;
        return comparison !== 0 ? comparison : left.joinOrder - right.joinOrder;
      });
    let rank = 0;
    return ranked.map((player, index) => {
      const previous = ranked[index - 1];
      if (previous === undefined ||
        previous.responseTimeTotalMillis * player.responseCount !==
          player.responseTimeTotalMillis * previous.responseCount) {
        rank = index + 1;
      }
      return {
        sessionId: player.sessionId,
        rank,
        averageMillis: Math.round(player.responseTimeTotalMillis / player.responseCount),
      };
    });
  }

  private recordResponse(sessionId: string, responseTimeMillis: number): void {
    const player = this.players.find((candidate) => candidate.sessionId === sessionId);
    if (player === undefined) return;
    player.responseTimeTotalMillis += Math.round(Math.max(0, Math.min(TURN_MILLIS, responseTimeMillis)));
    player.responseCount += 1;
  }
}
