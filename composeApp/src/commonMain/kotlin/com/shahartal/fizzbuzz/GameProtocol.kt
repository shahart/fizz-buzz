package com.shahartal.fizzbuzz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

val EMOJI_NICKNAMES = listOf(
    "😀", "😎", "🥳", "🤩", "😂", "😊",
    "🦊", "🐼", "🐸", "🦁", "🐵", "🐙",
    "🦄", "🐲", "🦖", "🐝", "🦋", "🐳",
    "🚀", "⚡", "🔥", "🌈", "⭐", "🌙",
)

const val DEFAULT_EMOJI_NICKNAME = "😀"

fun isEmojiNickname(value: String): Boolean = value in EMOJI_NICKNAMES

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
    val nickname: String? = null,
)

@Serializable
data class RosterPlayer(
    val sessionId: String,
    val nickname: String,
)

@Serializable
data class GameRoster(
    val type: String,
    val revision: Long,
    val players: List<RosterPlayer>,
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

fun GameSnapshot.winningNicknames(): List<String> =
    if (phase != GamePhase.GAME_OVER) emptyList() else responseRankings.orEmpty()
        .filter { it.rank == 1 }
        .mapNotNull { it.nickname?.takeIf(::isEmojiNickname) }
        .distinct()

enum class ConnectionStatus { IDLE, JOINING, CONNECTED, RECONNECTING, REJOIN, SOLO_PENDING, SOLO }

data class GameUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val snapshot: GameSnapshot? = null,
    val roster: GameRoster? = null,
    val serverClockOffsetMillis: Long = 0,
    val responseTimeTotalMillis: Long = 0,
    val responseCount: Int = 0,
    val latencyMillis: Long? = null,
    val bestNumber: Int = 0,
    val lowestResponseTimeMillis: Long = 0,
) {
    fun isActiveTurn(sessionId: String): Boolean =
        connectionStatus in setOf(ConnectionStatus.CONNECTED, ConnectionStatus.SOLO) &&
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
            lowestResponseTimeMillis = if (lowestResponseTimeMillis == 0L) {
                elapsed
            } else {
                minOf(lowestResponseTimeMillis, elapsed)
            },
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

    fun lowestResponseTimeText(): String {
        val tenths = (lowestResponseTimeMillis + 50) / 100
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

    fun reduce(next: GameRoster): GameUiState {
        val snapshotRevision = snapshot?.revision ?: -1
        val rosterRevision = roster?.revision ?: -1
        if (
            next.type != "roster" ||
            next.revision < snapshotRevision ||
            next.revision < rosterRevision ||
            next.players.any { !isEmojiNickname(it.nickname) }
        ) return this
        return copy(roster = next)
    }
}

fun GameUiState.startSoloGame(
    sessionId: String,
    nickname: String,
    nowMillis: Long,
    startingNumber: Int = 1,
): GameUiState {
    val revision = (snapshot?.revision ?: 0L) + 1L
    return copy(
        connectionStatus = ConnectionStatus.SOLO,
        snapshot = soloSnapshot(
            revision = revision,
            number = startingNumber.coerceAtLeast(1),
            sessionId = sessionId,
            nowMillis = nowMillis,
        ),
        roster = GameRoster(
            type = "roster",
            revision = revision,
            players = listOf(RosterPlayer(sessionId, nickname)),
        ),
        serverClockOffsetMillis = 0,
        responseTimeTotalMillis = 0,
        responseCount = 0,
        latencyMillis = null,
    )
}

fun GameUiState.answerSolo(
    sessionId: String,
    turnId: String,
    correct: Boolean,
    nowMillis: Long,
): GameUiState {
    val current = snapshot ?: return this
    if (connectionStatus != ConnectionStatus.SOLO || !isActiveTurn(sessionId) || current.turnId != turnId) return this
    if (nowMillis >= (current.deadline ?: 0L)) return timeoutSolo(sessionId, turnId, nowMillis)

    val recorded = recordResponse(sessionId, turnId, nowMillis)
    if (!correct) return recorded.finishSolo(sessionId, GameOverReason.WRONG_ANSWER, nowMillis)

    val next = soloSnapshot(
        revision = current.revision + 1L,
        number = current.number + 1,
        sessionId = sessionId,
        nowMillis = nowMillis,
    )
    return recorded.reduce(next, nowMillis, sessionId).copy(connectionStatus = ConnectionStatus.SOLO)
}

fun GameUiState.timeoutSolo(sessionId: String, turnId: String, nowMillis: Long): GameUiState {
    val current = snapshot ?: return this
    if (connectionStatus != ConnectionStatus.SOLO || !isActiveTurn(sessionId) || current.turnId != turnId) return this
    return finishSolo(sessionId, GameOverReason.TIMEOUT, nowMillis)
}

private fun GameUiState.finishSolo(
    sessionId: String,
    reason: GameOverReason,
    nowMillis: Long,
): GameUiState {
    val current = snapshot ?: return this
    return copy(
        connectionStatus = ConnectionStatus.SOLO,
        snapshot = current.copy(
            revision = current.revision + 1L,
            phase = GamePhase.GAME_OVER,
            turnId = null,
            activeSessionId = null,
            serverTime = nowMillis,
            deadline = null,
            gameOverReason = reason,
            failedSessionId = sessionId,
            responseRankings = null,
        ),
        serverClockOffsetMillis = 0,
        latencyMillis = null,
    )
}

private fun soloSnapshot(revision: Long, number: Int, sessionId: String, nowMillis: Long) = GameSnapshot(
    type = "snapshot",
    revision = revision,
    phase = GamePhase.ACTIVE,
    number = number,
    turnId = "solo-$revision-$number",
    activeSessionId = sessionId,
    connectedPlayers = 1,
    serverTime = nowMillis,
    deadline = nowMillis + STARTING_SECONDS * 1_000L,
)

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
    lowestResponseTimeMillis = lowestResponseTimeMillis,
)
