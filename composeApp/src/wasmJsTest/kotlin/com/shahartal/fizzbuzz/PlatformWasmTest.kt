package com.shahartal.fizzbuzz

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformWasmTest {
    @Test
    fun webpackDevelopmentServerUsesDeployedWorker() {
        val sessionId = "11111111-1111-4111-8111-111111111111"

        assertEquals(
            "wss://global-seven-boom.lat-shahar.workers.dev/game?sessionId=$sessionId",
            gameWebSocketUrl(sessionId),
        )
    }
}
