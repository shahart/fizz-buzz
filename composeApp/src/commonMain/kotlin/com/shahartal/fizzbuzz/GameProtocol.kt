package com.shahartal.fizzbuzz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GamePhase {
    @SerialName("active") ACTIVE,
    @SerialName("gameOver") GAME_OVER,
}

@Serializable
enum class GameOverReason {
    @SerialName("wrongAnswer") WRONG_ANSWER,
    @SerialName("timeout") TIMEOUT,
    @SerialName("noPlayers") NO_PLAYERS,
}

@Serializable
data class GameSnapshot(
    val type: String,
    val revision: Long,
    val phase: GamePhase,
    val number: Int,
    val turnId: String?,
    val activeSessionId: String?,
    val connectedPlayers: Int,
    val serverTime: Long,
    val deadline: Long?,
    val gameOverReason: GameOverReason? = null,
    val failedSessionId: String? = null,
    val responseRankings: List<ResponseRanking>? = null,
)

@Serializable
data class ResponseRanking(
    val sessionId: String,
    val rank: Int,
    val averageMillis: Long,
)

@Serializable
data class PongMessage(val type: String, val id: String)

fun GameSnapshot.failedByAnotherPlayer(sessionId: String): Boolean =
    failedSessionId != null && failedSessionId != sessionId

fun GameSnapshot.responseRank(sessionId: String): Pair<Int, Int>? {
    if (phase != GamePhase.GAME_OVER) return null
    val rankings = responseRankings ?: return null
    val mine = rankings.firstOrNull { it.sessionId == sessionId } ?: return null
    return mine.rank to rankings.size
}

enum class ConnectionStatus { IDLE, JOINING, CONNECTED, RECONNECTING, REJOIN }

data class GameUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val snapshot: GameSnapshot? = null,
    val serverClockOffsetMillis: Long = 0,
    val responseTimeTotalMillis: Long = 0,
    val responseCount: Int = 0,
    val latencyMillis: Long? = null,
    val bestNumber: Int = 0,
    val highestResponseTimeMillis: Long = 0,
) {
    fun isActiveTurn(sessionId: String): Boolean =
        connectionStatus == ConnectionStatus.CONNECTED &&
            snapshot?.phase == GamePhase.ACTIVE &&
            snapshot.activeSessionId == sessionId

    fun secondsRemaining(localNowMillis: Long): Int {
        val end = snapshot?.deadline ?: return 0
        val remaining = end - (localNowMillis + serverClockOffsetMillis)
        return ((remaining.coerceAtLeast(0) + 999) / 1_000).toInt().coerceAtMost(STARTING_SECONDS)
    }

    fun recordResponse(sessionId: String, turnId: String, localNowMillis: Long): GameUiState {
        val elapsed = responseTimeMillis(sessionId, turnId, localNowMillis) ?: return this
        return copy(
            responseTimeTotalMillis = responseTimeTotalMillis + elapsed,
            responseCount = responseCount + 1,
            highestResponseTimeMillis = maxOf(highestResponseTimeMillis, elapsed),
        )
    }

    fun responseTimeMillis(sessionId: String, turnId: String, localNowMillis: Long): Long? {
        val current = snapshot ?: return null
        val deadline = current.deadline ?: return null
        if (!isActiveTurn(sessionId) || current.turnId != turnId) return null
        val turnStartedAt = deadline - STARTING_SECONDS * 1_000L
        return (localNowMillis + serverClockOffsetMillis - turnStartedAt)
            .coerceIn(0, STARTING_SECONDS * 1_000L)
    }

    fun averageResponseTimeText(sessionId: String? = null): String {
        val rankedAverage = sessionId?.let { currentSessionId ->
            snapshot?.takeIf { it.phase == GamePhase.GAME_OVER }
                ?.responseRankings?.firstOrNull { it.sessionId == currentSessionId }?.averageMillis
        }
        val averageMillis = rankedAverage ?: if (responseCount == 0) return "0.0" else responseTimeTotalMillis / responseCount
        val tenths = (averageMillis + 50) / 100
        return "${tenths / 10}.${tenths % 10}"
    }

    fun highestResponseTimeText(): String {
        val tenths = (highestResponseTimeMillis + 50) / 100
        return "${tenths / 10}.${tenths % 10}"
    }

    fun reduce(next: GameSnapshot, receivedAtMillis: Long, sessionId: String? = null): GameUiState {
        if (next.type != "snapshot" || next.revision < (snapshot?.revision ?: -1)) return this
        val startsNewPlay = snapshot?.phase == GamePhase.GAME_OVER &&
            next.phase == GamePhase.ACTIVE && next.number == 1
        val correctlyAnsweredNumber = snapshot?.takeIf {
            sessionId != null &&
                it.phase == GamePhase.ACTIVE &&
                it.activeSessionId == sessionId &&
                next.phase == GamePhase.ACTIVE &&
                next.number == it.number + 1
        }?.number
        return copy(
            connectionStatus = ConnectionStatus.CONNECTED,
            snapshot = next,
            serverClockOffsetMillis = next.serverTime - receivedAtMillis,
            responseTimeTotalMillis = if (startsNewPlay) 0 else responseTimeTotalMillis,
            responseCount = if (startsNewPlay) 0 else responseCount,
            bestNumber = maxOf(bestNumber, correctlyAnsweredNumber ?: 0),
        )
    }
}

fun reconnectDelayMillis(attempt: Int): Long =
    (500L * (1L shl attempt.coerceIn(0, 4))).coerceAtMost(8_000L)

sealed interface RecognitionSubmission {
    data object Boom : RecognitionSubmission
    data class Number(val value: Int) : RecognitionSubmission
    data object Invalid : RecognitionSubmission
}

fun recognitionSubmission(
    state: GameUiState,
    sessionId: String,
    recognizedTurnId: String,
    alternatives: List<String>,
): RecognitionSubmission? {
    val snapshot = state.snapshot ?: return null
    if (!state.isActiveTurn(sessionId) || snapshot.turnId != recognizedTurnId || alternatives.isEmpty()) return null
    if (alternatives.none { it.matchesAnswerFor(snapshot.number) }) return RecognitionSubmission.Invalid
    return if (snapshot.number.isBoomNumber()) RecognitionSubmission.Boom else RecognitionSubmission.Number(snapshot.number)
}

fun GameUiState.afterFocusLoss(): GameUiState = GameUiState(
    connectionStatus = ConnectionStatus.REJOIN,
    bestNumber = bestNumber,
    highestResponseTimeMillis = highestResponseTimeMillis,
)
