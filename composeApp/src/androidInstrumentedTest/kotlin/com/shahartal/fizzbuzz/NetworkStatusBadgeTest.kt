package com.shahartal.fizzbuzz

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NetworkStatusBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labelUpdatesWhenNetworkConnectivityChanges() {
        composeRule.setContent {
            var online by remember { mutableStateOf(false) }
            NetworkStatusBadge(networkOnline = online)
            TextButton(onClick = { online = true }) { Text("Go online") }
        }

        val offlineLabel = labelText()
        assertTrue(offlineLabel in setOf("Offline", "לא מקוון"))

        composeRule.onNodeWithText("Go online").performClick()

        val onlineLabel = labelText()
        assertTrue(onlineLabel in setOf("Online", "מקוון"))
        assertNotEquals(offlineLabel, onlineLabel)
    }

    @Test
    fun popupAnnouncesBothOfflineAndRestoredConnectivity() {
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            var online by remember { mutableStateOf<Boolean?>(false) }
            Box(Modifier.fillMaxSize()) {
                SnackbarHost(hostState)
                NetworkStatusPopup(online, hostState)
                TextButton(onClick = { online = true }) { Text("Go online") }
            }
        }

        assertAnyText("You are offline. Continuing locally.", "אין חיבור לאינטרנט. ממשיכים לשחק מקומית.")
        composeRule.onNodeWithText("Go online").performClick()
        assertAnyText("You’re back online.", "החיבור לאינטרנט חזר.")
    }

    private fun labelText(): String = composeRule
        .onNodeWithTag("network-status")
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(separator = "") { it.text }

    private fun assertAnyText(vararg choices: String) {
        composeRule.waitUntil(5_000) {
            choices.any { composeRule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
