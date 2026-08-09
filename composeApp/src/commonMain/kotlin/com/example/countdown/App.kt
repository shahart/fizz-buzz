package com.example.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Navy = Color(0xFF102A43)
private val Sky = Color(0xFFEAF4FF)
private val Blue = Color(0xFF1473E6)
private val Red = Color(0xFFD92D20)

@Composable
fun App() {
    MaterialTheme {
        var round by remember { mutableStateOf(0) }
        var started by remember { mutableStateOf(!requiresUserSoundActivation) }
        var state by remember(round) {
            mutableStateOf(CountdownState(number = Random.nextInt(from = 1, until = 101)))
        }

        LaunchedEffect(round, started) {
            if (!started) return@LaunchedEffect
            while (!state.hasTimedOut) {
                delay(1_000)
                state = state.tick()
            }
            playTimeoutSound()
        }

        Surface(modifier = Modifier.fillMaxSize(), color = Sky) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!started) {
                    Text(
                        text = "READY?",
                        color = Navy,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Sound is enabled when you start",
                        color = Navy.copy(alpha = 0.7f),
                        fontSize = 16.sp,
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
                        Text(
                            text = "Start countdown",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 16.sp,
                        )
                    }
                    return@Column
                }

                Text(
                    text = if (state.hasTimedOut) "TIMED-OUT" else "YOUR NUMBER",
                    color = if (state.hasTimedOut) Red else Navy,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.number.toString(),
                        color = Navy,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    text = state.secondsRemaining.toString(),
                    color = if (state.hasTimedOut) Red else Blue,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.hasTimedOut) "Time is up" else "seconds remaining",
                    color = Navy.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = {
                        prepareTimeoutSound()
                        round += 1
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                ) {
                    Text(
                        text = if (state.hasTimedOut) "Play again" else "New number",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
