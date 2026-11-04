import { expect, test } from "@playwright/test";

test("shows the icon splash before the initial lobby", async ({ page }) => {
  await page.addInitScript(() => {
    const originalSetTimeout = window.setTimeout.bind(window);
    window.__splashTimeoutCallback = null;
    window.setTimeout = (callback, delay, ...args) => {
      if (delay === 1000) {
        window.__splashTimeoutCallback = callback;
        return 0;
      }
      return originalSetTimeout(callback, delay, ...args);
    };
  });
  await page.goto("/");
  await expect(page.locator("#splash-screen img")).toHaveAttribute("src", "./icon-new.png");
  await expect(page.getByRole("button", { name: /Join game|הצטרפות למשחק/ })).toBeVisible();
  await page.evaluate(() => window.__splashTimeoutCallback());
  await expect(page.locator("#splash-screen")).toHaveCount(0);
});

test("lobby instructions are prominent and fit in the Hebrew mobile layout", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "language", { configurable: true, get: () => "he-IL" });
  });
  await page.goto("/");

  const instructions = page.locator(".instructions");
  await expect(instructions).toBeVisible();
  await expect(instructions).toHaveCSS("font-size", "16px");
  await expect(instructions).toHaveCSS("font-weight", "800");
  const fits = await instructions.evaluate((element) => element.scrollWidth <= element.clientWidth);
  expect(fits).toBe(true);
});

test("hl query parameter forces the web game language", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, "language", { configurable: true, get: () => "he-IL" });
  });

  await page.goto("/?hl=en");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.locator("html")).toHaveAttribute("dir", "ltr");
  await expect(page.getByRole("button", { name: "Join game" })).toBeVisible();

  await page.goto("/?hl=he");
  await expect(page.locator("html")).toHaveAttribute("lang", "he");
  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  await expect(page.getByRole("button", { name: "הצטרפות למשחק" })).toBeVisible();
});

async function installWebSocketMock(page, language = "en-US") {
  await page.addInitScript(() => {
    class MockWebSocket {
      static CONNECTING = 0;
      static OPEN = 1;
      static CLOSING = 2;
      static CLOSED = 3;

      constructor(url) {
        this.url = url;
        this.readyState = MockWebSocket.CONNECTING;
        this.listeners = new Map();
        this.sent = [];
        window.__mockSockets.push(this);
      }

      addEventListener(type, listener) {
        const listeners = this.listeners.get(type) || [];
        listeners.push(listener);
        this.listeners.set(type, listeners);
      }

      emit(type, event = {}) {
        if (type === "open") this.readyState = MockWebSocket.OPEN;
        if (type === "close") this.readyState = MockWebSocket.CLOSED;
        for (const listener of this.listeners.get(type) || []) listener(event);
      }

      send(message) { this.sent.push(message); }
      close() { this.readyState = MockWebSocket.CLOSED; }
    }

    window.__mockSockets = [];
    Object.defineProperty(window, "WebSocket", { configurable: true, value: MockWebSocket });
    window.matchMedia = window.matchMedia || (() => ({ matches: false, addListener() {}, removeListener() {} }));
  });
  await page.addInitScript((language) => {
    Object.defineProperty(navigator, "language", { configurable: true, get: () => language });
  }, language);
}

async function connectWithRoster(page, { language = "en-US" } = {}) {
  await installWebSocketMock(page, language);
  await page.goto("/");
  await page.getByRole("button", { name: /Join game|הצטרפות למשחק/ }).click();
  await expect.poll(() => page.evaluate(() => window.__mockSockets.length)).toBe(1);
  await page.evaluate(() => window.__mockSockets[0].emit("open"));
  const sessionId = await page.evaluate(() => new URL(window.__mockSockets[0].url).searchParams.get("sessionId"));
  const otherId = "other-session";
  const now = Date.now();
  await page.evaluate(({ sessionId, otherId, now }) => {
    const socket = window.__mockSockets[0];
    socket.emit("message", { data: JSON.stringify({
      type: "roster", revision: 1, players: [
        { sessionId, nickname: "😀", countryCode: "US", timeZone: "America/New_York" },
        { sessionId: otherId, nickname: "🦊", countryCode: "IL", timeZone: "Asia/Jerusalem" },
      ],
    }) });
    socket.emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 1, phase: "active", number: 7,
      turnId: "turn-7", activeSessionId: otherId, connectedPlayers: 2,
      serverTime: now, deadline: now + 7000, gameOverReason: null,
      failedSessionId: null, responseRankings: [],
    }) });
  }, { sessionId, otherId, now });
  await expect(page.locator(".player-corner")).toBeVisible();
  return { sessionId, otherId };
}

