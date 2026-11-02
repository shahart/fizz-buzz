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
actual fun gameWebSocketUrl(sessionId: String, nickname: String): String = browserGameUrl(sessionId, nickname)
actual fun logGameEvent(event: String, details: String) {
    println("[SevenBoom] $event $details")
}

actual val supportsWebKeyboardControls: Boolean = true

@Composable
actual fun ObserveWebKeyboardControls(enabled: Boolean, onLeft: () -> Unit, onRight: () -> Unit) {
    DisposableEffect(enabled, onLeft, onRight) {
        val cleanup = if (enabled) observeKeyboard(onLeft, onRight) else null
        onDispose { cleanup?.invoke() }
    }
}

@Composable
actual fun rememberBestNumberStore(): BestNumberStore = remember {
    object : BestNumberStore {
        override fun load(): Int = browserLoadBestNumber().toInt().coerceAtLeast(0)

        override fun save(value: Int) {
            browserSaveBestNumber(value.toDouble())
        }

        override fun loadLowestResponseTimeMillis(): Long =
            browserLoadLowestResponseTimeMillis().toLong().coerceAtLeast(0L)

        override fun saveLowestResponseTimeMillis(value: Long) {
            browserSaveLowestResponseTimeMillis(value.toDouble())
        }

        override fun loadNickname(): String? = browserLoadNickname().takeIf(::isEmojiNickname)

        override fun saveNickname(value: String) {
            if (isEmojiNickname(value)) browserSaveNickname(value)
        }

        override fun loadTheme(): String = browserLoadTheme()

        override fun saveTheme(value: String) {
            if (value in setOf("classic", "mint", "sunset", "midnight")) browserSaveTheme(value)
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
        const value = Number(localStorage.getItem('seven-boom-lowest-response-time-millis'));
        return Number.isSafeInteger(value) && value >= 0 ? value : 0;
    } catch (_) {
        return 0;
    }
}""")
private external fun browserLoadLowestResponseTimeMillis(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""value => {
    try {
        const key = 'seven-boom-lowest-response-time-millis';
        const previous = Number(localStorage.getItem(key)) || 0;
        if (Number.isSafeInteger(value) && value >= 0 && (previous === 0 || value < previous)) {
            localStorage.setItem(key, String(value));
        }
    } catch (_) {}
}""")
private external fun browserSaveLowestResponseTimeMillis(value: Double)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    try {
        return localStorage.getItem('seven-boom-nickname-emoji') || '';
    } catch (_) {
        return '';
    }
}""")
private external fun browserLoadNickname(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""value => {
    try { localStorage.setItem('seven-boom-nickname-emoji', value); } catch (_) {}
}""")
private external fun browserSaveNickname(value: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""() => {
    try {
        const value = localStorage.getItem('seven-boom-theme');
        return ['classic', 'mint', 'sunset', 'midnight'].includes(value) ? value : 'classic';
    } catch (_) { return 'classic'; }
}""")
private external fun browserLoadTheme(): String

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""value => { try { localStorage.setItem('seven-boom-theme', value); } catch (_) {} }""")
private external fun browserSaveTheme(value: String)

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(sessionId, nickname) => {
    const isLocalHost = location.hostname === 'localhost' ||
        location.hostname === '127.0.0.1' ||
        location.hostname === '::1';
    const isWebpackDevelopmentServer = isLocalHost && location.port !== '8787';
    const endpoint = isWebpackDevelopmentServer
        ? 'wss://global-seven-boom.lat-shahar.workers.dev/game'
        : (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/game';
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
    return endpoint + '?sessionId=' + encodeURIComponent(sessionId) +
        '&nickname=' + encodeURIComponent(nickname) +
        '&timeZone=' + encodeURIComponent(timeZone);
}""")
private external fun browserGameUrl(sessionId: String, nickname: String): String

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

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("""(left, right) => {
    const onKeyDown = event => {
        if (event.repeat) return;
        if (event.key === 'ArrowLeft') {
            event.preventDefault();
            left();
        } else if (event.key === 'ArrowRight') {
            event.preventDefault();
            right();
        }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
}""")
private external fun observeKeyboard(left: () -> Unit, right: () -> Unit): () -> Unit
