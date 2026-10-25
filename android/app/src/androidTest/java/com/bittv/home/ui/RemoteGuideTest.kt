package com.bittv.home.ui

import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.bittv.home.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Settings → Remote shortcuts: a guide to the remote's buttons, closed by Back without leaving Settings. */
class RemoteGuideTest {
    private val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_SKIP_BOOT, true).putExtra(MainActivity.EXTRA_DEMO_CATALOG, true)
    @get:Rule val rule = AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(intent)) { r -> var a: MainActivity? = null; r.scenario.onActivity { a = it }; a!! }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }
    private fun focusedTags(): List<String> {
        val out = mutableListOf<String>(); var c: SemanticsNode? = rule.onNode(isFocused()).fetchSemanticsNode()
        while (c != null) { c.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }; c = c.parent }
        return out
    }
    private fun openGuide() {
        rule.onNodeWithTag("sc:Settings").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        press(Key.DirectionCenter)
        rule.onNodeWithTag("opt:remote").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        press(Key.DirectionCenter)
    }

    @Test fun remoteShortcutsShowsTheButtons() {
        openGuide()
        rule.onNodeWithTag("remote:guide").assertIsDisplayed()
        listOf("OK", "Back", "Home", "Mic", "Netflix", "YouTube", "Input").forEach {
            rule.onNodeWithTag("key:$it", useUnmergedTree = true).assertIsDisplayed()
        }
        rule.onNodeWithText("apps bar", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun backClosesTheGuideAndReturnsToItsButton() {
        openGuide()
        back()
        rule.onAllNodesWithTag("remote:guide").assertCountEquals(0)
        rule.onNodeWithTag("opt:animations").assertIsDisplayed()   // still in Settings
        assertEquals("opt:remote", focusedTags().first())
    }
}
