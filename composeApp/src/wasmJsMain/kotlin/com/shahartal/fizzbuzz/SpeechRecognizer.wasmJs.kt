package com.shahartal.fizzbuzz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

private class BrowserSpeechRecognizerController : SpeechRecognizerController {
    override val isSupported: Boolean = browserSpeechRecognitionSupported()
    override val hasDetectedSpeech: Boolean
        get() = browserHasDetectedSpeech()

    override fun startListening() = startBrowserSpeechRecognition()

    override fun stopListening() = stopBrowserSpeechRecognition()

    override fun consumePartialTranscript(): String? =
        consumeBrowserSpeechPartial()?.takeIf { it.isNotBlank() }

    override fun consumeResults(): List<String>? =
        consumeBrowserSpeechResult()?.let(::listOf)
}

@Composable
actual fun rememberSpeechRecognizerController(): SpeechRecognizerController {
    val controller = remember { BrowserSpeechRecognizerController() }
    DisposableEffect(controller) { onDispose(controller::stopListening) }
    return controller
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Boolean(window.SpeechRecognition || window.webkitSpeechRecognition)")
private external fun browserSpeechRecognitionSupported(): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognition) return;
        if (window.__countdownRecognition) {
            window.__countdownRecognitionActive = false;
            window.__countdownRecognition.abort();
        }
        window.__countdownSpeechResult = null;
        window.__countdownSpeechPartial = null;
        window.__countdownSpeechDetected = false;
        window.__countdownRecognitionActive = true;
        const recognition = new SpeechRecognition();
        window.__countdownRecognition = recognition;
        recognition.lang = navigator.language || 'en-US';
        recognition.continuous = false;
        recognition.interimResults = true;
        recognition.maxAlternatives = 1;
        recognition.onspeechstart = () => { window.__countdownSpeechDetected = true; };
        recognition.onresult = event => {
            const result = event.results[event.results.length - 1];
            if (result.isFinal) {
                window.__countdownSpeechResult = result[0].transcript;
                window.__countdownRecognitionActive = false;
            } else {
                window.__countdownSpeechPartial = result[0].transcript;
            }
        };
        recognition.onerror = event => {
            if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
                window.__countdownRecognitionActive = false;
            }
        };
        recognition.onend = () => {
            if (window.__countdownRecognitionActive && !window.__countdownSpeechResult) {
                try { recognition.start(); } catch (_) {}
            }
        };
        try { recognition.start(); } catch (_) {}
    }""",
)
private external fun startBrowserSpeechRecognition()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        window.__countdownRecognitionActive = false;
        if (window.__countdownRecognition) {
            try { window.__countdownRecognition.abort(); } catch (_) {}
            window.__countdownRecognition = null;
        }
    }""",
)
private external fun stopBrowserSpeechRecognition()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const partial = window.__countdownSpeechPartial;
        window.__countdownSpeechPartial = null;
        return partial == null ? null : String(partial);
    }""",
)
private external fun consumeBrowserSpeechPartial(): String?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        const result = window.__countdownSpeechResult;
        window.__countdownSpeechResult = null;
        return result == null ? null : String(result);
    }""",
)
private external fun consumeBrowserSpeechResult(): String?

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Boolean(window.__countdownSpeechDetected)")
private external fun browserHasDetectedSpeech(): Boolean
