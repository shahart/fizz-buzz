package com.shahartal.fizzbuzz

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val LATENCY_PING_INTERVAL_MILLIS = 2_000L

class GameSession(
    private val scope: CoroutineScope,
    private val client: HttpClient = createGameHttpClient(),
    val sessionId: String = anonymousSessionId(),
    val nickname: String,
    initialBestNumber: Int = 0,
    initialHighestResponseTimeMillis: Long = 0,
) {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = true }
    private val mutableState = MutableStateFlow(
        GameUiState(
            bestNumber = initialBestNumber.coerceAtLeast(0),
            highestResponseTimeMillis = initialHighestResponseTimeMillis.coerceAtLeast(0),
        ),
    )
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()
    private var connection: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private var shouldBeJoined = false
    private var lastRecordedTurnId: String? = null
    private var pingSequence = 0L
    private var pendingPingId: String? = null
    private var pendingPingStartedAt = 0L

    fun join() {
        if (shouldBeJoined) return
        shouldBeJoined = true
        mutableState.value = mutableState.value.copy(connectionStatus = ConnectionStatus.JOINING)
        connectionJob = scope.launch { connectionLoop() }
    }

    fun leaveForFocusLoss() {
        if (!shouldBeJoined) return
        shouldBeJoined = false
        val active = connection
        connection = null
        connectionJob?.cancel()
        connectionJob = null
        lastRecordedTurnId = null
        pendingPingId = null
        mutableState.value = mutableState.value.afterFocusLoss()
        scope.launch {
            active?.send(Frame.Text("{\"type\":\"leave\"}"))
            active?.close(CloseReason(CloseReason.Codes.NORMAL, "focus loss"))
        }
    }

    fun restart() = send("{\"type\":\"restart\"}")

    fun submitBoom(turnId: String) = sendAnswer(turnId, "boom")

    fun submitNumber(turnId: String, number: Int) {
        sendTrackedAnswer(turnId) { responseTimeMillis ->
            buildJsonObject {
                put("type", "answer")
                put("turnId", turnId)
                put("responseTimeMillis", responseTimeMillis)
                putJsonObject("answer") { put("type", "number"); put("value", number) }
            }.toString()
        }
    }

    fun submitRecognition(turnId: String, alternatives: List<String>): Boolean {
        when (val submission = recognitionSubmission(mutableState.value, sessionId, turnId, alternatives)) {
            null -> return false
            RecognitionSubmission.Boom -> submitBoom(turnId)
            is RecognitionSubmission.Number -> submitNumber(turnId, submission.value)
            RecognitionSubmission.Invalid -> sendAnswer(turnId, "invalid")
        }
        return true
    }

    fun close() {
        shouldBeJoined = false
        connectionJob?.cancel()
        connectionJob = null
        connection = null
        client.close()
    }

    private suspend fun connectionLoop() {
        var attempt = 0
        while (scope.isActive && shouldBeJoined) {
            if (attempt > 0) {
                mutableState.value = mutableState.value.copy(connectionStatus = ConnectionStatus.RECONNECTING)
                delay(reconnectDelayMillis(attempt - 1))
            }
            try {
                client.webSocket(gameWebSocketUrl(sessionId, nickname)) {
                    connection = this
                    attempt = 0
                    val socket = this
                    val pingJob = launch {
                        while (isActive) {
                            val sentAt = currentTimeMillis()
                            val id = "$sentAt-${++pingSequence}"
                            pendingPingId = id
                            pendingPingStartedAt = sentAt
                            val message = buildJsonObject { put("type", "ping"); put("id", id) }
                            socket.send(Frame.Text(message.toString()))
                            delay(LATENCY_PING_INTERVAL_MILLIS)
                        }
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val raw = frame.readText()
                            when (json.parseToJsonElement(raw).jsonObject["type"]?.jsonPrimitive?.content) {
                                "snapshot" -> {
                                    val snapshot = json.decodeFromString<GameSnapshot>(raw)
                                    mutableState.value = mutableState.value.reduce(snapshot, currentTimeMillis(), sessionId)
                                }
                                "roster" -> {
                                    val roster = json.decodeFromString<GameRoster>(raw)
                                    mutableState.value = mutableState.value.reduce(roster)
                                }
                                "pong" -> {
                                    val pong = json.decodeFromString<PongMessage>(raw)
                                    if (pong.id == pendingPingId) {
                                        val latency = (currentTimeMillis() - pendingPingStartedAt).coerceAtLeast(0)
                                        mutableState.value = mutableState.value.copy(latencyMillis = latency)
                                        pendingPingId = null
                                    }
                                }
                            }
                        }
                    } finally {
                        pingJob.cancel()
                        pendingPingId = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                println("Game WebSocket disconnected: ${error.message}")
            } finally {
                connection = null
            }
            if (shouldBeJoined) attempt = (attempt + 1).coerceAtMost(5)
        }
    }

    private fun sendAnswer(turnId: String, answerType: String) {
        sendTrackedAnswer(turnId) { responseTimeMillis ->
            buildJsonObject {
                put("type", "answer")
                put("turnId", turnId)
                put("responseTimeMillis", responseTimeMillis)
                putJsonObject("answer") { put("type", answerType) }
            }.toString()
        }
    }

    private fun sendTrackedAnswer(turnId: String, createMessage: (Long) -> String) {
        if (connection == null) return
        if (lastRecordedTurnId == turnId) return
        val before = mutableState.value
        val now = currentTimeMillis()
        val responseTimeMillis = before.responseTimeMillis(sessionId, turnId, now) ?: return
        mutableState.value = before.recordResponse(sessionId, turnId, now)
        lastRecordedTurnId = turnId
        send(createMessage(responseTimeMillis))
    }

    private fun send(message: String) {
        val active = connection ?: return
        scope.launch { active.send(Frame.Text(message)) }
    }
}
