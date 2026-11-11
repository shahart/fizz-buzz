import { EMOJIS, TURN_MS, answerMessage, gameSocketUrl, isBoom, isWinnerForSnapshot, playerIndicatorsMarkup, recognizedAnswer, remainingProgress, remainingSeconds, responseMillis } from "../game-core.mjs";
import { COPY } from "./copy.js";
import { createRenderer } from "./render.js";
import { bindUiEvents } from "./events.js";

(() => {
  "use strict";

  if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
      navigator.serviceWorker.register("./service-worker.js").catch((error) => {
        console.warn("Offline support could not be enabled", error);
      });
    });
  }

  const THEMES = [
    { id: "classic", unlock: 0, label: "classic", colors: ["#102a43", "#eaf4ff", "#1473e6", "#d92d20"] },
    { id: "mint", unlock: 14, label: "mint", colors: ["#124b42", "#e3f7ee", "#168a68", "#cb3f54"] },
    { id: "sunset", unlock: 49, label: "sunset", colors: ["#572b24", "#fff0de", "#e56a35", "#b52f45"] },
    { id: "midnight", unlock: 77, label: "midnight", colors: ["#e7ecff", "#171b34", "#8798ff", "#ff667a"] },
  ];
  const NICKNAMES = new Set(EMOJIS);


  const requestedLanguage = new URLSearchParams(window.location.search).get("hl");
  const locale = (navigator.language || "en").toLowerCase();
  const isHebrew = requestedLanguage === "he" || (requestedLanguage !== "en" && locale.startsWith("he"));
  const lang = isHebrew ? "he" : "en";
  const strings = COPY[lang];
  document.documentElement.lang = lang;
  document.documentElement.dir = isHebrew ? "rtl" : "ltr";
  document.title = (isHebrew ? "שבע בום" : "7-Boom") + " v1.13";

  const root = document.getElementById("app")!;
  const byId = (id) => document.getElementById(id);
  const nicknameDialog = byId("nickname-dialog") as HTMLDialogElement;
  const themeDialog = byId("theme-dialog") as HTMLDialogElement;
  const rosterDialog = byId("roster-dialog") as HTMLDialogElement;
  const offlineDialog = byId("offline-dialog") as HTMLDialogElement;
  const firefoxSpeechDialog = byId("firefox-speech-dialog") as HTMLDialogElement;
  const sessionId = makeSessionId();
  const state: any = {
    connection: "idle", socket: null, snapshot: null, roster: [], rosterRevision: -1,
    networkOnline: navigator.onLine !== false,
    offset: 0, nickname: storageGet("seven-boom-nickname-emoji", "😀"),
    soundMuted: storageGet("seven-boom-sound-muted", "false") === "true",
    theme: storageGet("seven-boom-theme", "classic"), best: numberGet("seven-boom-best-number"),
    lowest: numberGet("seven-boom-lowest-response-time-millis"), responseTotal: 0, responseCount: 0,
    latency: null, pendingPing: null, pingAt: 0, pingTimer: 0, joinTimer: 0,
    lastAnsweredTurn: null, intentionalClose: false, restartAfterConnect: false, speech: null, speechTurn: null,
    lastHeard: "", timerInterval: 0, soloRevision: 0, failureFlash: false, nicknameError: null,
    networkNoticeTimer: 0,
  };
  function logLocalEvent(event, details = {}) {
    console.info("[SevenBoom]", event, details);
  }
  if (!NICKNAMES.has(state.nickname)) state.nickname = "😀";
  if (!THEMES.some((theme) => theme.id === state.theme)) state.theme = "classic";

  function t(key, values = {}) {
    return (strings[key] || key).replace(/\{(\w+)\}/g, (_, name) => String(values[name] ?? ""));
  }
  const networkNotice = document.createElement("div");
  networkNotice.className = "network-notice";
  networkNotice.setAttribute("role", "status");
  networkNotice.setAttribute("aria-live", "polite");
  networkNotice.hidden = true;
  document.body.append(networkNotice);
  function showNetworkNotice(online) {
    showNotice(t(online ? "connectionRestoredNotice" : "connectionLostNotice"), online ? "online" : "offline");
  }
  function showPlayerCountNotice(count) {
    showNotice(t("playerCountChanged", { n: count }), "offline");
  }
  function showNotice(message, tone) {
    networkNotice.textContent = message;
    networkNotice.classList.toggle("online", tone === "online");
    networkNotice.classList.toggle("offline", tone === "offline");
    networkNotice.hidden = false;
    window.clearTimeout(state.networkNoticeTimer);
    state.networkNoticeTimer = window.setTimeout(() => { networkNotice.hidden = true; }, 3000);
  }
  function esc(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  }
  function audioCreditMarkup() {
    return `<span class="music-credit">${esc(t("musicCreditPrefix"))} <a href="https://pixabay.com/music/main-title-cinematic-tension-suspenseful-thriller-music-loop-297627/" target="_blank" rel="noopener noreferrer">${esc(t("musicTrackTitle"))}</a> · ${esc(t("musicArtistCredit"))} · <a href="https://pixabay.com/sound-effects/film-special-effects-wrong-buzzer-6268/" target="_blank" rel="noopener noreferrer">${esc(t("failureSoundTitle"))}</a> · ${esc(t("failureSoundArtist"))} · <a href="https://pixabay.com/sound-effects/cute-happy-victory-476376/" target="_blank" rel="noopener noreferrer">${esc(t("winnerSoundTitle"))}</a> · ${esc(t("winnerSoundArtist"))} · <a href="https://pixabay.com/service/license-summary/" target="_blank" rel="noopener noreferrer">${esc(t("pixabayLicense"))}</a></span>`;
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
  function desktopKeyboardHint() { return window.matchMedia("(min-width: 768px)").matches; }
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
    const showNetworkStatus = ["connected", "solo", "soloPending"].includes(state.connection);
    const networkLabel = state.networkOnline ? t("statusOnline") : t("statusOffline");
    return `<header class="topbar">
      <a class="brand" href="./" aria-label="${esc(t("brand"))}"><span class="brand-mark" aria-hidden="true">7</span><span>${esc(t("brand"))}</span></a>
      <div class="topbar-actions"><button class="icon-button sound-toggle" type="button" data-action="toggle-mute" aria-label="${esc(t(state.soundMuted ? "unmuteSound" : "muteSound"))}" title="${esc(t(state.soundMuted ? "unmuteSound" : "muteSound"))}">${state.soundMuted ? "🔇" : "🔊"}</button>${extra}<span class="top-chip"><span class="connection-dot ${showNetworkStatus ? (state.networkOnline ? "online" : "offline") : ""}"></span>${showNetworkStatus ? esc(networkLabel) : "7-BOOM"}</span></div>
    </header>`;
  }
  function updateNetworkStatus() {
    const networkOnline = navigator.onLine !== false;
    if (state.networkOnline === networkOnline) return;
    state.networkOnline = networkOnline;
    logLocalEvent("connectivity_changed", { status: networkOnline ? "online" : "offline" });
    showNetworkNotice(networkOnline);
    if (!networkOnline && state.connection === "connected" && state.snapshot?.phase === "active") {
      startSolo();
      if (rosterDialog.open) closeDialog(rosterDialog);
      return;
    }
    render();
  }
  window.addEventListener("online", updateNetworkStatus);
  window.addEventListener("offline", updateNetworkStatus);
  window.addEventListener("focus", updateNetworkStatus);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) updateNetworkStatus();
  });
  function render() {
    applyTheme();
    if (state.connection === "joining") {
      root.innerHTML = `${addTopbar()}<section class="stage"><div class="game-card loading-card"><div class="loader" aria-hidden="true"></div><p class="eyebrow">${esc(t("connection"))}</p><h1 class="loading-title">${esc(t("joining"))}</h1><p class="subhead">${esc(t("joiningHint"))}</p></div></section><footer class="footer">7-Boom · ${esc(state.nickname)}</footer>`;
      return;
    }
    if ((state.connection === "connected" || state.connection === "solo") && state.snapshot) {
      renderer.renderGame();
      return;
    }
    renderer.renderLobby();
  }
  const renderer = createRenderer({ state, root, addTopbar, t, esc, audioCreditMarkup, speechSupported, desktopKeyboardHint, currentTheme, secondsText, feedback, sessionId, NICKNAMES, playerIndicatorsMarkup, updateCountdown, syncSpeech, rosterDialog, renderRoster });
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
    byId("emoji-error").textContent = state.nicknameError || "";
    byId("emoji-error").hidden = !state.nicknameError;
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

  bindUiEvents({ byId, storageSet, render, syncSoundMute, closeDialog, nicknameDialog, themeDialog, rosterDialog, offlineDialog, firefoxSpeechDialog, state, startOnline, showNicknamePicker, showThemePicker, openRoster, startSolo, answer, logLocalEvent, send, isHebrew, t, sessionId });
  function startOnline() {
    if (!NICKNAMES.has(state.nickname)) { showNicknamePicker(); return; }
    logLocalEvent("join_started", { mode: "online" });
    prepareCountdownMusic();
    prepareFailureSound();
    prepareWinnerSound();
    clearTimeout(state.joinTimer);
    clearInterval(state.pingTimer);
    state.intentionalClose = false;
    state.connection = "joining";
    state.snapshot = null;
    state.roster = [];
    state.lastAnsweredTurn = null;
    render();
    if (!state.networkOnline) {
      showOffline();
      return;
    }
    const url = gameSocketUrl(location);
    let socket;
    try { socket = state.socket = new WebSocket(`${url}?sessionId=${encodeURIComponent(sessionId)}&nickname=${encodeURIComponent(state.nickname)}&timeZone=${encodeURIComponent(localTimeZone())}`); }
    catch { showOffline(); return; }
    state.joinTimer = setTimeout(() => {
      if (state.connection === "joining") showOffline();
    }, 9000);
    socket.addEventListener("open", () => {
      if (state.socket !== socket || state.connection !== "joining") return;
      logLocalEvent("connection_open");
      state.pingTimer = window.setInterval(sendPing, 2000);
    });
    socket.addEventListener("message", (event) => {
      if (state.socket !== socket) return;
      let message;
      try { message = JSON.parse(event.data); } catch { return; }
      if (message.type === "snapshot") acceptSnapshot(message);
      else if (message.type === "roster") acceptRoster(message);
      else if (message.type === "error" && message.code === "nicknameTaken") {
        clearTimeout(state.joinTimer);
        clearInterval(state.pingTimer);
        state.connection = "idle";
        state.snapshot = null;
        state.roster = [];
        state.nicknameError = t("emojiAlreadyChosen");
        render();
        showNicknamePicker();
      }
      else if (message.type === "pong" && message.id === state.pendingPing) {
        state.latency = Math.max(0, Date.now() - state.pingAt);
        state.pendingPing = null;
        updateCountdown();
      }
    });
    socket.addEventListener("error", () => {
      if (state.socket !== socket) return;
      if (state.connection === "joining") showOffline();
    });
    socket.addEventListener("close", () => {
      clearTimeout(state.joinTimer);
      clearInterval(state.pingTimer);
      if (state.socket !== socket) return;
      if (state.intentionalClose || !["joining", "connected"].includes(state.connection)) return;
      if (state.connection === "connected" && state.snapshot?.phase === "active") {
        if (rosterDialog.open) closeDialog(rosterDialog);
        startSolo();
        return;
      }
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
      if (previous ? previous.connectedPlayers !== next.connectedPlayers : next.connectedPlayers > 1) {
        showPlayerCountNotice(next.connectedPlayers);
      }
    }
    if (next.phase === "gameOver" && previous?.phase !== "gameOver" && next.gameOverReason !== "noPlayers") {
      logLocalEvent("game_over", { reason: next.gameOverReason, number: next.number, players: next.connectedPlayers });
      if (next.failedSessionId === sessionId) playFailureSound();
      else if (isWinnerForSnapshot(next, sessionId)) playWinnerSound();
    }
    const startsAgain = previous?.phase === "gameOver" && next.phase === "active" && next.number === 1;
    if (state.restartAfterConnect) {
      state.restartAfterConnect = false;
      if (next.phase === "gameOver") send({ type: "restart" });
    }
    const advancedOurTurn = previous?.phase === "active" && previous.activeSessionId === sessionId && next.phase === "active" && next.number === previous.number + 1;
    if (startsAgain) { state.responseTotal = 0; state.responseCount = 0; state.lastAnsweredTurn = null; }
    if (advancedOurTurn) { state.best = Math.max(state.best, previous.number); storageSet("seven-boom-best-number", state.best); }
    state.snapshot = next;
    state.connection = "connected";
    state.offset = next.serverTime - Date.now();
    syncCountdownMusic();
    clearTimeout(state.joinTimer);
    closeDialog(offlineDialog);
    render();
  }
  function acceptRoster(message) {
    if (message.type !== "roster" || message.revision < state.rosterRevision) return;
    if (!Array.isArray(message.players) || message.players.some((player) => !NICKNAMES.has(player.nickname))) return;
    state.rosterRevision = message.revision;
    state.roster = message.players;
    if (state.snapshot && state.connection === "connected") render();
    else if (rosterDialog.open) renderRoster();
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
    syncCountdownMusic();
    render();
  }
  function startSolo() {
    const startingNumber = state.snapshot?.phase === "active" ? state.snapshot.number : 1;
    state.restartAfterConnect = false;
    prepareCountdownMusic();
    prepareFailureSound();
    prepareWinnerSound();
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
    showSoloSnapshot(startingNumber);
  }
  function answer(kind, heardTranscript = null) {
    const snapshot = state.snapshot;
    if (!snapshot || snapshot.phase !== "active" || snapshot.activeSessionId !== sessionId) return;
    if (state.connection !== "connected" && state.connection !== "solo") return;
    if (state.lastAnsweredTurn === snapshot.turnId) return;
    const now = Date.now();
    const responseTime = responseMillis(snapshot.deadline, state.offset, now);
    state.lastAnsweredTurn = snapshot.turnId;
    syncCountdownMusic();
    state.responseTotal += responseTime;
    state.responseCount += 1;
    state.lowest = state.lowest === 0 ? responseTime : Math.min(state.lowest, responseTime);
    storageSet("seven-boom-lowest-response-time-millis", state.lowest);
    const correct = kind === "boom" ? isBoom(snapshot.number) : !isBoom(snapshot.number) && kind === "number";
    logLocalEvent("answer_submitted", { kind, number: snapshot.number, correct, mode: state.connection, response_ms: responseTime });
    state.lastHeard = heardTranscript ?? "";
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
    if (state.soundMuted) return;
    failureSound.currentTime = 0;
    failureSound.play().catch(() => {});
  }
  function playWinnerSound() {
    if (state.soundMuted) return;
    winnerSound.currentTime = 0;
    winnerSound.play().catch(() => {});
  }
  function syncSoundMute() {
    syncCountdownMusic();
    if (state.soundMuted) {
      failureSound.pause();
      failureSound.currentTime = 0;
      winnerSound.pause();
      winnerSound.currentTime = 0;
    }
  }
  const failureSound = new Audio("./wrong-buzzer-6268.mp3");
  failureSound.volume = 0.4;
  function prepareFailureSound() {
    const attempt = failureSound.play();
    failureSound.pause();
    if (attempt) attempt.catch(() => {});
  }
  const winnerSound = new Audio("./u_o8xh7gwsrj-cute_happy_victory-476376.mp3");
  winnerSound.volume = 0.4;
  function prepareWinnerSound() {
    const attempt = winnerSound.play();
    winnerSound.pause();
    if (attempt) attempt.catch(() => {});
  }
  const countdownMusic = new Audio("./cyberwave-orchestra-cinematic-tension-suspenseful-thriller-music-loop-297627.mp3");
  countdownMusic.loop = true;
  countdownMusic.volume = 0.22;
  function prepareCountdownMusic() {
    const attempt = countdownMusic.play();
    countdownMusic.pause();
    if (attempt) attempt.catch(() => {});
  }
  function syncCountdownMusic() {
    const snapshot = state.snapshot;
    const activeGame = snapshot?.phase === "active";
    const ownsActiveTurn = activeGame && snapshot.activeSessionId === sessionId;
    if (activeGame && !state.soundMuted) {
      countdownMusic.volume = ownsActiveTurn ? 0.05 : 0.05;
      countdownMusic.play().catch(() => {});
    }
    else {
      countdownMusic.pause();
    }
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
      const preferredLanguage = isHebrew ? "he-IL" : "en-US";
      const alternateLanguage = preferredLanguage === "he-IL" ? "en-US" : "he-IL";
      let recognitionLanguage = preferredLanguage;
      recognition.lang = recognitionLanguage;
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      recognition.onresult = (event) => {
        const transcript = event.results?.[event.results.length - 1]?.[0]?.transcript;
        if (!transcript || state.snapshot?.turnId !== snapshot.turnId) return;
        state.lastHeard = transcript;
        const answerKind = recognizedAnswer(transcript, snapshot.number);
        logLocalEvent("speech_answer", { transcript, expected: snapshot.number, mapped: answerKind });
        answer(answerKind, transcript);
      };
      recognition.onend = () => {
        if (state.snapshot?.phase !== "active" || state.snapshot.activeSessionId !== sessionId || state.snapshot.turnId !== snapshot.turnId || state.connection !== "connected") return;
        setTimeout(() => {
          if (state.speechTurn === snapshot.turnId && state.connection === "connected") {
            recognitionLanguage = recognitionLanguage === preferredLanguage ? alternateLanguage : preferredLanguage;
            recognition.lang = recognitionLanguage;
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
    syncCountdownMusic();
    render();
  }
  document.addEventListener("visibilitychange", () => { if (document.hidden) leaveOnline(); });
  window.addEventListener("pagehide", () => {
    countdownMusic.pause();
    if (state.connection === "connected") {
      state.intentionalClose = true;
      try { send({ type: "leave" }); state.socket?.close(); } catch { /* The page is closing. */ }
    }
  });
  window.setInterval(() => {
    updateCountdown();
    syncCountdownMusic();
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
  if (typeof window.requestIdleCallback === "function") window.requestIdleCallback(loadAnalyticsLater, { timeout: 5000 });
  else window.setTimeout(loadAnalyticsLater, 3500);

  for (const element of document.querySelectorAll<HTMLElement>("[data-copy]")) element.textContent = t(element.dataset.copy);
  for (const element of document.querySelectorAll<HTMLElement>("[data-label]")) element.setAttribute("aria-label", t(element.dataset.label));
  if (/Firefox|FxiOS/i.test(navigator.userAgent)) openDialog(firefoxSpeechDialog);
  applyTheme();
  render();
})();
