package com.shahartal.fizzbuzz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fizz_buzz.composeapp.generated.resources.Res
import fizz_buzz.composeapp.generated.resources.average_response_time
import fizz_buzz.composeapp.generated.resources.boom
import fizz_buzz.composeapp.generated.resources.choose_emoji
import fizz_buzz.composeapp.generated.resources.choose_nickname
import fizz_buzz.composeapp.generated.resources.connected_players
import fizz_buzz.composeapp.generated.resources.continue_solo
import fizz_buzz.composeapp.generated.resources.dismiss_roster
import fizz_buzz.composeapp.generated.resources.instructions
import fizz_buzz.composeapp.generated.resources.lowest_response_time
import fizz_buzz.composeapp.generated.resources.join_game
import fizz_buzz.composeapp.generated.resources.joining
import fizz_buzz.composeapp.generated.resources.layout_direction
import fizz_buzz.composeapp.generated.resources.latency
import fizz_buzz.composeapp.generated.resources.noto_sans_hebrew
import fizz_buzz.composeapp.generated.resources.play_again
import fizz_buzz.composeapp.generated.resources.roster_active_turn
import fizz_buzz.composeapp.generated.resources.roster_loading
import fizz_buzz.composeapp.generated.resources.ready
import fizz_buzz.composeapp.generated.resources.record
import fizz_buzz.composeapp.generated.resources.reconnecting
import fizz_buzz.composeapp.generated.resources.response_time_rank
import fizz_buzz.composeapp.generated.resources.response_time_unranked
import fizz_buzz.composeapp.generated.resources.rejoin_game
import fizz_buzz.composeapp.generated.resources.someone_else_timed_out
import fizz_buzz.composeapp.generated.resources.someone_else_wrong_answer
import fizz_buzz.composeapp.generated.resources.solo_mode_message
import fizz_buzz.composeapp.generated.resources.solo_mode_title
import fizz_buzz.composeapp.generated.resources.speech_unavailable
import fizz_buzz.composeapp.generated.resources.time_is_up
import fizz_buzz.composeapp.generated.resources.waiting
import fizz_buzz.composeapp.generated.resources.winner
import fizz_buzz.composeapp.generated.resources.wrong_answer
import fizz_buzz.composeapp.generated.resources.your_turn
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource

private val Navy = Color(0xFF102A43)
private val Sky = Color(0xFFEAF4FF)
private val Blue = Color(0xFF1473E6)
private val Red = Color(0xFFD92D20)
private const val MICROPHONE_SETTLE_MILLIS = 150L
private const val FAILURE_FLASH_COUNT = 2

@Composable
fun App(compact: Boolean = false) {
    val direction = if (stringResource(Res.string.layout_direction) == "rtl") LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(typography = fizzBuzzTypography()) { MultiplayerGame(compact) }
    }
}

@Composable
private fun fizzBuzzTypography(): Typography {
    val fontFamily = FontFamily(
        Font(Res.font.noto_sans_hebrew, FontWeight.Normal),
        Font(Res.font.noto_sans_hebrew, FontWeight.Bold),
    )
    val defaults = Typography()
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = defaults.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = defaults.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = defaults.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = defaults.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = defaults.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = defaults.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = defaults.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = defaults.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = defaults.labelSmall.copy(fontFamily = fontFamily),
    )
}

