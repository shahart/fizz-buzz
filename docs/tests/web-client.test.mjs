import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import test from "node:test";
import {
  EMOJIS,
  TURN_MS,
  answerMessage,
  gameSocketUrl,
  hasMultiplePlayers,
  isBoom,
  isWinnerForSnapshot,
  playerIndicatorsMarkup,
  remainingProgress,
  recognizedAnswer,
  remainingSeconds,
  responseMillis,
} from "../game-core.mjs";

test("the emoji nickname list is unique and matches the expected roster size", () => {
  assert.equal(EMOJIS.length, 24);
  assert.equal(new Set(EMOJIS).size, EMOJIS.length);
});


test("countdown music is available to the web and Android clients", async () => {
  const track = "cyberwave-orchestra-cinematic-tension-suspenseful-thriller-music-loop-297627.mp3";
  const webSource = await readFile(new URL("../src/app.ts", import.meta.url), "utf8");
  const androidSource = await readFile(new URL("../../composeApp/src/androidMain/kotlin/com/shahartal/fizzbuzz/Sound.android.kt", import.meta.url), "utf8");
  const wasmSource = await readFile(new URL("../../composeApp/src/wasmJsMain/kotlin/com/shahartal/fizzbuzz/Sound.wasmJs.kt", import.meta.url), "utf8");
  const commonAppSource = await readFile(new URL("../../composeApp/src/commonMain/kotlin/com/shahartal/fizzbuzz/App.kt", import.meta.url), "utf8");

  assert.equal(existsSync(new URL("../" + track, import.meta.url)), true);
  assert.match(webSource, new RegExp(track));
  assert.match(webSource, /snapshot\.activeSessionId === sessionId/);
  assert.match(androidSource, /R\.raw\.countdown_music/);
  assert.equal(existsSync(new URL("../../composeApp/src/androidMain/res/raw/countdown_music.mp3", import.meta.url)), true);
  assert.match(androidSource, /R\.raw\.wrong_buzzer/);
  assert.equal(existsSync(new URL("../wrong-buzzer-6268.mp3", import.meta.url)), true);
  assert.equal(existsSync(new URL("../../composeApp/src/androidMain/res/raw/wrong_buzzer.mp3", import.meta.url)), true);
  assert.ok(webSource.includes("u_o8xh7gwsrj-cute_happy_victory-476376.mp3"));
  assert.equal(existsSync(new URL("../u_o8xh7gwsrj-cute_happy_victory-476376.mp3", import.meta.url)), true);
  assert.ok(androidSource.includes("R.raw.cute_happy_victory"));
  assert.equal(existsSync(new URL("../../composeApp/src/androidMain/res/raw/cute_happy_victory.mp3", import.meta.url)), true);
  assert.ok(wasmSource.includes("u_o8xh7gwsrj-cute_happy_victory-476376.mp3"));
  assert.ok(commonAppSource.includes("GameResult.WINNER -> playWinnerSound()"));
  assert.match(commonAppSource, /activeTurn \|\| \(failed && snapshot\.gameOverReason == GameOverReason\.WRONG_ANSWER && !snapshot\.failedByAnotherPlayer\(sessionId\)\)/);
  assert.match(commonAppSource, /Res\.string\.voice_heard, lastHeard/);
});

test("mute is available across screens and countdown playback resumes without rewinding", async () => {
  const webSource = await readFile(new URL("../src/app.ts", import.meta.url), "utf8");
  const eventsSource = await readFile(new URL("../src/events.ts", import.meta.url), "utf8");
  const rendererSource = await readFile(new URL("../src/render.ts", import.meta.url), "utf8");
  const androidSource = await readFile(new URL("../../composeApp/src/androidMain/kotlin/com/shahartal/fizzbuzz/Sound.android.kt", import.meta.url), "utf8");
  const appSource = await readFile(new URL("../../composeApp/src/commonMain/kotlin/com/shahartal/fizzbuzz/App.kt", import.meta.url), "utf8");
  const syncFunction = webSource.split("function syncCountdownMusic()")[1].split("function speechSupported()")[0];

  assert.match(webSource, /data-action="toggle-mute"/);
  assert.match(webSource, /seven-boom-sound-muted/);
  assert.match(webSource, /countdownMusic\.loop = true/);
  assert.match(webSource, /countdownMusic\.pause\(\);\s*\}/);
  assert.doesNotMatch(syncFunction, /currentTime\s*=\s*0/);
  assert.match(syncFunction, /activeGame && !state\.soundMuted/);
  assert.match(syncFunction, /ownsActiveTurn \? 0\.05 : 0\.05/);
  assert.match(eventsSource, /case "toggle-mute"/);
  assert.match(rendererSource, /addTopbar/);
  assert.match(androidSource, /if \(isPlaying\) pause\(\)/);
  assert.match(androidSource, /if \(isCurrentPlayerTurn\) 0\.05f else 0\.05f/);
  assert.match(appSource, /SoundToggleButton\(soundMuted, onToggleSound, compact\)/);
});

