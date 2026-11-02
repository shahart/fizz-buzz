import { EMOJIS, TURN_MS, answerMessage, gameSocketUrl, isBoom, recognizedAnswer, remainingProgress, remainingSeconds, responseMillis } from "./game-core.mjs";

(() => {
  "use strict";

  const THEMES = [
    { id: "classic", unlock: 0, label: "classic", colors: ["#102a43", "#eaf4ff", "#1473e6", "#d92d20"] },
    { id: "mint", unlock: 14, label: "mint", colors: ["#124b42", "#e3f7ee", "#168a68", "#cb3f54"] },
    { id: "sunset", unlock: 49, label: "sunset", colors: ["#572b24", "#fff0de", "#e56a35", "#b52f45"] },
    { id: "midnight", unlock: 77, label: "midnight", colors: ["#e7ecff", "#171b34", "#8798ff", "#ff667a"] },
  ];
  const NICKNAMES = new Set(EMOJIS);
  const COPY = {
    en: {
      brand: "SEVEN BOOM", ready: "Ready?", welcome: "A fast global number game.",
      nickname: "Your emoji nickname", chooseEmoji: "Choose an emoji", nicknameEyebrow: "Make it yours",
      preferences: "Personalize", themes: "Choose a theme", theme: "Theme", classic: "Classic", mint: "Mint", sunset: "Sunset", midnight: "Midnight",
      locked: "Best {n} to unlock", selected: "Selected", close: "Close", join: "Join game", rejoin: "Rejoin game",
      instructions: "Say or tap the number. Use BOOM 💣 for multiples of 7 and numbers containing 7.",
      speechUnavailable: "Speech recognition is unavailable here. The answer buttons are ready.", keyboard: "On a keyboard, use ← or →.",
      best: "Best {n}", lowest: "Fastest {n}s", average: "Average {n}s", personalRecords: "Personal records",
      joining: "Joining the global game…", joiningHint: "Getting the next turn ready", yourTurn: "Your turn", waiting: "Waiting for your turn",
      players: "{n} players", liveGame: "Live game", activeTurn: "Blue ring marks the active turn.", rosterLoading: "Loading players…",
      seconds: "seconds left", boom: "BOOM", wrong: "Wrong answer", timeUp: "Time is up", otherWrong: "Another player gave a wrong answer",
      answerNumber: "Answer with the number",
      otherTimeout: "Another player ran out of time", feedbackNumber: "The correct answer was {n}.",
      feedbackDivisible: "{n} is divisible by 7, so the answer is BOOM.", feedbackContains: "{n} contains 7, so the answer is BOOM.",
      feedbackBoth: "{n} is divisible by 7 and contains 7, so the answer is BOOM.", winner: "Fastest players", playAgain: "Play again",
      rank: "Response-time rank: {rank} of {total}", unranked: "Response-time rank: —", latency: "Latency {n} ms",
      connection: "Connection", soloTitle: "Playing offline", soloMessage: "The global game is unavailable. Continue with a local single-player round?",
      continue: "Continue offline", statusOffline: "Offline game", speechListening: "Listening…", speechUnavailableShort: "Tap to answer",
      voiceHeard: "Heard: {text}", themeButton: "Colors", rosterButton: "Players", home: "Back to lobby",
    },
    he: {
      brand: "בום שבע", ready: "מוכנים?", welcome: "משחק מספרים עולמי ומהיר.",
      nickname: "כינוי האימוג׳י שלך", chooseEmoji: "בחירת אימוג׳י", nicknameEyebrow: "בוחרים כינוי",
      preferences: "התאמה אישית", themes: "בחירת ערכת צבעים", theme: "ערכת צבעים", classic: "קלאסית", mint: "מנטה", sunset: "שקיעה", midnight: "חצות",
      locked: "נדרש שיא של {n} לפתיחה", selected: "נבחרה", close: "סגירה", join: "הצטרפות למשחק", rejoin: "הצטרפות מחדש",
      instructions: "אמרו או לחצו על המספר. השתמשו בבום 💣 עבור כפולות של 7 ומספרים שמכילים 7.",
      speechUnavailable: "זיהוי דיבור אינו זמין כאן. אפשר לענות בעזרת הכפתורים.", keyboard: "במקלדת אפשר להשתמש בחצים ← ו־→.",
      best: "שיא {n}", lowest: "הכי מהיר {n} שנ׳", average: "ממוצע {n} שנ׳", personalRecords: "השיאים האישיים",
      joining: "מצטרפים למשחק העולמי…", joiningHint: "מכינים את התור הבא", yourTurn: "התור שלך", waiting: "ממתינים לתור שלך",
      players: "{n} שחקנים", liveGame: "משחק חי", activeTurn: "הטבעת הכחולה מסמנת את התור הפעיל.", rosterLoading: "טוענים שחקנים…",
      seconds: "שניות נותרו", boom: "בום", wrong: "תשובה שגויה", timeUp: "הזמן נגמר", otherWrong: "שחקן אחר נתן תשובה שגויה",
      answerNumber: "תשובה במספר",
      otherTimeout: "לשחקן אחר נגמר הזמן", feedbackNumber: "התשובה הנכונה הייתה {n}.",
      feedbackDivisible: "{n} מתחלק ב־7, לכן התשובה היא בום.", feedbackContains: "הספרה 7 מופיעה ב־{n}, לכן התשובה היא בום.",
      feedbackBoth: "{n} מתחלק ב־7 וגם מכיל 7, לכן התשובה היא בום.", winner: "השחקנים המהירים", playAgain: "לשחק שוב",
      rank: "דירוג זמן תגובה: {rank} מתוך {total}", unranked: "דירוג זמן תגובה: —", latency: "השהיה {n} אלפיות שנייה",
      connection: "חיבור", soloTitle: "משחקים ללא חיבור", soloMessage: "המשחק העולמי אינו זמין. להמשיך במשחק מקומי לשחקן יחיד?",
      continue: "המשך ללא חיבור", statusOffline: "משחק לא מקוון", speechListening: "מקשיבים…", speechUnavailableShort: "לחצו כדי לענות",
      voiceHeard: "שמעתי: {text}", themeButton: "צבעים", rosterButton: "שחקנים", home: "חזרה ללובי",
    },
  };

  const locale = (navigator.language || "en").toLowerCase();
  const isHebrew = locale.startsWith("he");
  const lang = isHebrew ? "he" : "en";
  const strings = COPY[lang];
  document.documentElement.lang = lang;
  document.documentElement.dir = isHebrew ? "rtl" : "ltr";
  document.title = isHebrew ? "בום שבע" : "7-Boom";

  const root = document.getElementById("app");
  const byId = (id) => document.getElementById(id);
  const nicknameDialog = byId("nickname-dialog");
  const themeDialog = byId("theme-dialog");
  const rosterDialog = byId("roster-dialog");
  const offlineDialog = byId("offline-dialog");
  const sessionId = makeSessionId();
  const state = {
    connection: "idle", socket: null, snapshot: null, roster: [], rosterRevision: -1,
    offset: 0, nickname: storageGet("seven-boom-nickname-emoji", "😀"),
    theme: storageGet("seven-boom-theme", "classic"), best: numberGet("seven-boom-best-number"),
    lowest: numberGet("seven-boom-lowest-response-time-millis"), responseTotal: 0, responseCount: 0,
    latency: null, pendingPing: null, pingAt: 0, pingTimer: 0, joinTimer: 0,
    lastAnsweredTurn: null, intentionalClose: false, speech: null, speechTurn: null,
    lastHeard: "", timerInterval: 0, soloRevision: 0, failureFlash: false,
  };
  function logLocalEvent(event, details = {}) {
    console.info("[SevenBoom]", event, details);
  }
  if (!NICKNAMES.has(state.nickname)) state.nickname = "😀";
  if (!THEMES.some((theme) => theme.id === state.theme)) state.theme = "classic";

  function t(key, values = {}) {
    return (strings[key] || key).replace(/\{(\w+)\}/g, (_, name) => String(values[name] ?? ""));
  }
  function esc(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  }
  function storageGet(key, fallback) {
    try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
  }
  function numberGet(key) {
    const value = Number(storageGet(key, "0"));
    return Number.isSafeInteger(value) && value >= 0 ? value : 0;
  }
  function storageSet(key, value) {
    try { localStorage.setItem(key, String(value)); } catch { /* Storage can be disabled in private browsing. */ }
  }
  function makeSessionId() {
    if (crypto.randomUUID) return crypto.randomUUID();
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
      const r = Math.random() * 16 | 0;
      return (c === "x" ? r : (r & 3 | 8)).toString(16);
    });
  }
  function localTimeZone() {
    try { return Intl.DateTimeFormat().resolvedOptions().timeZone || ""; } catch { return ""; }
  }
  function countryFlag(code) {
    if (!/^[A-Z]{2}$/.test(code || "")) return "";
    return String.fromCodePoint(...[...code].map((letter) => 0x1f1e6 + letter.charCodeAt(0) - 65));
  }
  function timeZoneCity(timeZone) {
    return typeof timeZone === "string" ? timeZone.split("/").pop().replaceAll("_", " ") : "";
  }
  function currentTheme() { return THEMES.find((theme) => theme.id === state.theme) || THEMES[0]; }
  function applyTheme() {
    const [ink, sky, blue, red] = currentTheme().colors;
    const style = document.documentElement.style;
    style.setProperty("--ink", ink);
    style.setProperty("--sky", sky);
    style.setProperty("--blue", blue);
    style.setProperty("--red", red);
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", sky);
  }
  function addTopbar(extra = "") {
    return `<header class="topbar">
      <a class="brand" href="./" aria-label="${esc(t("brand"))}"><span class="brand-mark" aria-hidden="true">7</span><span>${esc(t("brand"))}</span></a>
      <div class="topbar-actions">${extra}<span class="top-chip"><span class="connection-dot ${state.connection === "connected" ? "online" : state.connection === "solo" ? "offline" : ""}"></span>${state.connection === "solo" ? esc(t("statusOffline")) : state.connection === "connected" ? "LIVE" : "7-BOOM"}</span></div>
    </header>`;
  }
  function render() {
    applyTheme();
    if (state.connection === "joining") {
      root.innerHTML = `${addTopbar()}<section class="stage"><div class="game-card loading-card"><div class="loader" aria-hidden="true"></div><p class="eyebrow">${esc(t("connection"))}</p><h1 class="loading-title">${esc(t("joining"))}</h1><p class="subhead">${esc(t("joiningHint"))}</p></div></section><footer class="footer">7-Boom · ${esc(state.nickname)}</footer>`;
      return;
    }
    if ((state.connection === "connected" || state.connection === "solo") && state.snapshot) {
      renderGame();
      return;
    }
    renderLobby();
  }
  function renderLobby() {
    document.body.dataset.phase = "lobby";
    const rejoin = state.connection === "rejoin";
    const speechLine = speechSupported() ? t("instructions") : t("speechUnavailable");
    root.innerHTML = `${addTopbar(`<button class="icon-button" type="button" data-action="open-theme" aria-label="${esc(t("themeButton"))}" title="${esc(t("themeButton"))}">◐</button>`)}
      <section class="stage"><section class="game-card join-card" aria-labelledby="ready-title">
        <p class="eyebrow">${esc(t("brand"))}</p><h1 id="ready-title" class="hero-title">${esc(t("ready"))}</h1>
        <p class="subhead">${esc(t("welcome"))}</p>
        <div class="nickname-block"><span class="field-caption">${esc(t("nickname"))}</span>
          <button class="nickname-button" type="button" data-action="open-nickname" aria-label="${esc(t("chooseEmoji"))}"><span aria-hidden="true">${esc(state.nickname)}</span><span>${esc(t("chooseEmoji"))}</span></button>
        </div>
        <div class="join-options"><button class="quiet-button" type="button" data-action="open-theme">◐ &nbsp;${esc(t("theme"))}: ${esc(t(currentTheme().label))}</button></div>
        <div class="records" aria-label="${esc(t("personalRecords"))}"><span class="record-pill">${esc(t("best", { n: state.best }))}</span><span class="record-pill">${esc(t("lowest", { n: secondsText(state.lowest) }))}</span></div>
        <p class="instructions">${esc(speechLine)}</p>
        ${speechSupported() ? `<p class="keyboard-note">${esc(t("keyboard"))}</p>` : ""}
        <button class="primary-button join-button" type="button" data-action="join">${esc(t(rejoin ? "rejoin" : "join"))}</button>
      </section></section><footer class="footer">${esc(t("personalRecords"))} · 7-Boom</footer>`;
  }
  function renderGame() {
    const snapshot = state.snapshot;
    const failed = snapshot.phase === "gameOver";
    document.body.dataset.phase = failed ? "game-over" : "active";
    const ownsTurn = !failed && snapshot.activeSessionId === sessionId;
    const activeTurn = ownsTurn && state.lastAnsweredTurn !== snapshot.turnId;
    const gameOverClass = failed ? " game-over-card" : "";
    let message = "";
    let clue = "";
    if (failed) {
      const mine = snapshot.failedSessionId === sessionId;
      if (snapshot.gameOverReason === "timeout") message = t(mine ? "timeUp" : "otherTimeout");
      else if (snapshot.gameOverReason === "wrongAnswer") message = t(mine ? "wrong" : "otherWrong");
      if (mine && snapshot.gameOverReason === "wrongAnswer") clue = feedback(snapshot.number);
    } else if (!activeTurn) message = t("waiting");

    const rankings = snapshot.responseRankings || [];
    const ownRank = rankings.find((rank) => rank.sessionId === sessionId);
    const averageMs = ownRank?.averageMillis ?? (state.responseCount ? state.responseTotal / state.responseCount : 0);
    const ownRankText = ownRank ? t("rank", { rank: ownRank.rank, total: rankings.length }) : t("unranked");
    const winners = failed ? [...new Set(rankings.filter((rank) => rank.rank === 1).map((rank) => rank.nickname).filter((emoji) => NICKNAMES.has(emoji)))] : [];
    const currentName = failed ? t("boom") : activeTurn ? t("yourTurn") : t("waiting");

    root.innerHTML = `${addTopbar(`<button class="player-chip" type="button" data-action="open-roster"><span aria-hidden="true">👥</span>${esc(t("players", { n: snapshot.connectedPlayers }))}</button>`)}
      <section class="stage"><section class="game-card${gameOverClass}" aria-label="${esc(currentName)}">
        <div class="game-topline"><p class="turn-label">${failed ? `<span aria-hidden="true">💣</span> ${esc(currentName)}` : esc(currentName)}</p></div>
        <div class="number-orb" aria-live="off">${snapshot.number}</div>
        ${!failed ? `<div id="turn-progress" class="turn-progress" role="progressbar" aria-label="Turn time remaining" aria-valuemin="0" aria-valuemax="100" aria-valuenow="100"><span id="turn-progress-fill" class="turn-progress-fill"></span></div>` : ""}
        <p class="game-message">${esc(message || (activeTurn && state.lastHeard ? t("voiceHeard", { text: state.lastHeard }) : ""))}</p>
        ${clue ? `<p class="game-message">${esc(clue)}</p>` : ""}
        ${winners.length ? `<p class="winner-line">${esc(t("winner"))} &nbsp;${winners.map(esc).join(" ")}</p>` : ""}
        ${!failed ? `<div class="answer-row"><button class="answer-button boom" type="button" data-action="answer-boom" ${activeTurn ? "" : "disabled"} aria-label="BOOM">💣</button><button class="answer-button number" type="button" data-action="answer-number" ${activeTurn ? "" : "disabled"} aria-label="${esc(t("answerNumber"))}"></button></div>
          <p class="keyboard-note">${esc(state.speechTurn === snapshot.turnId ? t("speechListening") : speechSupported() ? t("keyboard") : t("speechUnavailableShort"))}</p>` : `<button class="primary-button play-again" type="button" data-action="play-again">${esc(t("playAgain"))}</button>`}
        <div class="stats-row"><span>${esc(t("best", { n: state.best }))}</span><span>${esc(t("lowest", { n: secondsText(state.lowest) }))}</span><span>${esc(t("average", { n: secondsText(averageMs) }))}</span>${failed ? `<span>${esc(ownRankText)}</span>` : ""}</div>
        <p id="latency" class="latency">${esc(t("latency", { n: state.latency ?? "—" }))}${state.connection === "solo" ? ` · ${esc(t("statusOffline"))}` : ""}</p>
      </section></section><footer class="footer">${esc(t("keyboard"))}</footer>`;
    updateCountdown();
    syncSpeech();
    if (rosterDialog.open) renderRoster();
  }
  function secondsText(milliseconds) { return ((Math.max(0, milliseconds) + 50) / 1000).toFixed(1); }
  function feedback(number) {
    if (!isBoom(number)) return t("feedbackNumber", { n: number });
    if (number % 7 === 0 && String(number).includes("7")) return t("feedbackBoth", { n: number });
    return t(number % 7 === 0 ? "feedbackDivisible" : "feedbackContains", { n: number });
  }
  function secondsRemaining() {
    return remainingSeconds(state.snapshot?.deadline ?? null, state.offset, Date.now());
  }
  function updateCountdown() {
    const progress = byId("turn-progress");
    if (progress && state.snapshot?.phase === "active") {
      const fraction = remainingProgress(state.snapshot.deadline, state.offset, Date.now());
      const percent = Math.round(fraction * 100);
      progress.setAttribute("aria-valuenow", String(percent));
      byId("turn-progress-fill").style.transform = `scaleX(${fraction})`;
    }
    const latency = byId("latency");
    if (latency && state.latency !== null) latency.textContent = t("latency", { n: state.latency });
  }
  function openDialog(dialog) { if (!dialog.open) dialog.showModal(); }
  function closeDialog(dialog) { if (dialog.open) dialog.close(); }

  function showNicknamePicker() {
    byId("emoji-grid").innerHTML = EMOJIS.map((emoji) => `<button class="emoji-choice${emoji === state.nickname ? " selected" : ""}" type="button" data-emoji="${emoji}" aria-label="${emoji}" aria-pressed="${emoji === state.nickname}">${emoji}</button>`).join("");
    openDialog(nicknameDialog);
  }
  function showThemePicker() {
    byId("theme-options").innerHTML = THEMES.map((theme) => {
      const unlocked = state.best >= theme.unlock;
      const selected = state.theme === theme.id;
      return `<button class="theme-option${selected ? " current" : ""}" type="button" data-theme="${theme.id}" ${unlocked ? "" : "disabled"} aria-pressed="${selected}">
        <span class="theme-left"><span class="swatch" style="--swatch:${theme.colors[2]}"></span><span>${esc(t(theme.label))}</span></span>
        <span class="theme-lock">${selected ? esc(t("selected")) : unlocked ? "" : esc(t("locked", { n: theme.unlock }))}</span></button>`;
    }).join("");
    openDialog(themeDialog);
  }
  function renderRoster() {
    byId("roster-title").textContent = t("players", { n: state.snapshot?.connectedPlayers ?? state.roster.length });
    const active = state.snapshot?.phase === "active" ? state.snapshot.activeSessionId : null;
    const players = state.roster.length ? state.roster : [{ sessionId, nickname: state.nickname }];
    byId("roster-list").innerHTML = players.map((player) => {
      const location = [countryFlag(player.countryCode), timeZoneCity(player.timeZone)].filter(Boolean).join(" ");
      const label = [player.nickname, location, player.sessionId === active ? t("yourTurn") : ""].filter(Boolean).join(" · ");
      return `<div class="roster-player${player.sessionId === active ? " active" : ""}" aria-label="${esc(label)}" title="${esc(player.timeZone || "")}"><span class="roster-emoji">${esc(player.nickname)}</span>${location ? `<span class="roster-location">${esc(location)}</span>` : ""}</div>`;
    }).join("");
    byId("roster-active-hint").hidden = active === null;
    byId("roster-active-hint").textContent = t("activeTurn");
  }
  function openRoster() { renderRoster(); openDialog(rosterDialog); }

  document.addEventListener("click", (event) => {
    const button = event.target.closest("[data-action]");
    if (!button) return;
    switch (button.dataset.action) {
      case "join": startOnline(); break;
      case "open-nickname": showNicknamePicker(); break;
      case "open-theme": showThemePicker(); break;
      case "open-roster": openRoster(); break;
      case "close-nickname": closeDialog(nicknameDialog); break;
      case "close-theme": closeDialog(themeDialog); break;
      case "close-roster": closeDialog(rosterDialog); break;
      case "continue-solo": closeDialog(offlineDialog); startSolo(); break;
      case "answer-boom": answer("boom"); break;
      case "answer-number": answer("number"); break;
      case "play-again": if (state.connection === "solo") startSolo(); else { logLocalEvent("restart_requested"); send({ type: "restart" }); } break;
    }
  });
  byId("emoji-grid").addEventListener("click", (event) => {
    const choice = event.target.closest("[data-emoji]");
    if (!choice) return;
    state.nickname = choice.dataset.emoji;
    storageSet("seven-boom-nickname-emoji", state.nickname);
    closeDialog(nicknameDialog);
    render();
  });
  byId("theme-options").addEventListener("click", (event) => {
    const choice = event.target.closest("[data-theme]");
    if (!choice || choice.disabled) return;
    state.theme = choice.dataset.theme;
    storageSet("seven-boom-theme", state.theme);
    closeDialog(themeDialog);
    render();
  });
  for (const dialog of [nicknameDialog, themeDialog, rosterDialog]) {
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) dialog.close();
    });
  }
  offlineDialog.addEventListener("cancel", (event) => event.preventDefault());
  window.addEventListener("keydown", (event) => {
    if (event.repeat || nicknameDialog.open || themeDialog.open || rosterDialog.open || offlineDialog.open) return;
    if (state.connection !== "connected" && state.connection !== "solo") return;
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      const isBoomKey = isHebrew ? event.key === "ArrowRight" : event.key === "ArrowLeft";
      answer(isBoomKey ? "boom" : "number");
    }
  });

  function startOnline() {
    if (!NICKNAMES.has(state.nickname)) { showNicknamePicker(); return; }
    logLocalEvent("join_started", { mode: "online" });
    clearTimeout(state.joinTimer);
    state.intentionalClose = false;
    state.connection = "joining";
    state.snapshot = null;
    state.roster = [];
    state.lastAnsweredTurn = null;
    render();
    const url = gameSocketUrl(location);
    try { state.socket = new WebSocket(`${url}?sessionId=${encodeURIComponent(sessionId)}&nickname=${encodeURIComponent(state.nickname)}&timeZone=${encodeURIComponent(localTimeZone())}`); }
    catch { showOffline(); return; }
    state.joinTimer = setTimeout(() => {
      if (state.connection === "joining") showOffline();
    }, 9000);
    state.socket.addEventListener("open", () => {
      if (state.connection !== "joining") return;
      logLocalEvent("connection_open");
      state.pingTimer = window.setInterval(sendPing, 2000);
    });
    state.socket.addEventListener("message", (event) => {
      let message;
      try { message = JSON.parse(event.data); } catch { return; }
      if (message.type === "snapshot") acceptSnapshot(message);
      else if (message.type === "roster") acceptRoster(message);
      else if (message.type === "pong" && message.id === state.pendingPing) {
        state.latency = Math.max(0, Date.now() - state.pingAt);
        state.pendingPing = null;
        updateCountdown();
      }
    });
    state.socket.addEventListener("error", () => {
      if (state.connection === "joining") showOffline();
    });
    state.socket.addEventListener("close", () => {
      clearTimeout(state.joinTimer);
      clearInterval(state.pingTimer);
      if (state.intentionalClose || !["joining", "connected"].includes(state.connection)) return;
      state.connection = "soloPending";
      state.snapshot = null;
      state.roster = [];
      stopSpeech();
      render();
      openDialog(offlineDialog);
    });
  }
  function showOffline() {
    if (state.connection === "soloPending") return;
    logLocalEvent("offline_prompted");
    clearTimeout(state.joinTimer);
    state.connection = "soloPending";
    stopSpeech();
    const socket = state.socket;
    state.intentionalClose = true;
    if (socket && socket.readyState < WebSocket.CLOSING) socket.close();
    state.intentionalClose = false;
    render();
    openDialog(offlineDialog);
  }
  function acceptSnapshot(next) {
    if (next.type !== "snapshot" || next.revision < (state.snapshot?.revision ?? -1)) return;
    const previous = state.snapshot;
    if (state.connection !== "connected") logLocalEvent("game_connected", { mode: "online" });
    if (previous?.connectedPlayers !== next.connectedPlayers) {
      logLocalEvent("players_count", { count: next.connectedPlayers });
    }
    if (next.phase === "gameOver" && previous?.phase !== "gameOver" && next.gameOverReason !== "noPlayers") {
      logLocalEvent("game_over", { reason: next.gameOverReason, number: next.number, players: next.connectedPlayers });
    }
    const startsAgain = previous?.phase === "gameOver" && next.phase === "active" && next.number === 1;
    const advancedOurTurn = previous?.phase === "active" && previous.activeSessionId === sessionId && next.phase === "active" && next.number === previous.number + 1;
    if (startsAgain) { state.responseTotal = 0; state.responseCount = 0; state.lastAnsweredTurn = null; }
    if (advancedOurTurn) { state.best = Math.max(state.best, previous.number); storageSet("seven-boom-best-number", state.best); }
    state.snapshot = next;
    state.connection = "connected";
    state.offset = next.serverTime - Date.now();
    clearTimeout(state.joinTimer);
    closeDialog(offlineDialog);
    render();
  }
  function acceptRoster(message) {
    if (message.type !== "roster" || message.revision < state.rosterRevision) return;
    if (!Array.isArray(message.players) || message.players.some((player) => !NICKNAMES.has(player.nickname))) return;
    state.rosterRevision = message.revision;
    state.roster = message.players;
    if (rosterDialog.open) renderRoster();
  }
  function sendPing() {
    if (state.socket?.readyState !== WebSocket.OPEN) return;
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    state.pendingPing = id;
    state.pingAt = Date.now();
    send({ type: "ping", id });
  }
  function send(message) {
    if (state.socket?.readyState === WebSocket.OPEN) state.socket.send(JSON.stringify(message));
  }
  function showSoloSnapshot(number, phase = "active", reason = null) {
    state.soloRevision += 1;
    const now = Date.now();
    state.snapshot = {
      type: "snapshot", revision: state.soloRevision, phase, number,
      turnId: phase === "active" ? `solo-${state.soloRevision}-${number}` : null,
      activeSessionId: phase === "active" ? sessionId : null, connectedPlayers: 1,
      serverTime: now, deadline: phase === "active" ? now + TURN_MS : null,
      gameOverReason: reason, failedSessionId: phase === "gameOver" ? sessionId : null,
      responseRankings: null,
    };
    if (phase === "gameOver") logLocalEvent("game_over", { reason, number, players: 1, mode: "solo" });
    state.offset = 0;
    render();
  }
  function startSolo() {
    clearTimeout(state.joinTimer);
    clearInterval(state.pingTimer);
    state.intentionalClose = true;
    try { state.socket?.close(); } catch { /* Ignore a closed socket. */ }
    state.socket = null;
    state.intentionalClose = false;
    state.connection = "solo";
    logLocalEvent("solo_started");
    state.responseTotal = 0;
    state.responseCount = 0;
    state.latency = null;
    state.lastAnsweredTurn = null;
    showSoloSnapshot(1);
  }
  function answer(kind) {
    const snapshot = state.snapshot;
    if (!snapshot || snapshot.phase !== "active" || snapshot.activeSessionId !== sessionId) return;
    if (state.connection !== "connected" && state.connection !== "solo") return;
    if (state.lastAnsweredTurn === snapshot.turnId) return;
    const now = Date.now();
    const responseTime = responseMillis(snapshot.deadline, state.offset, now);
    state.lastAnsweredTurn = snapshot.turnId;
    state.responseTotal += responseTime;
    state.responseCount += 1;
    state.lowest = state.lowest === 0 ? responseTime : Math.min(state.lowest, responseTime);
    storageSet("seven-boom-lowest-response-time-millis", state.lowest);
    const correct = kind === "boom" ? isBoom(snapshot.number) : !isBoom(snapshot.number) && kind === "number";
    logLocalEvent("answer_submitted", { kind, number: snapshot.number, correct, mode: state.connection, response_ms: responseTime });
    state.lastHeard = "";
    stopSpeech();
    if (state.connection === "solo") {
      if (correct) {
        state.best = Math.max(state.best, snapshot.number);
        storageSet("seven-boom-best-number", state.best);
        showSoloSnapshot(snapshot.number + 1);
      } else {
        showSoloSnapshot(snapshot.number, "gameOver", now >= (snapshot.deadline ?? 0) ? "timeout" : "wrongAnswer");
        playFailureSound();
      }
    } else {
      send(answerMessage(kind, snapshot.turnId, snapshot.number, responseTime));
    }
  }
  function failSoloForTimeout() {
    if (state.connection !== "solo" || state.snapshot?.phase !== "active" || secondsRemaining() > 0) return;
    const snapshot = state.snapshot;
    if (state.lastAnsweredTurn === snapshot.turnId) return;
    state.lastAnsweredTurn = snapshot.turnId;
    state.responseTotal += TURN_MS;
    state.responseCount += 1;
    state.lowest = state.lowest === 0 ? TURN_MS : Math.min(state.lowest, TURN_MS);
    storageSet("seven-boom-lowest-response-time-millis", state.lowest);
    showSoloSnapshot(snapshot.number, "gameOver", "timeout");
    playFailureSound();
  }
  function playFailureSound() {
    try {
      const Audio = window.AudioContext || window.webkitAudioContext;
      if (!Audio) return;
      const context = new Audio();
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = "sine";
      oscillator.frequency.value = 220;
      gain.gain.setValueAtTime(.08, context.currentTime);
      gain.gain.exponentialRampToValueAtTime(.001, context.currentTime + .45);
      oscillator.connect(gain); gain.connect(context.destination);
      oscillator.start(); oscillator.stop(context.currentTime + .45);
      oscillator.onended = () => context.close();
    } catch { /* Sound is optional. */ }
  }
  function speechSupported() { return Boolean(window.SpeechRecognition || window.webkitSpeechRecognition); }
  function stopSpeech() {
    state.speechTurn = null;
    if (!state.speech) return;
    state.speech.onend = null;
    try { state.speech.abort(); } catch { /* Recognition may already be stopped. */ }
    state.speech = null;
  }
  function syncSpeech() {
    const snapshot = state.snapshot;
    if (!speechSupported() || state.connection !== "connected" || snapshot?.phase !== "active" || snapshot.activeSessionId !== sessionId) {
      if (state.speech) stopSpeech();
      return;
    }
    if (state.speechTurn === snapshot.turnId) return;
    stopSpeech();
    state.speechTurn = snapshot.turnId;
    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    try {
      const recognition = new Recognition();
      state.speech = recognition;
      recognition.lang = isHebrew ? "he-IL" : (navigator.language || "en-US");
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.onresult = (event) => {
        const transcript = event.results?.[event.results.length - 1]?.[0]?.transcript;
        if (!transcript || state.snapshot?.turnId !== snapshot.turnId) return;
        state.lastHeard = transcript;
        const answerKind = recognizedAnswer(transcript, snapshot.number);
        logLocalEvent("speech_answer", { transcript, expected: snapshot.number, mapped: answerKind });
        answer(answerKind);
      };
      recognition.onend = () => {
        if (state.snapshot?.phase !== "active" || state.snapshot.activeSessionId !== sessionId || state.snapshot.turnId !== snapshot.turnId || state.connection !== "connected") return;
        setTimeout(() => {
          if (state.speechTurn === snapshot.turnId && state.connection === "connected") {
            try { recognition.start(); } catch { /* Touch input stays available. */ }
          }
        }, 300);
      };
      recognition.onerror = () => {};
      setTimeout(() => {
        if (state.speech === recognition && state.snapshot?.turnId === snapshot.turnId) {
          try { recognition.start(); } catch { /* Touch input stays available. */ }
        }
      }, 150);
    } catch { state.speechTurn = null; }
  }
  function leaveOnline() {
    if (state.connection !== "connected" || !state.socket) return;
    state.intentionalClose = true;
    send({ type: "leave" });
    state.socket.close(1000, "page hidden");
    state.socket = null;
    clearInterval(state.pingTimer);
    state.connection = "rejoin";
    state.snapshot = null;
    stopSpeech();
    render();
  }
  document.addEventListener("visibilitychange", () => { if (document.hidden) leaveOnline(); });
  window.addEventListener("pagehide", () => {
    if (state.connection === "connected") {
      state.intentionalClose = true;
      try { send({ type: "leave" }); state.socket?.close(); } catch { /* The page is closing. */ }
    }
  });
  window.setInterval(() => {
    updateCountdown();
    failSoloForTimeout();
  }, 250);

  function loadAnalyticsLater() {
    const config = globalThis.firebaseConfig;
    if (!config?.apiKey || !config?.appId || !config?.measurementId) return;
    Promise.all([
      import("https://www.gstatic.com/firebasejs/12.17.1/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/12.17.1/firebase-analytics.js"),
    ]).then(async ([appSdk, analyticsSdk]) => {
      if (await analyticsSdk.isSupported()) analyticsSdk.getAnalytics(appSdk.initializeApp(config));
    }).catch(() => {});
  }
  if ("requestIdleCallback" in window) window.requestIdleCallback(loadAnalyticsLater, { timeout: 5000 });
  else window.setTimeout(loadAnalyticsLater, 3500);

  for (const element of document.querySelectorAll("[data-copy]")) element.textContent = t(element.dataset.copy);
  for (const element of document.querySelectorAll("[data-label]")) element.setAttribute("aria-label", t(element.dataset.label));
  applyTheme();
  render();
})();
