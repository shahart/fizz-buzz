package com.example.countdown

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private class AndroidSpeechRecognizerController(
    private val context: Context,
) : SpeechRecognizerController, RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
        it.setRecognitionListener(this)
    }
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    private var result: String? = null
    private var active = false
    var requestPermission: () -> Unit = {}

    override val isSupported: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening() {
        result = null
        active = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermission()
            return
        }
        recognizer.startListening(intent)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted && active) recognizer.startListening(intent)
    }

    override fun stopListening() {
        active = false
        recognizer.cancel()
    }

    override fun consumeResult(): String? = result.also { result = null }

    fun destroy() {
        active = false
        recognizer.destroy()
    }

    override fun onResults(results: Bundle?) {
        result = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
    }

    override fun onError(error: Int) {
        if (active && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            recognizer.startListening(intent)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

@Composable
actual fun rememberSpeechRecognizerController(): SpeechRecognizerController {
    val context = LocalContext.current
    val controller = remember(context) { AndroidSpeechRecognizerController(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        controller::onPermissionResult,
    )
    controller.requestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    DisposableEffect(controller) { onDispose(controller::destroy) }
    return controller
}
