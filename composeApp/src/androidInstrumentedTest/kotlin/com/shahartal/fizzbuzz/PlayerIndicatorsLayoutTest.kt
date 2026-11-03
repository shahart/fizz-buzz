package com.shahartal.fizzbuzz

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerIndicatorsLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactHebrewIndicatorsUseRtlOrderAndFitANarrowScreen() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoxWithConstraints(Modifier.width(180.dp)) {
                    PlayerIndicators(
                        connectedPlayers = 2,
                        currentPlayerEmoji = "🐸",
                        yourEmoji = "🚀",
                        currentPlayerLabel = "השחקן הנוכחי",
                        youLabel = "אתם",
                        compact = true,
                    )
                }
            }
        }

        composeRule.onNodeWithText("השחקן הנוכחי").assertIsDisplayed()
        composeRule.onNodeWithText("אתם").assertIsDisplayed()
        val currentBounds = composeRule.onNodeWithTag("current-player-emoji").fetchSemanticsNode().boundsInRoot
        val yourBounds = composeRule.onNodeWithTag("your-emoji").fetchSemanticsNode().boundsInRoot
        val widthPx = with(composeRule.density) { 180.dp.toPx() }
        assertTrue("Current player should appear to the right in RTL", currentBounds.center.x > yourBounds.center.x)
        assertTrue("Current player emoji should fit within the narrow layout", currentBounds.left >= 0f && currentBounds.right <= widthPx)
        assertTrue("Your emoji should fit within the narrow layout", yourBounds.left >= 0f && yourBounds.right <= widthPx)
    }
}