test("player indicators render, follow turn changes and roster updates, and hide at game over", async ({ page }) => {
  const { sessionId, otherId } = await connectWithRoster(page);
  const indicators = page.locator(".player-corner-item");
  await expect(indicators).toHaveCount(2);
  await expect(indicators.nth(0)).toContainText("🦊");
  await expect(indicators.nth(0)).toContainText("Current player");
  await expect(indicators.nth(1)).toContainText("😀");
  await expect(indicators.nth(1)).toContainText("You");

  const now = Date.now();
  await page.evaluate(({ sessionId, now }) => {
    const socket = window.__mockSockets[0];
    socket.emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 2, phase: "active", number: 8,
      turnId: "turn-8", activeSessionId: sessionId, connectedPlayers: 2,
      serverTime: now, deadline: now + 7000, gameOverReason: null,
      failedSessionId: null, responseRankings: [],
    }) });
  }, { sessionId, now });
  await expect(indicators.nth(0)).toContainText("😀");

  await page.evaluate(({ otherId }) => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 3, phase: "active", number: 9,
      turnId: "turn-9", activeSessionId: otherId, connectedPlayers: 2,
      serverTime: Date.now(), deadline: Date.now() + 7000,
      gameOverReason: null, failedSessionId: null, responseRankings: [],
    }) });
  }, { otherId });
  await expect(indicators.nth(0)).toContainText("🦊");

  await page.evaluate(() => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 4, phase: "gameOver", number: 9,
      turnId: null, activeSessionId: null, connectedPlayers: 2,
      serverTime: Date.now(), deadline: null, gameOverReason: "wrongAnswer",
      failedSessionId: "other-session", responseRankings: [],
    }) });
  });
  await expect(page.locator(".player-corner")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Play again" })).toBeVisible();
});

test("missing roster data uses the fallback emoji and stale roster revisions are ignored", async ({ page }) => {
  await connectWithRoster(page);
  await page.evaluate(() => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 2, phase: "active", number: 8,
      turnId: "turn-8", activeSessionId: "not-in-roster", connectedPlayers: 2,
      serverTime: Date.now(), deadline: Date.now() + 7000,
      gameOverReason: null, failedSessionId: null, responseRankings: [],
    }) });
  });
  await expect(page.locator(".player-corner-item").nth(0)).toContainText("❔");
  await page.evaluate(() => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "roster", revision: 0,
      players: [{ sessionId: "not-in-roster", nickname: "🐸" }],
    }) });
  });
  await expect(page.locator(".player-corner-item").nth(0)).toContainText("❔");
  await page.evaluate(() => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "roster", revision: 2,
      players: [{ sessionId: "not-in-roster", nickname: "🐸" }],
    }) });
  });
  await expect(page.locator(".player-corner-item").nth(0)).toContainText("🐸");
  await page.evaluate(() => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "roster", revision: 3,
      players: [{ sessionId: "not-in-roster", nickname: "<img>" }],
    }) });
  });
  await expect(page.locator(".player-corner-item").nth(0)).toContainText("🐸");
});

test("Hebrew labels and indicators fit inside the card at a narrow viewport", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 740 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  const { otherId } = await connectWithRoster(page, { language: "he-IL" });
  await expect(page.locator("html")).toHaveAttribute("dir", "rtl");
  await expect(page.locator(".player-corner-item").nth(0)).toContainText("השחקן הנוכחי");
  await expect(page.locator(".player-corner-item").nth(1)).toContainText("אתם");
  const bounds = await page.locator(".player-corner").evaluate((element) => {
    const box = element.getBoundingClientRect();
    const card = element.closest(".game-card").getBoundingClientRect();
    return { left: box.left, right: box.right, cardLeft: card.left, cardRight: card.right, scrollWidth: element.scrollWidth };
  });
  expect(bounds.left).toBeGreaterThanOrEqual(bounds.cardLeft);
  expect(bounds.right).toBeLessThanOrEqual(bounds.cardRight);
  expect(bounds.scrollWidth).toBeLessThanOrEqual(bounds.cardRight - bounds.cardLeft);
  expect(otherId).toBe("other-session");
});

test("offline fallback starts a solo round, accepts an answer, and keeps indicators hidden", async ({ page }) => {
  await installWebSocketMock(page);
  await page.goto("/");
  await page.getByRole("button", { name: "Join game" }).click();
  await page.evaluate(() => window.__mockSockets[0].emit("error"));
  await expect(page.locator("#offline-dialog")).toBeVisible();
  await page.getByRole("button", { name: "Continue offline" }).click();
  await expect(page.locator(".number-orb")).toHaveText("1");
  await expect(page.locator(".player-corner")).toHaveCount(0);
  await page.getByRole("button", { name: "Answer with the number" }).click();
  await expect(page.locator(".number-orb")).toHaveText("2");
  await expect(page.locator(".player-corner")).toHaveCount(0);
  await page.getByRole("button", { name: "BOOM" }).click();
  await expect(page.locator("body")).toHaveAttribute("data-phase", "game-over");
  await expect(page.locator(".player-corner")).toHaveCount(0);
});

