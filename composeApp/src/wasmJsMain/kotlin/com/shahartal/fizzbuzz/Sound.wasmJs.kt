package com.shahartal.fizzbuzz

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
actual fun playTimeoutSound() {
    playBrowserTone()
}

@OptIn(ExperimentalWasmJsInterop::class)
actual fun prepareTimeoutSound() {
    prepareBrowserAudio()
}

actual val requiresUserSoundActivation: Boolean = true

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        const context = window.__countdownAudioContext || new AudioContext();
        window.__countdownAudioContext = context;
        if (context.state === 'suspended') context.resume();
    }""",
)
private external fun prepareBrowserAudio()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        const context = window.__countdownAudioContext || new AudioContext();
        window.__countdownAudioContext = context;
        const play = () => {
            const oscillator = context.createOscillator();
            const gain = context.createGain();
            oscillator.type = 'sine';
            oscillator.frequency.value = 880;
            gain.gain.setValueAtTime(0.25, context.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, context.currentTime + 0.7);
            oscillator.connect(gain);
            gain.connect(context.destination);
            oscillator.start();
            oscillator.stop(context.currentTime + 0.7);
        };
        if (context.state === 'suspended') {
            context.resume().then(play);
        } else {
            play();
        }
    }""",
)
private external fun playBrowserTone()
