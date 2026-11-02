import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  EMOJIS,
  TURN_MS,
  answerMessage,
  gameSocketUrl,
  isBoom,
  remainingProgress,
  recognizedAnswer,
  remainingSeconds,
  responseMillis,
} from "../game-core.mjs";

test("the emoji nickname list is unique and matches the expected roster size", () => {
  assert.equal(EMOJIS.length, 24);
  assert.equal(new Set(EMOJIS).size, EMOJIS.length);
});

test("7-Boom numbers include multiples of seven and numbers containing seven", () => {
  for (const number of [7, 14, 17, 70, 77, 105]) assert.equal(isBoom(number), true, String(number));
  for (const number of [1, 6, 8, 16, 18, 99]) assert.equal(isBoom(number), false, String(number));
});

test("the countdown uses the server clock offset and clamps to zero through seven", () => {
  const deadline = 20_000;
  assert.equal(remainingSeconds(deadline, 0, 13_001), 7);
  assert.equal(remainingSeconds(deadline, 0, 14_001), 6);
  assert.equal(remainingSeconds(deadline, 500, 19_001), 1);
  assert.equal(remainingSeconds(deadline, 0, 25_000), 0);
  assert.equal(remainingSeconds(null, 0, 1), 0);
});

test("the turn progress shrinks continuously over the seven-second turn", () => {
  const deadline = 20_000;
  assert.equal(remainingProgress(deadline, 0, 13_000), 1);
  assert.equal(remainingProgress(deadline, 0, 16_500), 0.5);
  assert.equal(remainingProgress(deadline, 500, 16_500), 3_000 / 7_000);
  assert.equal(remainingProgress(deadline, 0, 21_000), 0);
  assert.equal(remainingProgress(null, 0, 1), 0);
});

test("response times are measured from the seven-second turn start and clamped", () => {
  const deadline = 17_000;
  assert.equal(responseMillis(deadline, 0, 10_000), 0);
  assert.equal(responseMillis(deadline, 0, 11_250), 1_250);
  assert.equal(responseMillis(deadline, 500, 11_250), 1_750);
  assert.equal(responseMillis(deadline, 0, 20_000), TURN_MS);
  assert.equal(responseMillis(null, 0, 10_000), 0);
});

test("voice answers accept numeric, English, Hebrew, and spoken-digit forms", () => {
  assert.equal(recognizedAnswer("one", 1), "number");
  assert.equal(recognizedAnswer("ואן", 1), "number");
  assert.equal(recognizedAnswer("וואן", 1), "number");
  assert.equal(recognizedAnswer("1", 1), "number");
  assert.equal(recognizedAnswer("16", 16), "number");
  assert.equal(recognizedAnswer("twenty-three", 23), "number");
  assert.equal(recognizedAnswer("עשרים ושלוש", 23), "number");
  assert.equal(recognizedAnswer("one two", 12), "number");
  assert.equal(recognizedAnswer("wrong", 12), "invalid");
});

test("voice answers accept BOOM in English or Hebrew only on BOOM numbers", () => {
  for (const phrase of ["boom", "BOOM!", "בום", "בום בום"]) assert.equal(recognizedAnswer(phrase, 77), "boom");
  assert.equal(recognizedAnswer("seven", 7), "invalid");
  assert.equal(recognizedAnswer("boom", 8), "invalid");
});

test("answer messages match the Worker protocol and cap response time", () => {
  assert.deepEqual(answerMessage("number", "turn-1", 12, 530), {
    type: "answer", turnId: "turn-1", responseTimeMillis: 530,
    answer: { type: "number", value: 12 },
  });
  assert.deepEqual(answerMessage("boom", "turn-2", 14, -1).answer, { type: "boom" });
  assert.deepEqual(answerMessage("invalid", "turn-3", 8, TURN_MS + 10), {
    type: "answer", turnId: "turn-3", responseTimeMillis: TURN_MS,
    answer: { type: "invalid" },
  });
});

test("WebSocket routing uses the local Worker, deployed Worker, or production endpoint", () => {
  assert.equal(gameSocketUrl({ protocol: "http:", hostname: "localhost", host: "localhost:8787", port: "8787" }), "ws://localhost:8787/game");
  assert.equal(gameSocketUrl({ protocol: "https:", hostname: "global-seven-boom.lat-shahar.workers.dev", host: "global-seven-boom.lat-shahar.workers.dev", port: "" }), "wss://global-seven-boom.lat-shahar.workers.dev/game");
  assert.equal(gameSocketUrl({ protocol: "https:", hostname: "shahart.github.io", host: "shahart.github.io", port: "" }), "wss://global-seven-boom.lat-shahar.workers.dev/game");
  assert.equal(gameSocketUrl({ protocol: "http:", hostname: "localhost", host: "localhost:8766", port: "8766" }), "ws://global-seven-boom.lat-shahar.workers.dev/game");
});

test("the static web entry references the lightweight client and no Compose/WASM bundle", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  assert.match(html, /href="\.\/styles\.css"/);
  assert.match(html, /type="module" src="\.\/app\.js"/);
  assert.doesNotMatch(html, /composeApp\.js|\.wasm|composeResources/i);
  const app = await readFile(new URL("../app.js", import.meta.url), "utf8");
  assert.match(app, /id="turn-progress"/);
  assert.doesNotMatch(app, /id="countdown"/);
  assert.equal([...app.matchAll(/data-action="open-roster"/g)].length, 1);
  assert.match(app, /class="answer-button number"[^>]*aria-label="\$\{esc\(t\("answerNumber"\)\)\}"><\/button>/);
  await readFile(new URL("../styles.css", import.meta.url));
});

test("dialog close actions are handled outside the app root", async () => {
  const app = await readFile(new URL("../app.js", import.meta.url), "utf8");
  assert.match(app, /document\.addEventListener\("click"/);
  for (const action of ["close-nickname", "close-theme", "close-roster"]) {
    assert.match(app, new RegExp(`case "${action}"`));
  }
});
