package com.shahartal.countdown

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fizz_buzz.composeapp.generated.resources.Res
import fizz_buzz.composeapp.generated.resources.boom
import fizz_buzz.composeapp.generated.resources.heard
import fizz_buzz.composeapp.generated.resources.instructions
import fizz_buzz.composeapp.generated.resources.layout_direction
import fizz_buzz.composeapp.generated.resources.play_again
import fizz_buzz.composeapp.generated.resources.ready
import fizz_buzz.composeapp.generated.resources.say_boom
import fizz_buzz.composeapp.generated.resources.say_it_now
import fizz_buzz.composeapp.generated.resources.seconds_remaining_listening
import fizz_buzz.composeapp.generated.resources.speech_unavailable
import fizz_buzz.composeapp.generated.resources.start_game
import fizz_buzz.composeapp.generated.resources.time_is_up
import fizz_buzz.composeapp.generated.resources.wrong_answer
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

private val Navy = Color(0xFF102A43)
private val Sky = Color(0xFFEAF4FF)
private val Blue = Color(0xFF1473E6)
private val Red = Color(0xFFD92D20)
private const val MICROPHONE_SETTLE_MILLIS = 150L
private const val RESULT_GRACE_MILLIS = 2_000L

@Composable
fun App() {
    val layoutDirection = if (stringResource(Res.string.layout_direction) == "rtl") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme {
        var started by remember { mutableStateOf(false) }
        var gameId by remember { mutableStateOf(0) }
        var lastHeard by remember(gameId) { mutableStateOf<String?>(null) }
        var state by remember(gameId) {
            mutableStateOf(CountdownState(number = Random.nextInt(from = 1, until = 101)))
        }
        val speechRecognizer = rememberSpeechRecognizerController()

        fun submitAnswer(answer: String) {
            speechRecognizer.stopListening()
            val next = state.answer(answer)
            state = next
            if (next.hasFailed) playTimeoutSound()
        }

        LaunchedEffect(started, gameId, state.number, state.isFinished) {
            if (!started || state.isFinished) {
                speechRecognizer.stopListening()
                return@LaunchedEffect
            }

            // Let the previous utterance finish so it is not captured as the next answer.
            delay(MICROPHONE_SETTLE_MILLIS)
            if (speechRecognizer.isSupported) speechRecognizer.startListening()
            try {
                fun processRecognition(): Boolean {
                    val alternatives = speechRecognizer.consumeResults() ?: return false
                    val accepted = alternatives.firstOrNull { it.matchesAnswerFor(state.number) }
                    val spoken = accepted ?: alternatives.first()
                    lastHeard = spoken
                    speechRecognizer.stopListening()
                    println("Speech recognized for ${state.number}: ${alternatives.joinToString()}")
                    submitAnswer(spoken)
                    return true
                }

                repeat(STARTING_SECONDS * 10) { step ->
                    delay(100)
                    if (processRecognition()) return@LaunchedEffect
                    if ((step + 1) % 10 == 0 && step + 1 < STARTING_SECONDS * 10) {
                        state = state.tick()
                    }
                }

                if (speechRecognizer.hasDetectedSpeech) {
                    repeat((RESULT_GRACE_MILLIS / 100L).toInt()) {
                        delay(100)
                        if (processRecognition()) return@LaunchedEffect
                    }
                }
                state = state.tick()
                playTimeoutSound()
            } finally {
                speechRecognizer.stopListening()
            }
        }

        val flashTransition = rememberInfiniteTransition(label = "boom flash")
        val flashAlpha by flashTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(260), RepeatMode.Reverse),
            label = "boom flash alpha",
        )
        val failed = state.isFinished
        val background = if (failed) Red.copy(alpha = flashAlpha) else Sky

        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .padding(bottom = if (started && !failed) 92.dp else 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (!started) {
                        Text(stringResource(Res.string.ready), color = Navy, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (speechRecognizer.isSupported) {
                                stringResource(Res.string.instructions)
                            } else {
                                stringResource(Res.string.speech_unavailable)
                            },
                            color = Navy.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = {
                                prepareTimeoutSound()
                                started = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        ) {
                            Text(stringResource(Res.string.start_game), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                        }
                        return@Column
                    }

                Text(
                    text = if (failed) stringResource(Res.string.boom) else stringResource(Res.string.say_it_now),
                    color = if (failed) Color.White else Navy,
                    fontSize = if (failed) 56.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier.size(190.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(state.number.toString(), color = Navy, fontSize = 72.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = state.secondsRemaining.toString(),
                    color = if (failed) Color.White else Blue,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        state.hasTimedOut -> stringResource(Res.string.time_is_up)
                        state.hasFailed -> stringResource(Res.string.wrong_answer)
                        state.number.isBoomNumber() -> stringResource(Res.string.say_boom)
                        else -> stringResource(Res.string.seconds_remaining_listening)
                    },
                    color = if (failed) Color.White else Navy.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                lastHeard?.let { spoken ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.heard, spoken),
                        color = if (failed) Color.White else Navy,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                if (failed) {
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = {
                            prepareTimeoutSound()
                            gameId += 1
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    ) {
                        Text(stringResource(Res.string.play_again), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                }
                if (started && !failed) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Button(
                            onClick = {
                                lastHeard = null
                                submitAnswer("boom")
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Red),
                        ) {
                            Text("💣", fontSize = 30.sp)
                        }
                        Button(
                            onClick = {
                                lastHeard = null
                                submitAnswer(state.number.toString())
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        ) {
                            Text(state.number.toString(), fontSize = 28.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
        }
    }
}
