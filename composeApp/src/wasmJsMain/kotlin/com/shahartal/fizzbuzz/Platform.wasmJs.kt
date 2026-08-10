package com.shahartal.fizzbuzz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets
import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

actual fun createGameHttpClient(): HttpClient = HttpClient(Js) { install(WebSockets) }

@OptIn(ExperimentalWasmJsInterop::class)
actual fun currentTimeMillis(): Long = browserNow().toLong()

@OptIn(ExperimentalWasmJsInterop::class)
actual fun anonymousSessionId(): String = browserUuid()

@OptIn(ExperimentalWasmJsInterop::class)
actual fun gameWebSocketUrl(sessionId: String): String = browserGameUrl(sessionId)

@Composable
actual fun rememberBestNumberStore(): BestNumberStore = remember {
    object : BestNumberStore {
        override fun load(): Int = browserLoadBestNumber().toInt().coerceAtLeast(0)

        override fun save(value: Int) {
            browserSaveBestNumber(value.toDouble())
        }

        override fun loadHighestResponseTimeMillis(): Long =
            browserLoadHighestResponseTimeMillis().toLong().coerceAtLeast(0L)

        override fun saveHighestResponseTimeMillis(value: Long) {
            browserSaveHighestResponseTimeMillis(value.toDouble())
        }
    }
}

@Composable
actual fun ObserveGameLifecycle(onStopped: () -> Unit, onResumed: () -> Unit) {
    DisposableEffect(onStopped, onResumed) {
        val cleanup = observeVisibility(onStopped, onResumed)
        onDispose(cleanup)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now()")
private external fun browserNow(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => crypto.randomUUID()")
private external fun browserUuid(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    try {
        const value = Number(localStorage.getItem('seven-boom-best-number'));
        return Number.isSafeInteger(value) && value >= 0 ? value : 0;
    } catch (_) {
        return 0;
    }
}""")
private external fun browserLoadBestNumber(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""value => {
    try {
        const previous = Number(localStorage.getItem('seven-boom-best-number')) || 0;
        if (Number.isSafeInteger(value) && value > previous) {
            localStorage.setItem('seven-boom-best-number', String(value));
        }
    } catch (_) {}
}""")
private external fun browserSaveBestNumber(value: Double)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    try {
        const value = Number(localStorage.getItem('seven-boom-highest-response-time-millis'));
        return Number.isSafeInteger(value) && value >= 0 ? value : 0;
    } catch (_) {
        return 0;
    }
}""")
private external fun browserLoadHighestResponseTimeMillis(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""value => {
    try {
        const key = 'seven-boom-highest-response-time-millis';
        const previous = Number(localStorage.getItem(key)) || 0;
        if (Number.isSafeInteger(value) && value > previous) localStorage.setItem(key, String(value));
    } catch (_) {}
}""")
private external fun browserSaveHighestResponseTimeMillis(value: Double)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""sessionId => {
    const isLocalHost = location.hostname === 'localhost' ||
        location.hostname === '127.0.0.1' ||
        location.hostname === '::1';
    const isWebpackDevelopmentServer = isLocalHost && location.port !== '8787';
    const endpoint = isWebpackDevelopmentServer
        ? 'wss://global-seven-boom.lat-shahar.workers.dev/game'
        : (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/game';
    return endpoint + '?sessionId=' + encodeURIComponent(sessionId);
}""")
private external fun browserGameUrl(sessionId: String): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(hidden, visible) => {
    let wasHidden = document.hidden;
    const change = () => {
        if (document.hidden && !wasHidden) hidden();
        if (!document.hidden && wasHidden) visible();
        wasHidden = document.hidden;
    };
    const exit = () => hidden();
    document.addEventListener('visibilitychange', change);
    window.addEventListener('pagehide', exit);
    return () => {
        document.removeEventListener('visibilitychange', change);
        window.removeEventListener('pagehide', exit);
    };
}""")
private external fun observeVisibility(hidden: () -> Unit, visible: () -> Unit): () -> Unit
