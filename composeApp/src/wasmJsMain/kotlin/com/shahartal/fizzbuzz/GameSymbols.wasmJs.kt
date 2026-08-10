package com.shahartal.fizzbuzz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import fizz_buzz.composeapp.generated.resources.Res
import fizz_buzz.composeapp.generated.resources.noto_emoji
import org.jetbrains.compose.resources.Font

@Composable
actual fun BombSymbol(modifier: Modifier, fontSize: TextUnit) {
    Canvas(modifier) {
        val unit = size.minDimension / 32f
        val center = Offset(14f * unit, 18f * unit)
        drawCircle(Color(0xFF16181D), radius = 10f * unit, center = center)
        drawCircle(Color(0xFF343942), radius = 7f * unit, center = Offset(11f * unit, 15f * unit))
        drawLine(
            color = Color(0xFF6D4C41),
            start = Offset(20f * unit, 10f * unit),
            end = Offset(25f * unit, 5f * unit),
            strokeWidth = 3f * unit,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFFF8A00),
            start = Offset(24f * unit, 6f * unit),
            end = Offset(28f * unit, 2f * unit),
            strokeWidth = 2f * unit,
            cap = StrokeCap.Round,
        )
        drawCircle(Color(0xFFFFD54F), radius = 2.5f * unit, center = Offset(28f * unit, 3f * unit))
    }
}

@Composable
actual fun MedalSymbol(place: Int, modifier: Modifier) {
    val medalColor = when (place) {
        1 -> Color(0xFFFFC928)
        2 -> Color(0xFFC7CED8)
        3 -> Color(0xFFCD7F32)
        else -> return
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / 32f
            val leftRibbon = Path().apply {
                moveTo(8f * unit, 1f * unit)
                lineTo(15f * unit, 1f * unit)
                lineTo(18f * unit, 16f * unit)
                lineTo(11f * unit, 17f * unit)
                close()
            }
            val rightRibbon = Path().apply {
                moveTo(17f * unit, 1f * unit)
                lineTo(24f * unit, 1f * unit)
                lineTo(21f * unit, 17f * unit)
                lineTo(14f * unit, 16f * unit)
                close()
            }
            drawPath(leftRibbon, Color(0xFFEA4335))
            drawPath(rightRibbon, Color(0xFF2F6FED))
            val center = Offset(16f * unit, 21f * unit)
            drawCircle(Color(0x55000000), 10.5f * unit, center + Offset(0f, unit))
            drawCircle(medalColor, 10f * unit, center)
            drawCircle(Color.White.copy(alpha = .55f), 8f * unit, center, style = Stroke(1.5f * unit))
        }
        Text(
            text = place.toString(),
            color = Color(0xFF102A43),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
actual fun EmojiNicknameSymbol(emoji: String, modifier: Modifier, fontSize: TextUnit, color: Color) {
    Text(
        text = emoji,
        modifier = modifier,
        fontFamily = androidx.compose.ui.text.font.FontFamily(Font(Res.font.noto_emoji)),
        fontSize = fontSize,
        color = color,
    )
}

actual fun platformSupportedText(text: String): String =
    text.replace("💣", "").replace("🥇", "").replace("🥈", "").replace("🥉", "")
