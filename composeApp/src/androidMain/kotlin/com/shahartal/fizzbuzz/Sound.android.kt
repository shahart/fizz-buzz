package com.shahartal.fizzbuzz

import android.content.Context
import android.media.MediaPlayer

internal object AndroidSound {
    lateinit var context: Context
    var countdownPlayer: MediaPlayer? = null
    var failurePlayer: MediaPlayer? = null
    var winnerPlayer: MediaPlayer? = null
    val hasContext: Boolean get() = this::context.isInitialized
}

internal fun initializeAndroidSound(context: Context) {
    AndroidSound.context = context.applicationContext
}

actual fun playFailureSound() {
    if (!AndroidSound.hasContext) return
    AndroidSound.failurePlayer?.run {
        if (isPlaying) stop()
        release()
    }
    AndroidSound.failurePlayer = MediaPlayer.create(AndroidSound.context, R.raw.wrong_buzzer)?.apply {
        setVolume(0.4f, 0.4f)
        setOnCompletionListener { player ->
            player.release()
            if (AndroidSound.failurePlayer === player) AndroidSound.failurePlayer = null
        }
        start()
    }
}

actual fun stopFailureSound() {
    AndroidSound.failurePlayer?.run {
        if (isPlaying) stop()
        release()
    }
    AndroidSound.failurePlayer = null
}

actual fun prepareFailureSound() = Unit

actual fun playWinnerSound() {
    if (!AndroidSound.hasContext) return
    stopWinnerSound()
    AndroidSound.winnerPlayer = MediaPlayer.create(AndroidSound.context, R.raw.cute_happy_victory)?.apply {
        setVolume(0.4f, 0.4f)
        setOnCompletionListener { player ->
            player.release()
            if (AndroidSound.winnerPlayer === player) AndroidSound.winnerPlayer = null
        }
        start()
    }
}

actual fun stopWinnerSound() {
    AndroidSound.winnerPlayer?.run {
        if (isPlaying) stop()
        release()
    }
    AndroidSound.winnerPlayer = null
}

actual fun prepareWinnerSound() = Unit

actual fun playCountdownMusic(isCurrentPlayerTurn: Boolean) {
    val volume = if (isCurrentPlayerTurn) 0.05f else 0.05f
    AndroidSound.countdownPlayer?.let { player ->
        player.setVolume(volume, volume)
        if (!player.isPlaying) player.start()
        return
    }
    stopCountdownMusic()
    if (!AndroidSound.hasContext) return
    AndroidSound.countdownPlayer = MediaPlayer.create(AndroidSound.context, R.raw.countdown_music)?.apply {
        isLooping = true
        setVolume(volume, volume)
        start()
    }
}

actual fun stopCountdownMusic() {
    AndroidSound.countdownPlayer?.run {
        if (isPlaying) pause()
    }
}

actual val requiresUserSoundActivation: Boolean = false
