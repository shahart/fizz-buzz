export function bindUiEvents(d: Record<string, any>) {
  const { byId, storageSet, render, syncSoundMute, closeDialog, nicknameDialog, themeDialog, rosterDialog, offlineDialog, state, startOnline, showNicknamePicker, showThemePicker, openRoster, startSolo, answer, logLocalEvent, send, isHebrew, t, sessionId } = d;
  document.addEventListener("click", (event) => {
    const button = (event.target as Element).closest<HTMLElement>("[data-action]");
    if (!button) return;
    switch (button.dataset.action) {
      case "join": startOnline(); break;
      case "toggle-mute":
        state.soundMuted = !state.soundMuted;
        storageSet("seven-boom-sound-muted", state.soundMuted);
        syncSoundMute();
        render();
        break;
      case "open-nickname": showNicknamePicker(); break;
      case "open-theme": showThemePicker(); break;
      case "open-roster": openRoster(); break;
      case "close-nickname": closeDialog(nicknameDialog); break;
      case "close-theme": closeDialog(themeDialog); break;
      case "close-roster": closeDialog(rosterDialog); break;
      case "continue-solo": closeDialog(offlineDialog); startSolo(); break;
      case "answer-boom": answer("boom"); break;
      case "answer-number": answer("number"); break;
      case "play-again":
        if (state.connection === "solo") startSolo();
        else {
          logLocalEvent("restart_requested");
          state.restartAfterConnect = true;
          startOnline();
        }
        break;
    }
  });
  byId("emoji-grid").addEventListener("click", (event) => {
    const choice = (event.target as Element).closest<HTMLElement>("[data-emoji]");
    if (!choice) return;
    const duplicate = state.roster.some((player) => player.sessionId !== sessionId && player.nickname === choice.dataset.emoji);
    if (duplicate) {
      state.nicknameError = t("emojiAlreadyChosen");
      byId("emoji-error").textContent = state.nicknameError;
      byId("emoji-error").hidden = false;
      return;
    }
    state.nicknameError = null;
    byId("emoji-error").hidden = true;
    state.nickname = choice.dataset.emoji;
    storageSet("seven-boom-nickname-emoji", state.nickname);
    closeDialog(nicknameDialog);
    render();
  });
  byId("theme-options").addEventListener("click", (event) => {
    const choice = (event.target as Element).closest<HTMLButtonElement>("[data-theme]");
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

}
