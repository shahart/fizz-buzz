package com.example.countdown

const val STARTING_SECONDS = 7

data class CountdownState(
    val number: Int,
    val secondsRemaining: Int = STARTING_SECONDS,
) {
    val hasTimedOut: Boolean
        get() = secondsRemaining == 0

    fun tick(): CountdownState = copy(
        secondsRemaining = (secondsRemaining - 1).coerceAtLeast(0),
    )
}
