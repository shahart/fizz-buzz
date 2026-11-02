package com.shahartal.fizzbuzz

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformWasmTest {
    @Test
    fun webpackDevelopmentServerUsesDeployedWorker() {
        val sessionId = "11111111-1111-4111-8111-111111111111"

        val url = gameWebSocketUrl(sessionId, "🚀")
        assertTrue(url.startsWith("wss://global-seven-boom.lat-shahar.workers.dev/game?sessionId=$sessionId&nickname=%F0%9F%9A%80&timeZone="))
        assertTrue(url.substringAfter("&timeZone=").isNotBlank())
    }
}