@Composable
private fun MultiplayerGame(compact: Boolean) {
    val scope = rememberCoroutineScope()
    val bestNumberStore = rememberBestNumberStore()
    var nickname by remember(bestNumberStore) {
        mutableStateOf(bestNumberStore.loadNickname()?.takeIf(::isEmojiNickname))
    }
    var showNicknamePicker by remember { mutableStateOf(nickname == null) }
    var showRoster by remember { mutableStateOf(false) }
    val session = remember(bestNumberStore, nickname) {
        GameSession(
            scope,
            nickname = nickname ?: DEFAULT_EMOJI_NICKNAME,
            initialBestNumber = bestNumberStore.load(),
            initialLowestResponseTimeMillis = bestNumberStore.loadLowestResponseTimeMillis(),
        )
    }
    val uiState by session.state.collectAsState()
    val snapshot = uiState.snapshot
    val speech = rememberSpeechRecognizerController()
    var lastHeard by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(currentTimeMillis()) }
    var soundedRevision by remember { mutableLongStateOf(-1) }
    var failureBlinking by remember { mutableStateOf(false) }
    val failureFlashAlpha = remember { Animatable(1f) }
    val contentScrollState = rememberScrollState()
    val activeTurn = uiState.isActiveTurn(session.sessionId)

    LaunchedEffect(uiState.bestNumber) {
        bestNumberStore.save(uiState.bestNumber)
    }
    LaunchedEffect(uiState.lowestResponseTimeMillis) {
        bestNumberStore.saveLowestResponseTimeMillis(uiState.lowestResponseTimeMillis)
    }

    DisposableEffect(session) { onDispose(session::close) }
    ObserveGameLifecycle(
        onStopped = {
            speech.stopListening()
            session.leaveForFocusLoss()
        },
        onResumed = {},
    )

    LaunchedEffect(snapshot?.deadline, uiState.serverClockOffsetMillis) {
        while (snapshot?.phase == GamePhase.ACTIVE) {
            now = currentTimeMillis()
            delay(100)
        }
    }

    LaunchedEffect(activeTurn, snapshot?.turnId) {
        speech.stopListening()
        val turnId = snapshot?.turnId ?: return@LaunchedEffect
        if (!activeTurn) return@LaunchedEffect
        delay(MICROPHONE_SETTLE_MILLIS)
        if (speech.isSupported) speech.startListening()
        try {
            while (true) {
                delay(100)
                speech.consumePartialTranscript()?.let { partial ->
                    if (partial.matchesAnswerFor(snapshot.number)) {
                        lastHeard = partial
                        session.submitRecognition(turnId, listOf(partial))
                        return@LaunchedEffect
                    }
                }
                val alternatives = speech.consumeResults() ?: continue
                lastHeard = alternatives.firstOrNull()
                session.submitRecognition(turnId, alternatives)
                return@LaunchedEffect
            }
        } finally {
            speech.stopListening()
        }
    }

    LaunchedEffect(snapshot?.revision, snapshot?.phase) {
        if (
            snapshot?.phase == GamePhase.GAME_OVER &&
            snapshot.revision != soundedRevision &&
            snapshot.gameOverReason != GameOverReason.NO_PLAYERS
        ) {
            soundedRevision = snapshot.revision
            playTimeoutSound()
        }
    }

    LaunchedEffect(snapshot?.revision, snapshot?.failedSessionId) {
        failureBlinking = false
        failureFlashAlpha.snapTo(1f)
        val current = snapshot ?: return@LaunchedEffect
        if (
            current.phase == GamePhase.GAME_OVER &&
            current.gameOverReason != GameOverReason.NO_PLAYERS
        ) {
            failureBlinking = true
            repeat(FAILURE_FLASH_COUNT) {
                failureFlashAlpha.animateTo(0.2f, tween(260))
                failureFlashAlpha.animateTo(1f, tween(260))
            }
            failureBlinking = false
        }
    }

    LaunchedEffect(snapshot?.phase) {
        if (snapshot?.phase == GamePhase.ACTIVE) contentScrollState.scrollTo(0)
    }

    val failed = snapshot?.phase == GamePhase.GAME_OVER && snapshot.gameOverReason != GameOverReason.NO_PLAYERS
    val background = when {
        failed && failureBlinking -> Red.copy(alpha = failureFlashAlpha.value)
        failed -> Red
        else -> Sky
    }
    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(contentScrollState)
                        .heightIn(min = maxHeight)
                        .padding(horizontal = if (compact) 18.dp else 24.dp, vertical = if (compact) 4.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (uiState.connectionStatus) {
                        ConnectionStatus.IDLE, ConnectionStatus.REJOIN -> JoinPanel(
                            speechSupported = speech.isSupported,
                            rejoin = uiState.connectionStatus == ConnectionStatus.REJOIN,
                            nickname = nickname,
                            compact = compact,
                            onChooseNickname = { showNicknamePicker = true },
                            onJoin = { prepareTimeoutSound(); lastHeard = null; session.join() },
                        )
                        ConnectionStatus.JOINING -> StatusText(stringResource(Res.string.joining))
                        ConnectionStatus.RECONNECTING -> StatusText(stringResource(Res.string.reconnecting))
                        ConnectionStatus.SOLO_PENDING -> StatusText(stringResource(Res.string.solo_mode_title))
                        ConnectionStatus.CONNECTED, ConnectionStatus.SOLO -> if (snapshot != null) GamePanel(
                            snapshot = snapshot,
                            sessionId = session.sessionId,
                            activeTurn = activeTurn,
                            seconds = uiState.secondsRemaining(now),
                            averageResponseTime = uiState.averageResponseTimeText(session.sessionId),
                            latencyMillis = uiState.latencyMillis,
                            bestNumber = uiState.bestNumber,
                            lowestResponseTime = uiState.lowestResponseTimeText(),
                            lastHeard = lastHeard,
                            onBoom = { lastHeard = null; snapshot.turnId?.let(session::submitBoom) },
                            onNumber = {
                                lastHeard = null
                                snapshot.let { current ->
                                    current.turnId?.let { turnId -> session.submitNumber(turnId, current.number) }
                                }
                            },
                            onRestart = session::restart,
                            onShowRoster = { showRoster = true },
                            compact = compact,
                        )
                    }
                }
            }

        }

        if (showNicknamePicker) {
            EmojiNicknamePicker(
                selected = nickname,
                onSelect = { selected ->
                    bestNumberStore.saveNickname(selected)
                    nickname = selected
                    showNicknamePicker = false
                },
                onDismiss = { if (nickname != null) showNicknamePicker = false },
                compact = compact,
            )
        }
        if (showRoster && snapshot != null) {
            PlayerRosterDialog(
                snapshot = snapshot,
                roster = uiState.roster?.takeIf { it.revision == snapshot.revision },
                onDismiss = { showRoster = false },
            )
        }
        if (uiState.connectionStatus == ConnectionStatus.SOLO_PENDING) {
            SoloModeDialog(onContinue = session::continueSolo)
        }
    }
}

