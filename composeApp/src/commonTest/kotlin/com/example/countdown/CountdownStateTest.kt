package com.example.countdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountdownStateTest {
    @Test
    fun startsAtSevenAndCountsDownToZero() {
        var state = CountdownState(number = 42)

        assertEquals(STARTING_SECONDS, state.secondsRemaining)
        assertFalse(state.hasTimedOut)

        repeat(STARTING_SECONDS) { state = state.tick() }

        assertEquals(0, state.secondsRemaining)
        assertTrue(state.hasTimedOut)
    }

    @Test
    fun neverCountsBelowZero() {
        val state = CountdownState(number = 42, secondsRemaining = 0).tick()

        assertEquals(0, state.secondsRemaining)
    }
}
