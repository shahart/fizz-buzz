package com.shahartal.fizzbuzz

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameProtocolTest {
    private val active = GameSnapshot(
        type = "snapshot",
        revision = 4,
        phase = GamePhase.ACTIVE,
        number = 17,
        turnId = "turn-4",
        activeSessionId = "a",
        connectedPlayers = 2,
        serverTime = 10_000,
        deadline = 17_000,
    )

    @Test
    fun decodesStrictSnapshotProtocol() {
        val decoded = Json.decodeFromString<GameSnapshot>(
            """{"type":"snapshot","revision":4,"phase":"active","number":17,"turnId":"turn-4","activeSessionId":"a","connectedPlayers":2,"serverTime":10000,"deadline":17000}""",
        )
        assertEquals(active, decoded)
    }

    @Test
    fun decodesRosterAndPreservesDuplicatePlayersInOrder() {
        val roster = Json.decodeFromString<GameRoster>(
            """{"type":"roster","revision":4,"players":[{"sessionId":"a","nickname":"🚀"},{"sessionId":"b","nickname":"🚀"}]}""",
        )
        val reduced = GameUiState(snapshot = active).reduce(roster)

        assertEquals(listOf("a", "b"), reduced.roster?.players?.map { it.sessionId })
        assertEquals(listOf("🚀", "🚀"), reduced.roster?.players?.map { it.nickname })
    }

    @Test
    fun rosterReducerRequiresCurrentRevisionAndValidEmojiNicknames() {
        val current = GameRoster("roster", revision = 4, players = listOf(RosterPlayer("a", "🦊")))
        val state = GameUiState(snapshot = active).reduce(current)

        assertEquals(state, state.reduce(current.copy(revision = 3, players = emptyList())))
        assertEquals(
            state,
            state.reduce(current.copy(revision = 5, players = listOf(RosterPlayer("a", "not-an-emoji")))),
        )
        assertEquals(5, state.reduce(current.copy(revision = 5)).roster?.revision)
    }

    @Test
    fun identifiesWhetherAnotherPlayerCausedGameOver() {
        val failed = active.copy(
            phase = GamePhase.GAME_OVER,
            gameOverReason = GameOverReason.TIMEOUT,
            failedSessionId = "a",
        )

        assertFalse(failed.failedByAnotherPlayer("a"))
        assertTrue(failed.failedByAnotherPlayer("b"))
        assertFalse(failed.copy(failedSessionId = null).failedByAnotherPlayer("b"))
    }

    @Test
    fun findsTheCurrentPlayersResponseRankOnlyAtGameOver() {
        val rankings = listOf(
            ResponseRanking("b", rank = 1, averageMillis = 1_200, nickname = "🐼"),
            ResponseRanking("a", rank = 2, averageMillis = 1_800, nickname = "🦊"),
        )
        assertNull(active.copy(responseRankings = rankings).responseRank("a"))
        assertEquals(
            2 to 2,
            active.copy(phase = GamePhase.GAME_OVER, responseRankings = rankings).responseRank("a"),
        )
        assertNull(active.copy(phase = GamePhase.GAME_OVER, responseRankings = rankings).responseRank("c"))
        val finished = GameUiState(
            connectionStatus = ConnectionStatus.CONNECTED,
            snapshot = active.copy(phase = GamePhase.GAME_OVER, responseRankings = rankings),
        )
        assertEquals("1.8", finished.averageResponseTimeText("a"))
        assertEquals(listOf("🐼"), finished.snapshot?.winningNicknames())
    }

    @Test
    fun nicknameMustComeFromTheEmojiChooser() {
        assertTrue(isEmojiNickname("🚀"))
        assertFalse(isEmojiNickname("Shahar"))
        assertFalse(isEmojiNickname("🚀🚀"))
        assertEquals(
            emptyList(),
            active.copy(
                phase = GamePhase.GAME_OVER,
                responseRankings = listOf(ResponseRanking("a", rank = 1, averageMillis = 1_000)),
            ).winningNicknames(),
        )
    }

    @Test
    fun reducerIgnoresOlderRevisionAndCalibratesDeadline() {
        val reduced = GameUiState().reduce(active, receivedAtMillis = 9_500)
        assertEquals(500, reduced.serverClockOffsetMillis)
        assertEquals(7, reduced.secondsRemaining(10_000))
        assertEquals(1, reduced.secondsRemaining(16_000))
        assertEquals(reduced, reduced.reduce(active.copy(revision = 3, number = 99), 12_000))
    }

    @Test
    fun responseAverageUsesAuthoritativeTurnStartAndResetsOnRestart() {
        val first = GameUiState(ConnectionStatus.CONNECTED, active, serverClockOffsetMillis = 500)
            .recordResponse("a", "turn-4", localNowMillis = 11_000)
        assertEquals(1, first.responseCount)
        assertEquals(1_500L, first.responseTimeTotalMillis)
        assertEquals("1.5", first.averageResponseTimeText())
        assertEquals(1_500L, first.highestResponseTimeMillis)
        assertEquals("1.5", first.highestResponseTimeText())

        val secondTurn = active.copy(revision = 5, turnId = "turn-5", serverTime = 12_000, deadline = 19_000)
        val second = first.reduce(secondTurn, receivedAtMillis = 11_500)
            .recordResponse("a", "turn-5", localNowMillis = 13_500)
        assertEquals(2, second.responseCount)
        assertEquals("1.8", second.averageResponseTimeText())
        assertEquals(2_000L, second.highestResponseTimeMillis)
        assertEquals("2.0", second.highestResponseTimeText())

        val gameOver = second.reduce(secondTurn.copy(revision = 6, phase = GamePhase.GAME_OVER), 14_000)
        val restarted = gameOver.reduce(active.copy(revision = 7, number = 1), 15_000)
        assertEquals(0, restarted.responseCount)
        assertEquals("0.0", restarted.averageResponseTimeText())
    }

    @Test
    fun recordTracksOnlyTheCurrentPlayersCorrectlyAnsweredNumbers() {
        val initial = GameUiState(ConnectionStatus.CONNECTED, active)
        val advanced = initial.reduce(
            active.copy(revision = 5, number = 18, turnId = "turn-5", activeSessionId = "b"),
            receivedAtMillis = 10_500,
            sessionId = "a",
        )
        assertEquals(17, advanced.bestNumber)

        val otherPlayerAdvanced = advanced.reduce(
            active.copy(revision = 6, number = 19, turnId = "turn-6", activeSessionId = "a"),
            receivedAtMillis = 11_000,
            sessionId = "a",
        )
        assertEquals(17, otherPlayerAdvanced.bestNumber)

        val failed = otherPlayerAdvanced.reduce(
            active.copy(revision = 7, number = 19, phase = GamePhase.GAME_OVER, activeSessionId = null),
            receivedAtMillis = 11_500,
            sessionId = "a",
        )
        assertEquals(17, failed.bestNumber)
        assertEquals(17, failed.afterFocusLoss().bestNumber)
    }

    @Test
    fun microphoneEligibilityIsOnlyTheConnectedActiveTurn() {
        val state = GameUiState(ConnectionStatus.CONNECTED, active)
        assertTrue(state.isActiveTurn("a"))
        assertFalse(state.isActiveTurn("b"))
        assertFalse(state.copy(connectionStatus = ConnectionStatus.RECONNECTING).isActiveTurn("a"))
    }

    @Test
    fun recognitionRejectsStaleTurnsAndNeverReturnsTranscript() {
        val state = GameUiState(ConnectionStatus.CONNECTED, active)
        assertNull(recognitionSubmission(state, "a", "old-turn", listOf("boom")))
        assertNull(recognitionSubmission(state, "b", "turn-4", listOf("boom")))
        assertEquals(RecognitionSubmission.Boom, recognitionSubmission(state, "a", "turn-4", listOf("boom")))
        assertEquals(RecognitionSubmission.Invalid, recognitionSubmission(state, "a", "turn-4", listOf("seventeen")))
        assertIs<RecognitionSubmission.Number>(
            recognitionSubmission(state.copy(snapshot = active.copy(number = 18)), "a", "turn-4", listOf("18")),
        )
    }

    @Test
    fun focusLossRequiresExplicitRejoinAndBackoffIsBounded() {
        val focused = GameUiState(
            connectionStatus = ConnectionStatus.CONNECTED,
            snapshot = active,
            bestNumber = 17,
            highestResponseTimeMillis = 2_400,
        )
        val stopped = focused.afterFocusLoss()
        assertEquals(ConnectionStatus.REJOIN, stopped.connectionStatus)
        assertNull(stopped.snapshot)
        assertEquals(17, stopped.bestNumber)
        assertEquals(2_400L, stopped.highestResponseTimeMillis)
        assertEquals(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 8_000L), (0..5).map(::reconnectDelayMillis))
    }
}