@Composable
private fun SoloModeDialog(onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(Res.string.solo_mode_title), fontWeight = FontWeight.Bold) },
        text = { Text(stringResource(Res.string.solo_mode_message)) },
        confirmButton = {
            Button(onClick = onContinue) { Text(stringResource(Res.string.continue_solo)) }
        },
    )
}

@Composable
private fun JoinPanel(
    speechSupported: Boolean,
    rejoin: Boolean,
    nickname: String?,
    compact: Boolean,
    onChooseNickname: () -> Unit,
    onJoin: () -> Unit,
) {
    Text(stringResource(Res.string.ready), color = Navy, fontSize = if (compact) 24.sp else 32.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(if (compact) 6.dp else 16.dp))
    Text(stringResource(Res.string.choose_nickname), color = Navy.copy(alpha = .7f), fontSize = if (compact) 12.sp else 15.sp)
    TextButton(onClick = onChooseNickname, shape = RoundedCornerShape(14.dp)) {
        if (nickname == null) {
            Text(stringResource(Res.string.choose_emoji), fontSize = 18.sp)
        } else {
            EmojiNicknameSymbol(nickname, fontSize = if (compact) 34.sp else 44.sp)
        }
    }
    Spacer(Modifier.height(if (compact) 2.dp else 10.dp))
    Text(
        platformSupportedText(
            if (speechSupported) stringResource(Res.string.instructions) else stringResource(Res.string.speech_unavailable),
        ),
        color = Navy.copy(alpha = 0.7f), fontSize = if (compact) 12.sp else 16.sp, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(if (compact) 10.dp else 32.dp))
    Button(
        onClick = onJoin,
        enabled = nickname != null,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Blue),
    ) {
        Text(stringResource(if (rejoin) Res.string.rejoin_game else Res.string.join_game), Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun EmojiNicknamePicker(selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit, compact: Boolean) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.choose_emoji), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = if (compact) {
                    Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EMOJI_NICKNAMES.chunked(if (compact) 4 else 6).forEach { emojis ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        emojis.forEach { emoji ->
                            TextButton(
                                onClick = { onSelect(emoji) },
                                modifier = Modifier.size(if (compact) 40.dp else 48.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (emoji == selected) Blue.copy(alpha = .15f) else Color.Transparent,
                                ),
                            ) {
                                EmojiNicknameSymbol(emoji, fontSize = if (compact) 20.sp else 22.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = Navy, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun PlayerRosterDialog(snapshot: GameSnapshot, roster: GameRoster?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.connected_players, snapshot.connectedPlayers), fontWeight = FontWeight.Bold) },
        text = {
            if (roster == null) {
                Text(stringResource(Res.string.roster_loading), color = Navy.copy(alpha = .7f))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(56.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 336.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(roster.players, key = { it.sessionId }) { player ->
                            val isActive = snapshot.phase == GamePhase.ACTIVE &&
                                player.sessionId == snapshot.activeSessionId
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .then(if (isActive) Modifier.border(3.dp, Blue, CircleShape) else Modifier)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmojiNicknameSymbol(player.nickname, fontSize = 30.sp)
                            }
                        }
                    }
                    if (
                        snapshot.phase == GamePhase.ACTIVE &&
                        roster.players.any { it.sessionId == snapshot.activeSessionId }
                    ) {
                        Text(
                            stringResource(Res.string.roster_active_turn),
                            color = Blue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dismiss_roster)) }
        },
    )
}

@Composable
private fun GamePanel(
    snapshot: GameSnapshot,
    sessionId: String,
    activeTurn: Boolean,
    seconds: Int,
    averageResponseTime: String,
    latencyMillis: Long?,
    bestNumber: Int,
    lowestResponseTime: String,
    lastHeard: String?,
    onBoom: () -> Unit,
    onNumber: () -> Unit,
    onRestart: () -> Unit,
    onShowRoster: () -> Unit,
    compact: Boolean,
) {
    val failed = snapshot.phase == GamePhase.GAME_OVER
    if (failed) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BombSymbol(Modifier.size(if (compact) 32.dp else 48.dp), fontSize = if (compact) 32.sp else 48.sp)
            Text(
                text = stringResource(Res.string.boom).replace("💣", "").trim(),
                color = Color.White,
                fontSize = if (compact) 32.sp else 56.sp,
                fontWeight = FontWeight.Black,
            )
        }
        val winners = snapshot.winningNicknames()
        if (winners.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.winner),
                    color = Color.White,
                    fontSize = if (compact) 18.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                winners.forEach { winner ->
                    EmojiNicknameSymbol(winner, fontSize = if (compact) 22.sp else 30.sp, color = Color.White)
                }
            }
        }
    } else {
        Text(
            text = stringResource(if (activeTurn) Res.string.your_turn else Res.string.waiting),
            color = Navy,
            fontSize = if (compact) 18.sp else 24.sp,
            fontWeight = FontWeight.Black,
        )
    }
    Spacer(Modifier.height(if (compact) 2.dp else 14.dp))
    if (!compact) {
        TextButton(onClick = onShowRoster, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text(
                stringResource(Res.string.connected_players, snapshot.connectedPlayers),
                color = if (failed) Color.White else Navy.copy(alpha = .65f),
            )
        }
    }
    Spacer(Modifier.height(if (compact) 4.dp else 18.dp))
    Box(Modifier.size(if (compact) 72.dp else 190.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Text(snapshot.number.toString(), color = Navy, fontSize = if (compact) 36.sp else 72.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(if (compact) 4.dp else 24.dp))
    Text(seconds.toString(), color = if (failed) Color.White else Blue, fontSize = if (compact) 26.sp else 48.sp, fontWeight = FontWeight.Bold)
    val statusMessage = when {
            snapshot.gameOverReason == GameOverReason.TIMEOUT && snapshot.failedByAnotherPlayer(sessionId) ->
                stringResource(Res.string.someone_else_timed_out)
            snapshot.gameOverReason == GameOverReason.TIMEOUT -> stringResource(Res.string.time_is_up)
            snapshot.gameOverReason == GameOverReason.WRONG_ANSWER && snapshot.failedByAnotherPlayer(sessionId) ->
                stringResource(Res.string.someone_else_wrong_answer)
            failed -> stringResource(Res.string.wrong_answer)
            !activeTurn -> stringResource(Res.string.waiting)
            else -> null
        }
    if (statusMessage != null) {
        Text(
            text = statusMessage,
            color = if (failed) Color.White else Navy.copy(alpha = .7f),
            fontSize = if (compact) 12.sp else 16.sp,
            textAlign = TextAlign.Center,
        )
    }
    if (!failed) {
        Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
        AnswerButtons(
            enabled = activeTurn,
            number = snapshot.number,
            onBoom = onBoom,
            onNumber = onNumber,
            compact = compact,
        )
    }
    if (activeTurn && lastHeard != null) {
        Spacer(Modifier.height(12.dp))
        Text(lastHeard, color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
    if (failed) {
        Spacer(Modifier.height(if (compact) 10.dp else 30.dp))
        Button(onClick = onRestart, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
            Text(stringResource(Res.string.play_again), Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
    Spacer(Modifier.height(if (compact) 2.dp else 10.dp))
    Text(
        text = stringResource(Res.string.record, bestNumber),
        color = if (failed) Color.White.copy(alpha = .9f) else Navy.copy(alpha = .7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(Res.string.lowest_response_time, lowestResponseTime),
        color = if (failed) Color.White.copy(alpha = .9f) else Navy.copy(alpha = .7f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(Res.string.average_response_time, averageResponseTime),
        color = if (failed) Color.White.copy(alpha = .85f) else Navy.copy(alpha = .65f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
    if (failed) {
        val rank = snapshot.responseRank(sessionId)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (rank == null) {
                    stringResource(Res.string.response_time_unranked)
                } else {
                    stringResource(Res.string.response_time_rank, rank.first, rank.second)
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val place = rank?.first
            if (place != null && place in 1..3) MedalSymbol(place, Modifier.size(28.dp))
        }
    }
    Text(
        text = stringResource(Res.string.latency, latencyMillis?.toString() ?: "—"),
        color = if (failed) Color.White.copy(alpha = .7f) else Navy.copy(alpha = .5f),
        fontSize = 9.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun AnswerButtons(enabled: Boolean, number: Int, onBoom: () -> Unit, onNumber: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = if (compact) 4.dp else 24.dp, vertical = if (compact) 4.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
    ) {
        Button(
            onClick = onBoom, enabled = enabled, modifier = Modifier.weight(1f).height(if (compact) 44.dp else 64.dp),
            shape = RoundedCornerShape(if (compact) 26.dp else 18.dp), colors = ButtonDefaults.buttonColors(containerColor = Red),
        ) { BombSymbol(Modifier.size(if (compact) 28.dp else 36.dp), fontSize = if (compact) 24.sp else 30.sp) }
        Button(
            onClick = onNumber, enabled = enabled, modifier = Modifier.weight(1f).height(if (compact) 44.dp else 64.dp),
            shape = RoundedCornerShape(if (compact) 26.dp else 18.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue),
        ) { Text(number.toString(), fontSize = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.Black) }
    }
}