test("speech recognition alternates English and Hebrew when the preferred language misses", async () => {
  const webSource = await readFile(new URL("../src/app.ts", import.meta.url), "utf8");
  const wasmSource = await readFile(new URL("../../composeApp/src/wasmJsMain/kotlin/com/shahartal/fizzbuzz/SpeechRecognizer.wasmJs.kt", import.meta.url), "utf8");
  const androidSource = await readFile(new URL("../../composeApp/src/androidMain/kotlin/com/shahartal/fizzbuzz/SpeechRecognizer.android.kt", import.meta.url), "utf8");

  assert.match(webSource, /recognition\.lang = recognitionLanguage/);
  assert.match(webSource, /recognitionLanguage = recognitionLanguage === preferredLanguage \? alternateLanguage : preferredLanguage/);
  assert.match(webSource, /new URLSearchParams\(window\.location\.search\)\.get\("hl"\)/);
  assert.match(webSource, /requestedLanguage === "he" \|\| \(requestedLanguage !== "en" && locale\.startsWith\("he"\)\)/);
  assert.match(wasmSource, /recognition\.lang = window\.__countdownSpeechLanguage/);
  assert.match(wasmSource, /window\.__countdownSpeechLanguage === preferred \? alternate : preferred/);
  assert.match(androidSource, /recognitionLanguages = listOf/);
  assert.match(androidSource, /EXTRA_LANGUAGE, recognitionLanguages\[languageIndex\]/);
});

test("web footer credits the Pixabay music, failure cue, and license on the welcome screen only", async () => {
  const appSource = await readFile(new URL("../src/app.ts", import.meta.url), "utf8");
  const renderSource = await readFile(new URL("../src/render.ts", import.meta.url), "utf8");

  assert.match(appSource, /function audioCreditMarkup\(\)/);
  assert.ok(appSource.includes("pixabay.com/sound-effects/cute-happy-victory-476376"));
  assert.match(appSource, /pixabay\.com\/music\/main-title-cinematic-tension-suspenseful-thriller-music-loop-297627/);
  assert.match(appSource, /pixabay\.com\/sound-effects\/film-special-effects-wrong-buzzer-6268/);
  assert.match(appSource, /pixabay\.com\/service\/license-summary/);
  assert.match(appSource, /next\.failedSessionId === sessionId\) playFailureSound\(\)/);
  assert.ok(appSource.includes("isWinnerForSnapshot(next, sessionId)"));
  assert.equal((renderSource.match(/audioCreditMarkup\(\)/g) ?? []).length, 1);
  assert.match(renderSource, /personalRecords.*audioCreditMarkup\(\)/);
  assert.doesNotMatch(renderSource.split("function renderGame()")[1], /audioCreditMarkup/);
});

test("7-Boom numbers include multiples of seven and numbers containing seven", () => {
  for (const number of [7, 14, 17, 70, 77, 105]) assert.equal(isBoom(number), true, String(number));
  for (const number of [1, 6, 8, 16, 18, 99]) assert.equal(isBoom(number), false, String(number));
});

test("player indicators are hidden for a solo game and shown for multiple players", () => {
  assert.equal(hasMultiplePlayers(0), false);
  assert.equal(hasMultiplePlayers(1), false);
  assert.equal(hasMultiplePlayers(2), true);
  assert.equal(hasMultiplePlayers(10), true);
});

test("a late joiner absent from the round rankings is not treated as a winner", () => {
  const finishedTwoPlayerRound = {
    phase: "gameOver",
    gameOverReason: "wrongAnswer",
    failedSessionId: "failed",
    connectedPlayers: 2,
    responseRankings: [
      { sessionId: "failed", rank: 1 },
      { sessionId: "survivor", rank: 2 },
    ],
  };
  assert.equal(isWinnerForSnapshot(finishedTwoPlayerRound, "survivor"), true);
  assert.equal(isWinnerForSnapshot(finishedTwoPlayerRound, "late-joiner"), false);
});

