package com.example.countdown

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

actual fun playTimeoutSound() {
    val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 700)
    Handler(Looper.getMainLooper()).postDelayed(tone::release, 800)
}

actual fun prepareTimeoutSound() = Unit

actual val requiresUserSoundActivation: Boolean = false
