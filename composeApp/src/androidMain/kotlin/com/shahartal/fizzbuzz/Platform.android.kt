package com.shahartal.fizzbuzz

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import java.util.UUID

actual fun createGameHttpClient(): HttpClient = HttpClient(OkHttp) { install(WebSockets) }
actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun anonymousSessionId(): String = UUID.randomUUID().toString()
actual fun gameWebSocketUrl(sessionId: String, nickname: String): String =
    Uri.parse(BuildConfig.GAME_WORKER_URL).buildUpon()
        .appendQueryParameter("sessionId", sessionId)
        .appendQueryParameter("nickname", nickname)
        .build()
        .toString()

@Composable
actual fun rememberBestNumberStore(): BestNumberStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val preferences = context.getSharedPreferences("seven_boom_player", 0)
        object : BestNumberStore {
            override fun load(): Int = preferences.getInt("best_number", 0).coerceAtLeast(0)

            override fun save(value: Int) {
                if (value > load()) preferences.edit().putInt("best_number", value).apply()
            }

            override fun loadHighestResponseTimeMillis(): Long =
                preferences.getLong("highest_response_time_millis", 0L).coerceAtLeast(0L)

            override fun saveHighestResponseTimeMillis(value: Long) {
                if (value > loadHighestResponseTimeMillis()) {
                    preferences.edit().putLong("highest_response_time_millis", value).apply()
                }
            }

            override fun loadNickname(): String? =
                preferences.getString("nickname_emoji", null)?.takeIf(::isEmojiNickname)

            override fun saveNickname(value: String) {
                if (isEmojiNickname(value)) preferences.edit().putString("nickname_emoji", value).apply()
            }
        }
    }
}

@Composable
actual fun ObserveGameLifecycle(onStopped: () -> Unit, onResumed: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, onStopped, onResumed) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onStopped()
                Lifecycle.Event.ON_RESUME -> onResumed()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
