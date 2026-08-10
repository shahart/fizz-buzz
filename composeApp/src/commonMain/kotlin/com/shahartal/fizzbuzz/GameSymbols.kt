package com.shahartal.fizzbuzz

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit

@Composable
expect fun BombSymbol(modifier: Modifier = Modifier, fontSize: TextUnit)

@Composable
expect fun MedalSymbol(place: Int, modifier: Modifier = Modifier)

expect fun platformSupportedText(text: String): String