test("player indicator markup follows live player count, active turn, localization, and HTML escaping", () => {
  const labels = { currentLabel: "Current player", youLabel: "You" };
  const onePlayer = playerIndicatorsMarkup({ connectedPlayers: 1, currentEmoji: "🚀", yourEmoji: "🦊", ...labels });
  assert.equal(onePlayer, "");

  const multiplayer = playerIndicatorsMarkup({ connectedPlayers: 2, currentEmoji: "🚀", yourEmoji: "🦊", ...labels });
  assert.match(multiplayer, /aria-label="Current player: 🚀; You: 🦊"/);
  assert.match(multiplayer, /<span>Current player<\/span>/);
  assert.match(multiplayer, /<span>You<\/span>/);

  const hebrew = playerIndicatorsMarkup({
    connectedPlayers: 3,
    currentEmoji: "🚀",
    yourEmoji: "🦊",
    currentLabel: "השחקן הנוכחי",
    youLabel: "אתם",
  });
  assert.match(hebrew, /השחקן הנוכחי/);
  assert.match(hebrew, /אתם/);
  assert.equal(playerIndicatorsMarkup({ connectedPlayers: 3, active: false, ...labels }), "");
  assert.match(playerIndicatorsMarkup({
    connectedPlayers: 2,
    currentEmoji: "<img>",
    yourEmoji: "&",
    ...labels,
  }), /&lt;img&gt;.*&amp;/s);
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
  assert.match(html, /type="module" src="\.\/build\/src\/app\.js"/);
  assert.doesNotMatch(html, /composeApp\.js|\.wasm|composeResources/i);
  const app = await readFile(new URL("../src/app.ts", import.meta.url), "utf8");
  const renderer = await readFile(new URL("../src/render.ts", import.meta.url), "utf8");
  const copy = await readFile(new URL("../src/copy.ts", import.meta.url), "utf8");
  assert.match(app, /from "\.\/render\.js"/);
  assert.match(app, /from "\.\/events\.js"/);
  assert.match(renderer, /id="turn-progress"/);
  assert.match(renderer, /playerIndicatorsMarkup\(\{/);
  assert.match(renderer, /active: !failed/);
  assert.match(renderer, /\$\{playerIndicators\}/);
  assert.match(renderer, /currentEmoji/);
  assert.match(renderer, /yourEmoji/);
  assert.doesNotMatch(renderer, /id="countdown"/);
  assert.equal([...renderer.matchAll(/data-action="open-roster"/g)].length, 1);
  assert.match(renderer, /class="answer-button number"[^>]*aria-label="\$\{esc\(t\("answerNumber"\)\)\}"><\/button>/);
  assert.match(renderer, /failedPlayer !== sessionId/);
  assert.match(renderer, /isWinnerForSnapshot\(snapshot, sessionId\)/);
  assert.match(renderer, /rank\.sessionId !== snapshot\.failedSessionId/);
  assert.match(renderer, /snapshot\.gameOverReason === "wrongAnswer" && snapshot\.failedSessionId === sessionId && state\.lastHeard/);
  assert.match(app, /function answer\(kind, heardTranscript = null\)/);
  assert.match(app, /state\.lastHeard = heardTranscript \?\? ""/);
  assert.match(app, /answer\(answerKind, transcript\)/);
  assert.match(renderer, /playerCount >= 3/);
  assert.match(renderer, /class="result-popup" role="alert"/);
  const styles = await readFile(new URL("../styles.css", import.meta.url), "utf8");
  const composeApp = await readFile(new URL("../../composeApp/src/commonMain/kotlin/com/shahartal/fizzbuzz/App.kt", import.meta.url), "utf8");
  assert.match(styles, /data-outcome="winner"/);
  assert.match(styles, /--turquoise: #2da095/);
  assert.match(styles, /data-outcome="winner"\] \{ background: var\(--turquoise\)/);
  assert.match(styles, /\.answer-button\.number \{ background: var\(--turquoise\)/);
  assert.match(styles, /--bordeaux: #a44968/);
  assert.match(styles, /body\[data-phase="game-over"\] \{ background: var\(--bordeaux\)/);
  assert.match(styles, /\.answer-button\.boom \{ background: var\(--bordeaux\)/);
  assert.match(composeApp, /Turquoise = Color\(0xFF2DA095\)/);
  assert.match(composeApp, /Bordeaux = Color\(0xFFA44968\)/);
  assert.match(styles, /data-outcome="spectator"/);
  assert.match(styles, /@keyframes result-flash/);
  assert.match(styles, /\.player-corner \{ position: absolute/);
  assert.match(styles, /inset-inline-end:/);
  assert.match(styles, /\.player-corner-item \{/);
  assert.match(styles, /\.player-corner-emoji \{/);
  assert.match(styles, /html\[dir="rtl"\]/);
  assert.match(styles, /@media \(max-width: 390px\)/);
  assert.match(copy, /currentPlayer: "השחקן הנוכחי",\s*you: "אתם"/);
});

test("web entry uses the replacement PNG icon and shows a one second splash", async () => {
  const html = await readFile(new URL("../index.html", import.meta.url), "utf8");
  const styles = await readFile(new URL("../styles.css", import.meta.url), "utf8");

  assert.match(html, /rel="icon" type="image\/png" href="\.\/icon-new\.png"/);
  assert.match(html, /id="splash-screen"[\s\S]*?src="\.\/icon-new\.png"/);
  assert.match(html, /\}, 1000\)/);
  assert.match(styles, /\.splash-screen\s*\{[^}]*position:\s*fixed/);
  assert.match(styles, /width:\s*min\(58\.8vw, 266px\)/);
});

test("dialog close actions are handled outside the app root", async () => {
  const events = await readFile(new URL("../src/events.ts", import.meta.url), "utf8");
  assert.match(events, /document\.addEventListener\("click"/);
  for (const action of ["close-nickname", "close-theme", "close-roster"]) {
    assert.match(events, new RegExp(`case "${action}"`));
  }
});
