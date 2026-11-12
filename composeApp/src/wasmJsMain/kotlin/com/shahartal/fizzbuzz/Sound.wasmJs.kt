package com.shahartal.fizzbuzz

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
actual fun playFailureSound() {
    playBrowserFailureSound()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun stopFailureSound() {
    stopBrowserFailureSound()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun prepareFailureSound() {
    prepareBrowserFailureSound()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun playWinnerSound() {
    playBrowserWinnerSound()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun stopWinnerSound() {
    stopBrowserWinnerSound()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun prepareWinnerSound() {
    prepareBrowserWinnerSound()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun playCountdownMusic(isCurrentPlayerTurn: Boolean) {
    playBrowserCountdownMusic(isCurrentPlayerTurn)
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun stopCountdownMusic() {
    stopBrowserCountdownMusic()
}

actual val requiresUserSoundActivation: Boolean = true

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """isCurrentPlayerTurn => {
        const audio = window.__countdownMusic || new Audio('./cyberwave-orchestra-cinematic-tension-suspenseful-thriller-music-loop-297627.mp3');
        window.__countdownMusic = audio;
        audio.loop = true;
        audio.volume = isCurrentPlayerTurn ? 0.05 : 0.05;
        audio.play().catch(() => {});
    }""",
)
private external fun playBrowserCountdownMusic(isCurrentPlayerTurn: Boolean)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const audio = window.__countdownMusic;
        if (audio) audio.pause();
    }""",
)
private external fun stopBrowserCountdownMusic()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const audio = window.__failureSound || new Audio('./wrong-buzzer-6268.mp3');
        window.__failureSound = audio;
        audio.muted = true;
        const attempt = audio.play();
        audio.pause();
        audio.muted = false;
        if (attempt) attempt.catch(() => {});
    }""",
)
private external fun prepareBrowserFailureSound()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const audio = window.__failureSound || new Audio('./wrong-buzzer-6268.mp3');
        window.__failureSound = audio;
        audio.currentTime = 0;
        audio.volume = 0.4;
        audio.play().catch(() => {});
    }""",
)
private external fun playBrowserFailureSound()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => { const audio = window.__failureSound; if (audio) { audio.pause(); audio.currentTime = 0; } }""")
private external fun stopBrowserFailureSound()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const audio = window.__winnerSound || new Audio('./u_o8xh7gwsrj-cute_happy_victory-476376.mp3');
        window.__winnerSound = audio;
        audio.muted = true;
        const attempt = audio.play();
        audio.pause();
        audio.muted = false;
        if (attempt) attempt.catch(() => {});
    }""",
)
private external fun prepareBrowserWinnerSound()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const audio = window.__winnerSound || new Audio('./u_o8xh7gwsrj-cute_happy_victory-476376.mp3');
        window.__winnerSound = audio;
        audio.currentTime = 0;
        audio.volume = 0.4;
        audio.play().catch(() => {});
    }""",
)
private external fun playBrowserWinnerSound()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => { const audio = window.__winnerSound; if (audio) { audio.pause(); audio.currentTime = 0; } }""")
private external fun stopBrowserWinnerSound()
