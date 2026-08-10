package com.shahartal.fizzbuzz

import androidx.compose.runtime.Composable
import io.ktor.client.HttpClient

expect fun createGameHttpClient(): HttpClient
expect fun currentTimeMillis(): Long
expect fun anonymousSessionId(): String
expect fun gameWebSocketUrl(sessionId: String, nickname: String): String

interface BestNumberStore {
    fun load(): Int
    fun save(value: Int)
    fun loadHighestResponseTimeMillis(): Long
    fun saveHighestResponseTimeMillis(value: Long)
    fun loadNickname(): String?
    fun saveNickname(value: String)
}

@Composable
expect fun rememberBestNumberStore(): BestNumberStore

@Composable
expect fun ObserveGameLifecycle(onStopped: () -> Unit, onResumed: () -> Unit)
