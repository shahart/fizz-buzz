package com.shahartal.fizzbuzz

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
actual fun BombSymbol(modifier: Modifier, fontSize: TextUnit) {
    Text("💣", modifier = modifier, fontSize = fontSize)
}

@Composable
actual fun MedalSymbol(place: Int, modifier: Modifier) {
    val medal = when (place) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> return
    }
    Text(medal, modifier = modifier, fontSize = 24.sp)
}

@Composable
actual fun EmojiNicknameSymbol(emoji: String, modifier: Modifier, fontSize: TextUnit, color: Color) {
    Text(emoji, modifier = modifier, fontSize = fontSize, color = color)
}

actual fun platformSupportedText(text: String): String = text
