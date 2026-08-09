package com.example.countdown

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
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
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
        it.setRecognitionListener(this)
    }
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
    }
    private var results: List<String>? = null
    private var latestPartial: String? = null
    override var hasDetectedSpeech: Boolean = false
        private set
    private var active = false
    private var permissionRequestPending = false
    private var destroyed = false
    private val startRunnable = Runnable { beginListening() }
    var requestPermission: () -> Unit = {}

    override val isSupported: Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun startListening() {
        results = null
        latestPartial = null
        hasDetectedSpeech = false
        active = true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!permissionRequestPending) {
                permissionRequestPending = true
                requestPermission()
            }
            return
        }
        scheduleStart(delayMillis = 0L)
    }

    fun onPermissionResult(granted: Boolean) {
        permissionRequestPending = false
        if (granted && active) {
            scheduleStart(delayMillis = 0L)
        } else if (!granted) {
            active = false
            Log.w(TAG, "Microphone permission was denied")
        }
    }

    override fun stopListening() {
        active = false
        handler.removeCallbacks(startRunnable)
        recognizer.cancel()
    }

    override fun consumeResults(): List<String>? = results.also { results = null }

    fun destroy() {
        active = false
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        recognizer.destroy()
    }

    override fun onResults(results: Bundle?) {
        val recognized = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter(String::isNotBlank)
        if (!recognized.isNullOrEmpty()) {
            this.results = recognized
            active = false
            handler.removeCallbacks(startRunnable)
        } else if (active) {
            scheduleStart(RETRY_DELAY_MILLIS)
        }
    }

    override fun onError(error: Int) {
        Log.w(TAG, "Speech recognition error: ${errorName(error)} ($error)")
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> active = false
            SpeechRecognizer.ERROR_CLIENT -> if (active) scheduleStart(RETRY_DELAY_MILLIS)
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> if (active) scheduleStart(BUSY_RETRY_DELAY_MILLIS)
            else -> if (active) scheduleStart(RETRY_DELAY_MILLIS)
        }
    }

    private fun scheduleStart(delayMillis: Long) {
        if (!active || destroyed) return
        handler.removeCallbacks(startRunnable)
        handler.postDelayed(startRunnable, delayMillis)
    }

    private fun beginListening() {
        if (!active || destroyed) return
        try {
            recognizer.startListening(intent)
            Log.d(TAG, "Speech recognition listening")
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to start speech recognition", exception)
            scheduleStart(BUSY_RETRY_DELAY_MILLIS)
        }
    }

    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        else -> "unknown"
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() {
        hasDetectedSpeech = true
    }
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        latestPartial?.let { partial ->
            handler.postDelayed({
                if (active && results == null && latestPartial == partial) {
                    results = listOf(partial)
                    active = false
                    recognizer.cancel()
                }
            }, PARTIAL_RESULT_FALLBACK_MILLIS)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        latestPartial = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull { it.isNotBlank() }
    }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        private const val TAG = "FizzBuzzSpeech"
        private const val RETRY_DELAY_MILLIS = 250L
        private const val BUSY_RETRY_DELAY_MILLIS = 650L
        private const val PARTIAL_RESULT_FALLBACK_MILLIS = 500L
    }
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
