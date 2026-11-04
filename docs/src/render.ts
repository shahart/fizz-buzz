type RendererDependencies = Record<string, any>;

export function createRenderer(d: RendererDependencies) {
  const { state, root, addTopbar, t, esc, audioCreditMarkup, speechSupported, desktopKeyboardHint, currentTheme, secondsText, feedback, sessionId, NICKNAMES, playerIndicatorsMarkup, updateCountdown, syncSpeech, rosterDialog, renderRoster } = d;
  function renderLobby() {
    document.body.dataset.outcome = "";
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
        ${desktopKeyboardHint() && speechSupported() ? `<p class="keyboard-note">${esc(t("keyboard"))}</p>` : ""}
        <button class="primary-button join-button" type="button" data-action="join">${esc(t(rejoin ? "rejoin" : "join"))}</button>
      </section></section><footer class="footer">${esc(t("personalRecords"))} · 7-Boom${audioCreditMarkup()}</footer>`;
  }
  function renderGame() {
    const snapshot = state.snapshot;
    const failed = snapshot.phase === "gameOver";
    document.body.dataset.phase = failed ? "game-over" : "active";
    const ownsTurn = !failed && snapshot.activeSessionId === sessionId;
    const activeTurn = ownsTurn && state.lastAnsweredTurn !== snapshot.turnId;
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
    const playerCount = Math.max(rankings.length, snapshot.connectedPlayers);
    const survivingRankings = rankings.filter((rank) => rank.sessionId !== snapshot.failedSessionId);
    const bestSurvivingRank = Math.min(...survivingRankings.map((rank) => rank.rank), Infinity);
    const failedPlayer = snapshot.failedSessionId;
    const won = failed && failedPlayer != null && failedPlayer !== sessionId && (
      playerCount === 2 || (playerCount >= 3 && ownRank?.rank === bestSurvivingRank)
    );
    const spectator = failed && playerCount >= 3 && failedPlayer !== sessionId && !won;
    const outcome = won ? "winner" : spectator ? "spectator" : failed ? "loser" : "";
    document.body.dataset.outcome = outcome;
    const gameOverClass = failed ? ` game-over-card game-over-${outcome}` : "";
    const averageMs = ownRank?.averageMillis ?? (state.responseCount ? state.responseTotal / state.responseCount : 0);
    const ownRankText = ownRank ? t("rank", { rank: ownRank.rank, total: rankings.length }) : t("unranked");
    const winners = failed && playerCount === 2
      ? [...new Set(rankings.filter((rank) => rank.sessionId !== failedPlayer && rank.nickname && NICKNAMES.has(rank.nickname)).map((rank) => rank.nickname))]
      : failed && Number.isFinite(bestSurvivingRank)
        ? [...new Set(survivingRankings.filter((rank) => rank.rank === bestSurvivingRank).map((rank) => rank.nickname).filter((emoji) => NICKNAMES.has(emoji)))]
        : [];
    const currentName = failed ? t("boom") : activeTurn ? t("yourTurn") : t("waiting");
    const activePlayer = state.roster.find((player) => player.sessionId === snapshot.activeSessionId);
    const yourEmoji = state.nickname;
    const currentEmoji = activePlayer?.nickname || (snapshot.activeSessionId === sessionId ? yourEmoji : "❔");
    const playerIndicators = playerIndicatorsMarkup({
      connectedPlayers: snapshot.connectedPlayers,
      currentEmoji,
      yourEmoji,
      currentLabel: t("currentPlayer"),
      youLabel: t("you"),
      active: !failed,
    });

    root.innerHTML = `${addTopbar(`<button class="player-chip" type="button" data-action="open-roster"><span aria-hidden="true">👥</span>${esc(t("players", { n: snapshot.connectedPlayers }))}</button>`)}
      <section class="stage"><section class="game-card${gameOverClass}" aria-label="${esc(currentName)}">
        ${playerIndicators}
        <div class="game-topline"><p class="turn-label">${failed ? `<span aria-hidden="true">💣</span> ${esc(currentName)}` : esc(currentName)}</p></div>
        <div class="number-orb" aria-live="off">${snapshot.number}</div>
        ${!failed ? `<div id="turn-progress" class="turn-progress" role="progressbar" aria-label="Turn time remaining" aria-valuemin="0" aria-valuemax="100" aria-valuenow="100"><span id="turn-progress-fill" class="turn-progress-fill"></span></div>` : ""}
        <p class="game-message">${esc(message || (activeTurn && state.lastHeard ? t("voiceHeard", { text: state.lastHeard }) : ""))}</p>
        ${failed && snapshot.gameOverReason === "wrongAnswer" && snapshot.failedSessionId === sessionId && state.lastHeard ? `<p class="game-message">${esc(t("voiceHeard", { text: state.lastHeard }))}</p>` : ""}
        ${clue ? `<p class="game-message">${esc(clue)}</p>` : ""}
        ${won ? `<div class="result-popup" role="alert">🏆 ${esc(t("youWon"))}</div>` : spectator ? `<div class="result-popup spectator-message" role="status">${esc(t("spectator"))}</div>` : ""}
        ${winners.length ? `<p class="winner-line">${esc(t("winner"))} &nbsp;${winners.map(esc).join(" ")}</p>` : ""}
        ${!failed ? `<div class="answer-row"><button class="answer-button boom" type="button" data-action="answer-boom" ${activeTurn ? "" : "disabled"} aria-label="BOOM">💣</button><button class="answer-button number" type="button" data-action="answer-number" ${activeTurn ? "" : "disabled"} aria-label="${esc(t("answerNumber"))}"></button></div>
          <p class="keyboard-note">${esc(state.speechTurn === snapshot.turnId ? t("speechListening") : desktopKeyboardHint() && speechSupported() ? t("keyboard") : t("speechUnavailableShort"))}</p>` : `<button class="primary-button play-again" type="button" data-action="play-again">${esc(t("playAgain"))}</button>`}
        <div class="stats-row"><span>${esc(t("best", { n: state.best }))}</span><span>${esc(t("lowest", { n: secondsText(state.lowest) }))}</span><span>${esc(t("average", { n: secondsText(averageMs) }))}</span>${failed ? `<span>${esc(ownRankText)}</span>` : ""}</div>
        <p id="latency" class="latency">${esc(t("latency", { n: state.latency ?? "—" }))}${state.connection === "solo" ? ` · ${esc(t("statusOffline"))}` : ""}</p>
      </section></section><footer class="footer">${desktopKeyboardHint() ? esc(t("keyboard")) : ""}</footer>`;
    updateCountdown();
    syncSpeech();
    if (rosterDialog.open) renderRoster();
  }
  return { renderLobby, renderGame };
}