test("nickname and theme dialogs update the rendered lobby and persist preferences", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("button", { name: /Choose an emoji/ }).click();
  await expect(page.locator("#nickname-dialog")).toBeVisible();
  await page.locator('#emoji-grid [data-emoji="🦊"]').click();
  await expect(page.locator(".nickname-button")).toContainText("🦊");
  expect(await page.evaluate(() => localStorage.getItem("seven-boom-nickname-emoji"))).toBe("🦊");

  await page.getByRole("button", { name: /Colors/ }).click();
  await expect(page.locator("#theme-dialog")).toBeVisible();
  await page.locator('#theme-options [data-theme="classic"]').click();
  await expect(page.locator("#theme-dialog")).toBeHidden();
  expect(await page.evaluate(() => localStorage.getItem("seven-boom-theme"))).toBe("classic");
});

test("nickname picker rejects an emoji already used by another roster player", async ({ page }) => {
  const { sessionId } = await connectWithRoster(page);
  await page.evaluate(() => {
    const dialog = document.querySelector("#nickname-dialog");
    document.querySelector("#emoji-grid").innerHTML = '<button type="button" data-emoji="🦊">🦊</button><button type="button" data-emoji="🐼">🐼</button>';
    dialog.showModal();
  });

  await page.locator('#emoji-grid [data-emoji="🦊"]').click();
  await expect(page.locator("#emoji-error")).toBeVisible();
  await expect(page.locator("#emoji-error")).toContainText("Someone else has already chosen this emoji");
  await expect(page.locator("#nickname-dialog")).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem("seven-boom-nickname-emoji"))).toBeNull();

  await page.locator('#emoji-grid [data-emoji="🐼"]').click();
  await expect(page.locator("#nickname-dialog")).toBeHidden();
  expect(await page.evaluate(() => localStorage.getItem("seven-boom-nickname-emoji"))).toBe("🐼");
  expect(sessionId).toBeTruthy();
});

test("server duplicate rejection returns the user to the picker to choose again", async ({ page }) => {
  await installWebSocketMock(page);
  await page.goto("/");
  await page.getByRole("button", { name: /Join game/ }).click();
  await expect.poll(() => page.evaluate(() => window.__mockSockets.length)).toBe(1);
  await page.evaluate(() => {
    const socket = window.__mockSockets[0];
    socket.emit("open");
    socket.emit("message", { data: JSON.stringify({ type: "error", code: "nicknameTaken" }) });
  });
  await expect(page.locator("#nickname-dialog")).toBeVisible();
  await expect(page.locator("#emoji-error")).toContainText("Someone else has already chosen this emoji");

  await page.locator('#emoji-grid [data-emoji="🦊"]').click();
  await expect(page.locator("#nickname-dialog")).toBeHidden();
  await page.getByRole("button", { name: /Join game/ }).click();
  await expect.poll(() => page.evaluate(() => window.__mockSockets.length)).toBe(2);
  expect(await page.evaluate(() => new URL(window.__mockSockets[1].url).searchParams.get("nickname"))).toBe("🦊");
});

test("emoji order stays left to right in Hebrew", async ({ page }) => {
  await installWebSocketMock(page, "he-IL");
  await page.goto("/");
  await page.getByRole("button", { name: /בחירת אימוג׳י/ }).click();
  await expect(page.locator("#emoji-grid")).toHaveCSS("direction", "ltr");
});

test("player roster dialog reflects roster updates and answer controls emit the protocol message", async ({ page }) => {
  const { sessionId } = await connectWithRoster(page);
  await page.getByRole("button", { name: /2 players/ }).click();
  await expect(page.locator("#roster-list")).toContainText("🦊");
  await page.locator('#roster-dialog [data-action="close-roster"]').click();
  await page.evaluate((sessionId) => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 2, phase: "active", number: 7,
      turnId: "turn-7-local", activeSessionId: sessionId, connectedPlayers: 2,
      serverTime: Date.now(), deadline: Date.now() + 7000,
      gameOverReason: null, failedSessionId: null, responseRankings: [],
    }) });
  }, sessionId);
  await page.getByRole("button", { name: "Answer with the number" }).click();
  const sent = await page.evaluate(() => window.__mockSockets[0].sent.map(JSON.parse));
  expect(sent).toHaveLength(1);
  expect(sent[0]).toMatchObject({
    type: "answer", turnId: "turn-7-local", responseTimeMillis: expect.any(Number),
    answer: { type: "number", value: 7 },
  });
});

test("arrow keys submit answers on the active turn", async ({ page }) => {
  const { sessionId } = await connectWithRoster(page);
  await page.evaluate((sessionId) => {
    window.__mockSockets[0].emit("message", { data: JSON.stringify({
      type: "snapshot", revision: 2, phase: "active", number: 7,
      turnId: "turn-7-local", activeSessionId: sessionId, connectedPlayers: 2,
      serverTime: Date.now(), deadline: Date.now() + 7000,
      gameOverReason: null, failedSessionId: null, responseRankings: [],
    }) });
  }, sessionId);
  await page.keyboard.press("ArrowLeft");
  const sent = await page.evaluate(() => window.__mockSockets[0].sent.map(JSON.parse));
  expect(sent).toHaveLength(1);
  expect(sent[0]).toMatchObject({ type: "answer", turnId: "turn-7-local", answer: { type: "boom" } });
});
