package com.example.countdown

import androidx.compose.runtime.Composable

interface SpeechRecognizerController {
    val isSupported: Boolean
    fun startListening()
    fun stopListening()
    fun consumeResult(): String?
}

@Composable
expect fun rememberSpeechRecognizerController(): SpeechRecognizerController
