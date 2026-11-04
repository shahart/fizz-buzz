package com.shahartal.fizzbuzz

expect fun playFailureSound()

expect fun stopFailureSound()

expect fun prepareFailureSound()

expect fun playWinnerSound()

expect fun stopWinnerSound()

expect fun prepareWinnerSound()

expect fun playCountdownMusic(isCurrentPlayerTurn: Boolean)

expect fun stopCountdownMusic()

expect val requiresUserSoundActivation: Boolean
