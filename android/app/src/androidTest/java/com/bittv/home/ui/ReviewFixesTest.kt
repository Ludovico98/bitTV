package com.bittv.home.ui

import android.content.Intent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assert
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.bittv.home.BitTvApp
import com.bittv.home.MainActivity
import com.bittv.home.model.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun tagsUp(n: SemanticsNode): List<String> {
    val out = mutableListOf<String>(); var c: SemanticsNode? = n
    while (c != null) { c.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }; c = c.parent }
    return out
}

/** Paths found by the final review, on the real app (screen stack, Back, Home key). */
class ReviewFixesAppTest {
    private val app get() = ApplicationProvider.getApplicationContext<BitTvApp>()
    private val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SKIP_BOOT, true).putExtra(MainActivity.EXTRA_DEMO_CATALOG, true)
    @get:Rule val rule = AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(intent)) { r -> var a: MainActivity? = null; r.scenario.onActivity { a = it }; a!! }

    @After fun restoreWatchLater() = runBlocking { app.graph.prefs.setWatchLater(Prefs().watchLater) }

    private fun setWatchLater(vararg ids: String) { runBlocking { app.graph.prefs.setWatchLater(ids.toList()) }; rule.waitForIdle() }
    private fun focusedTags(): List<String>? = rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let(::tagsUp)
    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle() }

    private fun removeFromDetailsAndComeBack(tile: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tile).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(tile).performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        press(Key.DirectionCenter)
        rule.onNodeWithTag("watchlater").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(tile).fetchSemanticsNodes().isEmpty() }
        back()
    }

    @Test
    fun anAppSwitchedOffInSettingsLeavesTheAppBarAndComesBack() {
        rule.onNodeWithTag("app:spotify").assertExists()
        rule.onNodeWithTag("sc:Settings").performClick()
        val sw = rule.onNodeWithTag("opt:app:spotify")
        sw.assert(androidx.compose.ui.test.isOn()).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("app:spotify").fetchSemanticsNodes().isEmpty() }   // Home, underneath
        sw.assert(androidx.compose.ui.test.isOff()).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("app:spotify").fetchSemanticsNodes().isNotEmpty() }
        back()
        rule.onNodeWithTag("app:spotify").assertExists()
    }

    @Test
    fun watchLaterRemovedFromDetailsKeepsFocusInTheRow() {
        setWatchLater("loworbit", "grandeur")
        removeFromDetailsAndComeBack("tile:wl:loworbit")
        val tags = focusedTags()
        assertTrue("nothing focused after Back", tags != null)
        assertEquals("tile:wl:grandeur", tags!!.first())
    }

    @Test
    fun lastWatchLaterRemovedStillLeavesFocusOnHome() {
        setWatchLater("loworbit")
        removeFromDetailsAndComeBack("tile:wl:loworbit")
        val tags = focusedTags()
        assertTrue("nothing focused after Back", tags != null)
        assertFalse("focus ended in the app bar", "rail" in tags!!)
        // and the app bar can still be left with Right
        press(Key.DirectionLeft); press(Key.DirectionRight)
        assertFalse("stuck in the app bar", "rail" in (focusedTags() ?: listOf("rail")))
    }

    @Test
    fun searchQuerySurvivesOpeningAResult() {
        rule.onNodeWithTag("sc:Search").performClick()
        rule.onNodeWithTag("key:c").performClick(); rule.onNodeWithTag("key:r").performClick()
        rule.onNodeWithTag("result:0").performClick()
        rule.onNodeWithTag("play").assertExists()
        back()
        rule.onNodeWithTag("query").assert(hasText("cr"))
    }

    @Test
    fun theMicOnHomeNoLongerAnswersInTheActiveSection() {
        press(Key.M); press(Key.Search)
        rule.mainClock.advanceTimeBy(1_000); rule.waitForIdle()
        rule.onAllNodesWithTag("assist").assertCountEquals(0)
        rule.onNodeWithTag("ctl:play").assertExists()   // Spotify, the news and the day stay in the box
    }

    @Test
    fun settingsHasNoGoogleAssistantSection() {
        rule.onNodeWithTag("sc:Settings").performClick()
        rule.onNodeWithTag("opt:animations").assertExists()
        rule.onAllNodesWithText("Google Assistant", substring = true, ignoreCase = true).assertCountEquals(0)
        rule.onAllNodesWithTag("opt:voice").assertCountEquals(0)
        rule.onAllNodesWithText("Hear a sample").assertCountEquals(0)
    }

    @Test
    fun backFromSettingsReturnsToTheSettingsButton() {
        rule.onNodeWithTag("sc:Settings").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        press(Key.DirectionCenter)
        rule.onNodeWithTag("opt:animations").assertExists()
        back()
        assertEquals("sc:Settings", focusedTags()?.first())
    }

    @Test
    fun homeKeyInsideBitTvGoesHome() {
        rule.onNodeWithTag("sc:Search").performClick()
        rule.onNodeWithTag("query").assertExists()
        rule.runOnUiThread { rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).addCategory(Intent.CATEGORY_HOME)) }
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("query").fetchSemanticsNodes().isEmpty() }
        assertEquals("hero", focusedTags()?.first())
    }
}
