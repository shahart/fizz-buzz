package com.shahartal.fizzbuzz

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.view.KeyEvent
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.AfterClass
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private var savedPreferences: Map<String, *>? = null

    @Before
    fun savePlayerPreferences() {
        savedPreferences = composeRule.activity.getSharedPreferences("seven_boom_player", 0).all
    }

    @Test
    fun lobbyInstructionsAreVisibleAndProminent() {
        chooseNicknameIfNeeded()
        requireNotNull(FakeSessionFactory.lastSession).publish(
            connectedPlayers = 1,
            activeSessionId = "self",
            connectionStatus = ConnectionStatus.IDLE,
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lobby-instructions").assertIsDisplayed()
    }

    @After
    fun restorePlayerPreferences() {
        val values = savedPreferences ?: return
        composeRule.activity.getSharedPreferences("seven_boom_player", 0).edit().clear().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
    }

    @Test
    fun launchesRendersPlayerIndicatorsAndSubmitsNumberFromTheScreen() {
        val beforePicker = requireNotNull(FakeSessionFactory.lastSession)
        if (composeRule.onAllNodesWithTag("nickname-picker").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("choose-nickname").performClick()
        }
        composeRule.onNodeWithTag("nickname-choice-${beforePicker.nickname}").performClick()
        composeRule.onAllNodesWithTag("nickname-picker").assertCountEquals(0)
        composeRule.waitForIdle()
        val session = requireNotNull(FakeSessionFactory.lastSession)

        // The real MainActivity starts with a one-player game, so the duplicate labels stay hidden.
        composeRule.onAllNodesWithTag("player-indicators").assertCountEquals(0)
        composeRule.onNodeWithTag("network-status").assertIsDisplayed()

        session.publish(connectedPlayers = 2, activeSessionId = "other")
        composeRule.onNodeWithTag("player-indicators").performScrollTo().assertIsDisplayed()
        val isRtl = composeRule.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        composeRule.onNodeWithText(if (isRtl) "השחקן הנוכחי" else "Current player").assertIsDisplayed()
        composeRule.onNodeWithText(if (isRtl) "אתם" else "You").assertIsDisplayed()
        composeRule.onNodeWithTag("current-player-emoji").assertTextEquals(session.otherEmoji)
        composeRule.onNodeWithTag("your-emoji").assertTextEquals(session.nickname)

        // Switching the fake live turn to this device enables the real number answer button.
        session.publish(connectedPlayers = 2, activeSessionId = session.sessionId)
        composeRule.onNodeWithTag("current-player-emoji").assertTextEquals(session.nickname)
        composeRule.onNodeWithContentDescription("Answer with the number").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("turn-1" to 8), session.numberAnswers)
        }

        session.publish(connectedPlayers = 2, activeSessionId = "missing", includeActivePlayer = false)
        composeRule.onNodeWithTag("current-player-emoji").assertTextEquals("❔")

        session.publish(connectedPlayers = 1, activeSessionId = session.sessionId)
        composeRule.onAllNodesWithTag("player-indicators").assertCountEquals(0)

        session.publish(connectedPlayers = 2, activeSessionId = null, phase = GamePhase.GAME_OVER)
        composeRule.onAllNodesWithTag("player-indicators").assertCountEquals(0)

        session.publish(connectedPlayers = 1, activeSessionId = session.sessionId, connectionStatus = ConnectionStatus.SOLO)
        composeRule.onAllNodesWithTag("player-indicators").assertCountEquals(0)
        composeRule.onNodeWithTag("network-status").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun playerCountChangeShowsRedNotice() {
        chooseNicknameIfNeeded()
        val session = requireNotNull(FakeSessionFactory.lastSession)
        session.publish(connectedPlayers = 1, activeSessionId = session.sessionId)
        composeRule.waitForIdle()

        session.publish(connectedPlayers = 2, activeSessionId = "other")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("player-count-notice").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("player-count-notice").assertIsDisplayed()
        val isRtl = composeRule.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        composeRule.onNodeWithText(if (isRtl) "מספר השחקנים במשחק: 2" else "Players in the game: 2").assertIsDisplayed()
    }

    @Test
    fun serverRejectedNicknameReopensPickerWithLocalizedFeedback() {
        if (composeRule.onAllNodesWithTag("nickname-picker").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("choose-nickname").performClick()
        }
        val original = requireNotNull(FakeSessionFactory.lastSession)
        composeRule.onNodeWithTag("nickname-choice-${original.nickname}").performClick()
        composeRule.waitForIdle()

        requireNotNull(FakeSessionFactory.lastSession).rejectNickname()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nickname-picker").assertIsDisplayed()
        val isRtl = composeRule.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        composeRule.onNodeWithText(
            if (isRtl) "מישהו אחר כבר בחר באימוג׳י הזה. נא לבחור אימוג׳י אחר."
            else "Someone else has already chosen this emoji. Please choose another.",
        ).assertIsDisplayed()
    }

    @Test
    fun submitsBoomWithArrowMappedToBoomInTheActiveLayout() {
        val beforePicker = requireNotNull(FakeSessionFactory.lastSession)
        if (composeRule.onAllNodesWithTag("nickname-picker").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag("choose-nickname").performClick()
        }
        composeRule.onNodeWithTag("nickname-choice-${beforePicker.nickname}").performClick()
        composeRule.waitForIdle()
        val session = requireNotNull(FakeSessionFactory.lastSession)

        val boomKey = if (
            composeRule.activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        ) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        composeRule.runOnIdle {
            assertTrue(
                "The active game's arrow control should handle the key",
                composeRule.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, boomKey)),
            )
            assertEquals(listOf("turn-1"), session.boomAnswers)
        }
    }

    @Test
    fun muteButtonIsAvailableDuringTheGameAndSavesThePreference() {
        chooseNicknameIfNeeded()
        requireNotNull(FakeSessionFactory.lastSession).publish(
            connectedPlayers = 1,
            activeSessionId = "self",
            connectionStatus = ConnectionStatus.CONNECTED,
        )
        composeRule.waitForIdle()
        val preferences = composeRule.activity.getSharedPreferences("seven_boom_player", 0)
        val wasMuted = preferences.getBoolean("sound_muted", false)
        val currentTag = if (wasMuted) "sound-toggle-muted" else "sound-toggle-enabled"
        val toggledTag = if (wasMuted) "sound-toggle-enabled" else "sound-toggle-muted"

        composeRule.onNodeWithTag(currentTag).performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(toggledTag).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(!wasMuted, preferences.getBoolean("sound_muted", false))
        }
    }

    private fun chooseNicknameIfNeeded() {
        if (composeRule.onAllNodesWithTag("nickname-picker").fetchSemanticsNodes().isEmpty()) return
        val session = requireNotNull(FakeSessionFactory.lastSession)
        composeRule.onNodeWithTag("nickname-choice-${session.nickname}").performClick()
        composeRule.waitForIdle()
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun installFakeGameSession() {
            MainActivity.sessionFactoryOverride = FakeSessionFactory::create
        }

        @JvmStatic
        @AfterClass
        fun removeFakeGameSession() {
            MainActivity.sessionFactoryOverride = null
            FakeSessionFactory.lastSession = null
        }
    }
}

