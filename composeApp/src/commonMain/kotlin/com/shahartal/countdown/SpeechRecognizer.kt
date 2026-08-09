package com.shahartal.countdown

import androidx.compose.runtime.Composable

interface SpeechRecognizerController {
    val isSupported: Boolean
    val hasDetectedSpeech: Boolean
    fun startListening()
    fun stopListening()
    fun consumeResults(): List<String>?
}

@Composable
expect fun rememberSpeechRecognizerController(): SpeechRecognizerController
