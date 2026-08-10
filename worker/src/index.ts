import { DurableObject } from "cloudflare:workers";
import {
  Answer,
  DEFAULT_EMOJI_NICKNAME,
  GameEngine,
  GameState,
  initialState,
  isEmojiNickname,
  Player,
  ResponseRanking,
  TURN_MILLIS,
} from "./game-engine";

interface SocketAttachment {
  sessionId: string;
  connectionId: string;
  nickname: string;
}

interface ClientAnswerMessage {
  type: "answer";
  turnId: string;
  answer: Answer;
  responseTimeMillis: number;
}

type ClientMessage = ClientAnswerMessage | { type: "restart" } | { type: "leave" } | { type: "ping"; id: string };

interface Snapshot {
  type: "snapshot";
  revision: number;
  phase: "active" | "gameOver";
  number: number;
  turnId: string | null;
  activeSessionId: string | null;
  connectedPlayers: number;
  serverTime: number;
  deadline: number | null;
  gameOverReason?: "wrongAnswer" | "timeout" | "noPlayers";
  failedSessionId?: string;
  responseRankings?: ResponseRanking[];
}

interface RosterMessage {
  type: "roster";
  revision: number;
  players: Array<{ sessionId: string; nickname: string }>;
}

export class GlobalGame extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    ctx.blockConcurrencyWhile(async () => {
      this.ctx.storage.sql.exec(`
        CREATE TABLE IF NOT EXISTS game_state (
          singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
          revision INTEGER NOT NULL,
          phase TEXT NOT NULL,
          number INTEGER NOT NULL,
          turn_id TEXT,
          active_session_id TEXT,
          deadline INTEGER,
          game_over_reason TEXT,
          failed_session_id TEXT,
          priority_next_session_id TEXT,
          last_join_order INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS roster (
          session_id TEXT PRIMARY KEY,
          nickname TEXT NOT NULL DEFAULT '😀',
          join_order INTEGER NOT NULL UNIQUE,
          response_time_total_millis INTEGER NOT NULL DEFAULT 0,
          response_count INTEGER NOT NULL DEFAULT 0
        );
      `);
      const stateColumns = this.ctx.storage.sql.exec<{ name: string }>("PRAGMA table_info(game_state)").toArray();
      if (!stateColumns.some((column) => column.name === "failed_session_id")) {
        this.ctx.storage.sql.exec("ALTER TABLE game_state ADD COLUMN failed_session_id TEXT");
      }
      const rosterColumns = this.ctx.storage.sql.exec<{ name: string }>("PRAGMA table_info(roster)").toArray();
      if (!rosterColumns.some((column) => column.name === "response_time_total_millis")) {
        this.ctx.storage.sql.exec("ALTER TABLE roster ADD COLUMN response_time_total_millis INTEGER NOT NULL DEFAULT 0");
      }
      if (!rosterColumns.some((column) => column.name === "response_count")) {
        this.ctx.storage.sql.exec("ALTER TABLE roster ADD COLUMN response_count INTEGER NOT NULL DEFAULT 0");
      }
      if (!rosterColumns.some((column) => column.name === "nickname")) {
        this.ctx.storage.sql.exec("ALTER TABLE roster ADD COLUMN nickname TEXT NOT NULL DEFAULT '😀'");
      }
      const existing = this.ctx.storage.sql.exec("SELECT singleton FROM game_state WHERE singleton = 1").toArray();
      if (existing.length === 0) this.persist(initialState(), []);
    });
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);
    const sessionId = url.searchParams.get("sessionId");
    const nickname = parseNickname(url.searchParams.get("nickname"));
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Expected a WebSocket upgrade", { status: 426 });
    }
    if (sessionId === null || !isSessionId(sessionId)) {
      return new Response("A valid sessionId is required", { status: 400 });
    }
    if (nickname === null) return new Response("A valid emoji nickname is required", { status: 400 });

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    const connectionId = crypto.randomUUID();
    server.serializeAttachment({ sessionId, connectionId, nickname } satisfies SocketAttachment);
    this.ctx.acceptWebSocket(server);

    for (const socket of this.ctx.getWebSockets()) {
      if (socket === server) continue;
      const attachment = attachmentOf(socket);
      if (attachment?.sessionId === sessionId) socket.close(4001, "Replaced by reconnect");
    }

    const engine = this.load();
    const now = Date.now();
    const liveSessions = new Set(
      this.ctx.getWebSockets()
        .map(attachmentOf)
        .filter((attachment): attachment is SocketAttachment => attachment !== null)
        .map((attachment) => attachment.sessionId),
    );
    let changed = false;
    // WebSockets survive normal hibernation. Persisted players without a live
    // socket are therefore leftovers from a runtime/deployment restart and
    // must not keep an old counter alive for the next first player.
    for (const player of [...engine.players]) {
      if (!liveSessions.has(player.sessionId)) {
        changed = engine.leave(player.sessionId, now).changed || changed;
      }
    }
    const mutation = engine.join(sessionId, now, nickname);
    changed = mutation.changed || changed;
    if (changed) {
      this.persist(engine.state, engine.players);
      await this.schedule(mutation.alarm);
      this.broadcast(engine);
    } else {
      this.sendSnapshot(server, engine);
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    if (typeof message !== "string") return;
    const attachment = attachmentOf(socket);
    if (attachment === null || !this.isCurrentConnection(attachment)) return;
    const parsed = parseClientMessage(message);
    if (parsed === null) return;
    if (parsed.type === "ping") {
      socket.send(JSON.stringify({ type: "pong", id: parsed.id }));
      return;
    }

    const engine = this.load();
    const now = Date.now();
    const mutation = parsed.type === "answer"
      ? engine.answer(attachment.sessionId, parsed.turnId, parsed.answer, now, parsed.responseTimeMillis)
      : parsed.type === "restart"
        ? engine.restart(attachment.sessionId, now, attachment.nickname)
        : engine.leave(attachment.sessionId, now);

    if (mutation.changed) {
      this.persist(engine.state, engine.players);
      await this.schedule(mutation.alarm);
      this.broadcast(engine);
    }
    if (parsed.type === "leave") socket.close(1000, "Left game");
  }

  async webSocketClose(socket: WebSocket): Promise<void> {
    const attachment = attachmentOf(socket);
    if (attachment === null || this.hasAnotherConnection(attachment)) return;
    await this.removeSession(attachment.sessionId);
  }

  async webSocketError(socket: WebSocket): Promise<void> {
    const attachment = attachmentOf(socket);
    if (attachment === null || this.hasAnotherConnection(attachment)) return;
    await this.removeSession(attachment.sessionId);
  }

  async alarm(): Promise<void> {
    const engine = this.load();
    if (engine.state.turnId === null || engine.state.deadline === null) return;
    const mutation = engine.timeout(engine.state.turnId, engine.state.deadline, Date.now());
    if (!mutation.changed) {
      await this.schedule(mutation.alarm);
      return;
    }
    this.persist(engine.state, engine.players);
    await this.schedule(null);
    this.broadcast(engine);
  }

  private async removeSession(sessionId: string): Promise<void> {
    const engine = this.load();
    const mutation = engine.leave(sessionId, Date.now());
    if (!mutation.changed) return;
    this.persist(engine.state, engine.players);
    await this.schedule(mutation.alarm);
    this.broadcast(engine);
  }

  private load(): GameEngine {
    const row = this.ctx.storage.sql.exec<{
      revision: number; phase: "active" | "gameOver"; number: number; turn_id: string | null;
      active_session_id: string | null; deadline: number | null; game_over_reason: GameState["gameOverReason"];
      failed_session_id: string | null;
      priority_next_session_id: string | null; last_join_order: number;
    }>("SELECT revision, phase, number, turn_id, active_session_id, deadline, game_over_reason, failed_session_id, priority_next_session_id, last_join_order FROM game_state WHERE singleton = 1").one();
    const state: GameState = {
      revision: row.revision,
      phase: row.phase,
      number: row.number,
      turnId: row.turn_id,
      activeSessionId: row.active_session_id,
      deadline: row.deadline,
      gameOverReason: row.game_over_reason,
      failedSessionId: row.failed_session_id,
      priorityNextSessionId: row.priority_next_session_id,
      lastJoinOrder: row.last_join_order,
    };
    const players = this.ctx.storage.sql.exec<{
      session_id: string; nickname: string; join_order: number; response_time_total_millis: number; response_count: number;
    }>(
      "SELECT session_id, nickname, join_order, response_time_total_millis, response_count FROM roster ORDER BY join_order",
    ).toArray().map((player): Player => ({
      sessionId: player.session_id,
      nickname: isEmojiNickname(player.nickname) ? player.nickname : DEFAULT_EMOJI_NICKNAME,
      joinOrder: player.join_order,
      responseTimeTotalMillis: player.response_time_total_millis,
      responseCount: player.response_count,
    }));
    return new GameEngine(state, players);
  }

  private persist(state: GameState, players: Player[]): void {
    const sql = this.ctx.storage.sql;
    this.ctx.storage.transactionSync(() => {
      sql.exec(
        `INSERT INTO game_state(singleton, revision, phase, number, turn_id, active_session_id, deadline, game_over_reason, failed_session_id, priority_next_session_id, last_join_order)
         VALUES(1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON CONFLICT(singleton) DO UPDATE SET revision=excluded.revision, phase=excluded.phase, number=excluded.number,
           turn_id=excluded.turn_id, active_session_id=excluded.active_session_id, deadline=excluded.deadline,
           game_over_reason=excluded.game_over_reason, failed_session_id=excluded.failed_session_id,
           priority_next_session_id=excluded.priority_next_session_id,
           last_join_order=excluded.last_join_order`,
        state.revision, state.phase, state.number, state.turnId, state.activeSessionId, state.deadline,
        state.gameOverReason, state.failedSessionId, state.priorityNextSessionId, state.lastJoinOrder,
      );
      sql.exec("DELETE FROM roster");
      for (const player of players) {
        sql.exec(
          "INSERT INTO roster(session_id, nickname, join_order, response_time_total_millis, response_count) VALUES(?, ?, ?, ?, ?)",
          player.sessionId, player.nickname, player.joinOrder, player.responseTimeTotalMillis, player.responseCount,
        );
      }
    });
  }

  private async schedule(deadline: number | null): Promise<void> {
    if (deadline === null) await this.ctx.storage.deleteAlarm();
    else await this.ctx.storage.setAlarm(deadline);
  }

  private broadcast(engine: GameEngine): void {
    const snapshotMessage = JSON.stringify(this.snapshot(engine));
    const rosterMessage = JSON.stringify(this.roster(engine));
    const activeSessions = new Set(engine.players.map((player) => player.sessionId));
    for (const socket of this.ctx.getWebSockets()) {
      const attachment = attachmentOf(socket);
      if (engine.state.phase === "active" && (attachment === null || !activeSessions.has(attachment.sessionId))) {
        continue;
      }
      try {
        socket.send(snapshotMessage);
        socket.send(rosterMessage);
      } catch (error) {
        console.warn(JSON.stringify({ event: "snapshot_send_failed", error: String(error) }));
      }
    }
  }

  private sendSnapshot(socket: WebSocket, engine: GameEngine): void {
    socket.send(JSON.stringify(this.snapshot(engine)));
    socket.send(JSON.stringify(this.roster(engine)));
  }

  private snapshot(engine: GameEngine): Snapshot {
    const snapshot: Snapshot = {
      type: "snapshot",
      revision: engine.state.revision,
      phase: engine.state.phase,
      number: engine.state.number,
      turnId: engine.state.turnId,
      activeSessionId: engine.state.activeSessionId,
      connectedPlayers: engine.players.length,
      serverTime: Date.now(),
      deadline: engine.state.deadline,
    };
    if (engine.state.gameOverReason !== null) snapshot.gameOverReason = engine.state.gameOverReason;
    if (engine.state.failedSessionId !== null) snapshot.failedSessionId = engine.state.failedSessionId;
    if (engine.state.phase === "gameOver") snapshot.responseRankings = engine.responseRankings();
    return snapshot;
  }

  private roster(engine: GameEngine): RosterMessage {
    return {
      type: "roster",
      revision: engine.state.revision,
      players: engine.players.map(({ sessionId, nickname }) => ({ sessionId, nickname })),
    };
  }

  private isCurrentConnection(attachment: SocketAttachment): boolean {
    return !this.ctx.getWebSockets().some((candidate) => {
      const other = attachmentOf(candidate);
      return other?.sessionId === attachment.sessionId && other.connectionId !== attachment.connectionId;
    });
  }

  private hasAnotherConnection(attachment: SocketAttachment): boolean {
    return this.ctx.getWebSockets().some((candidate) => {
      const other = attachmentOf(candidate);
      return other?.sessionId === attachment.sessionId && other.connectionId !== attachment.connectionId;
    });
  }
}