private object FakeSessionFactory {
    var lastSession: FakeGameSession? = null

    fun create(nickname: String, initialBestNumber: Int, initialLowestResponseTimeMillis: Long): GameSessionController =
        FakeGameSession(nickname, initialBestNumber, initialLowestResponseTimeMillis).also { lastSession = it }
}

private class FakeGameSession(
    override val nickname: String,
    initialBestNumber: Int,
    initialLowestResponseTimeMillis: Long,
) : GameSessionController {
    override val sessionId = "self"
    val otherEmoji = EMOJI_NICKNAMES.first { it != nickname }
    val numberAnswers = mutableListOf<Pair<String, Int>>()
    val boomAnswers = mutableListOf<String>()
    private var revision = 1L
    private val mutableState = MutableStateFlow(stateFor(connectedPlayers = 1, activeSessionId = sessionId))
    override val state: StateFlow<GameUiState> = mutableState.asStateFlow()

    init {
        mutableState.value = mutableState.value.copy(
            bestNumber = initialBestNumber,
            lowestResponseTimeMillis = initialLowestResponseTimeMillis,
        )
    }

    fun publish(
        connectedPlayers: Int,
        activeSessionId: String?,
        phase: GamePhase = GamePhase.ACTIVE,
        connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
        includeActivePlayer: Boolean = true,
    ) {
        revision += 1
        mutableState.value = stateFor(connectedPlayers, activeSessionId, phase, connectionStatus, includeActivePlayer)
    }

    private fun stateFor(
        connectedPlayers: Int,
        activeSessionId: String?,
        phase: GamePhase = GamePhase.ACTIVE,
        connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
        includeActivePlayer: Boolean = true,
    ): GameUiState {
        val snapshot = GameSnapshot(
            type = "snapshot",
            revision = revision,
            phase = phase,
            number = 8,
            turnId = if (phase == GamePhase.ACTIVE) "turn-1" else null,
            activeSessionId = activeSessionId,
            connectedPlayers = connectedPlayers,
            serverTime = 10_000,
            deadline = if (phase == GamePhase.ACTIVE) 17_000 else null,
            gameOverReason = if (phase == GamePhase.GAME_OVER) GameOverReason.NO_PLAYERS else null,
        )
        val players = buildList {
            add(RosterPlayer(sessionId, nickname))
            if (connectedPlayers > 1 && includeActivePlayer) add(RosterPlayer("other", otherEmoji))
        }
        return GameUiState(
            connectionStatus = connectionStatus,
            snapshot = snapshot,
            roster = GameRoster("roster", revision, players),
        )
    }

    override fun join() = Unit
    override fun leaveForFocusLoss() = Unit
    override fun restart() = Unit
    override fun restartOnline() = Unit
    override fun continueSolo() = Unit
    override fun submitBoom(turnId: String) { boomAnswers += turnId }
    override fun submitNumber(turnId: String, number: Int) { numberAnswers += turnId to number }
    override fun submitRecognition(turnId: String, alternatives: List<String>) = false
    override fun close() = Unit

    fun rejectNickname() {
        mutableState.value = mutableState.value.copy(
            connectionStatus = ConnectionStatus.IDLE,
            nicknameTaken = true,
        )
    }
}
