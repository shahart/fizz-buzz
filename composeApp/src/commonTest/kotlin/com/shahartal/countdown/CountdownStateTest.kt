package com.shahartal.countdown

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

    @Test
    fun boomNumbersRequireBoom() {
        for (number in listOf(7, 14, 17, 70, 97)) {
            assertTrue("boom".matchesAnswerFor(number))
            assertTrue("BOOM!".matchesAnswerFor(number))
            assertTrue("boom boom".matchesAnswerFor(number))
            assertFalse(number.toString().matchesAnswerFor(number))
        }
    }

    @Test
    fun regularNumbersAcceptDigitsAndEnglishWords() {
        assertTrue("43".matchesAnswerFor(43))
        assertTrue("forty three".matchesAnswerFor(43))
        assertTrue("חמש".matchesAnswerFor(5))
        assertTrue("חמישה".matchesAnswerFor(5))
        assertTrue("חמישים וארבע".matchesAnswerFor(54))
        assertTrue("ארבעים ושלושה".matchesAnswerFor(43))
        assertTrue("טוונטי".matchesAnswerFor(20))
        assertTrue("טווניטי".matchesAnswerFor(20))
        assertTrue("ניין פור".matchesAnswerFor(94))
        assertTrue("nine four".matchesAnswerFor(94))
        assertTrue("פייב פור".matchesAnswerFor(54))
        assertTrue("אחד אחד".matchesAnswerFor(11))
        assertTrue("אחת אחת".matchesAnswerFor(11))
        assertTrue("חמש ארבע".matchesAnswerFor(54))
        assertFalse("boom".matchesAnswerFor(43))
        assertFalse("42".matchesAnswerFor(43))
    }

    @Test
    fun correctAnswerAdvancesAndResetsTimer() {
        val next = CountdownState(number = 41, secondsRemaining = 2).answer("forty-one")

        assertEquals(42, next.number)
        assertEquals(STARTING_SECONDS, next.secondsRemaining)
        assertFalse(next.hasFailed)
    }

    @Test
    fun wrongAnswerFailsRound() {
        val failed = CountdownState(number = 42).answer("43")

        assertTrue(failed.hasFailed)
        assertTrue(failed.isFinished)
    }
}
