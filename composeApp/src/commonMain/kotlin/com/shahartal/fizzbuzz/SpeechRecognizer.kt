package com.shahartal.fizzbuzz

import androidx.compose.runtime.Composable

interface SpeechRecognizerController {
    val isSupported: Boolean
    val hasDetectedSpeech: Boolean
    fun startListening()
    fun stopListening()
    fun consumePartialTranscript(): String?
    fun consumeResults(): List<String>?
}

@Composable
expect fun rememberSpeechRecognizerController(): SpeechRecognizerController