function attachmentOf(socket: WebSocket): SocketAttachment | null {
  const value: unknown = socket.deserializeAttachment();
  if (typeof value !== "object" || value === null) return null;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.sessionId === "string" && typeof candidate.connectionId === "string"
    ? {
        sessionId: candidate.sessionId,
        connectionId: candidate.connectionId,
        nickname: typeof candidate.nickname === "string" && isEmojiNickname(candidate.nickname)
          ? candidate.nickname
          : DEFAULT_EMOJI_NICKNAME,
      }
    : null;
}

function isSessionId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function parseNickname(value: string | null): string | null {
  if (value === null) return DEFAULT_EMOJI_NICKNAME;
  return isEmojiNickname(value) ? value : null;
}

function parseClientMessage(raw: string): ClientMessage | null {
  let value: unknown;
  try { value = JSON.parse(raw); } catch { return null; }
  if (!isRecord(value) || typeof value.type !== "string") return null;
  if (value.type === "restart" || value.type === "leave") {
    return Object.keys(value).length === 1 ? { type: value.type } : null;
  }
  if (value.type === "ping") {
    return Object.keys(value).length === 2 && typeof value.id === "string" && value.id.length > 0 && value.id.length <= 80
      ? { type: "ping", id: value.id }
      : null;
  }
  if (value.type !== "answer" || Object.keys(value).length !== 4 || typeof value.turnId !== "string" ||
    !Number.isInteger(value.responseTimeMillis) || (value.responseTimeMillis as number) < 0 ||
    (value.responseTimeMillis as number) > TURN_MILLIS || !isRecord(value.answer)) {
    return null;
  }
  const answer = value.answer;
  if (answer.type === "boom" || answer.type === "invalid") {
    if (Object.keys(answer).length !== 1) return null;
    return { type: "answer", turnId: value.turnId, answer: { type: answer.type }, responseTimeMillis: value.responseTimeMillis as number };
  }
  if (answer.type === "number" && Object.keys(answer).length === 2 && Number.isSafeInteger(answer.value) && (answer.value as number) >= 1) {
    return {
      type: "answer", turnId: value.turnId, answer: { type: "number", value: answer.value as number },
      responseTimeMillis: value.responseTimeMillis as number,
    };
  }
  return null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/health") return Response.json({ status: "ok" });
    if (url.pathname === "/game") {
      if (request.method !== "GET" || request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
        return new Response("Expected a WebSocket upgrade", { status: 426 });
      }
      if (!isSessionId(url.searchParams.get("sessionId") ?? "")) {
        return new Response("A valid sessionId is required", { status: 400 });
      }
      if (parseNickname(url.searchParams.get("nickname")) === null) {
        return new Response("A valid emoji nickname is required", { status: 400 });
      }
      return env.GLOBAL_GAME.getByName("global").fetch(request);
    }
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;
