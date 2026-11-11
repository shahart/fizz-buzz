package com.shahartal.fizzbuzz

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient

expect fun createGameHttpClient(): HttpClient
expect fun currentTimeMillis(): Long
expect fun anonymousSessionId(): String
expect fun gameWebSocketUrl(sessionId: String, nickname: String): String
expect fun logGameEvent(event: String, details: String = "")

interface BestNumberStore {
    fun load(): Int
    fun save(value: Int)
    fun loadLowestResponseTimeMillis(): Long
    fun saveLowestResponseTimeMillis(value: Long)
    fun loadNickname(): String?
    fun saveNickname(value: String)
    fun loadTheme(): String
    fun saveTheme(value: String)
    fun loadSoundMuted(): Boolean
    fun saveSoundMuted(value: Boolean)
}

@Composable
expect fun rememberBestNumberStore(): BestNumberStore

expect val supportsWebKeyboardControls: Boolean

@Composable
expect fun ObserveWebKeyboardControls(enabled: Boolean, onLeft: () -> Unit, onRight: () -> Unit)

@Composable
expect fun ObserveGameLifecycle(onStopped: () -> Unit, onResumed: () -> Unit)

@Composable
expect fun ObserveNetworkConnectivity(onOnlineChanged: (Boolean) -> Unit)
