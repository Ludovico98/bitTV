package com.bittv.home.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.bittv.home.assistant.AssistantState
import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Prefs
import com.bittv.home.model.Title
import com.bittv.home.ui.assistant.AssistantArea
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.theme.BitTvTheme
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
    fun backClosesAnOpenPageBeforeAHiddenAnswer() {
        press(Key.M)
        rule.waitUntil(8_000) { rule.onAllNodesWithTag("assist").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("sc:Search").performClick()
        rule.onNodeWithTag("query").assertExists()   // also lets the click's recomposition finish before Back
        back()
        rule.onAllNodesWithTag("query").assertCountEquals(0)
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

/** Dismissing the Assistant while it has focus must hand focus back to the controls it covered. */
class AssistantFocusTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun dismissingAFocusedAnswerReturnsFocusToTheControls() {
        var showing by mutableStateOf(false)
        val actions = object : HomeActions {
            override fun openTitle(t: Title) {}; override fun openApp(a: AppEntry) {}
            override fun openSearch() {}; override fun openSettings() {}; override fun toggleWatchLater(t: Title) {}
        }
        rule.setContent {
            BitTvTheme {
                HomeScreen(HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps, emptySet(), emptyList(), null, emptyList(), null),
                    actions, assistantOverlay = if (showing) { { AssistantArea(AssistantState.Listening("hi"), onDismiss = { showing = false }) } } else null)
            }
        }
        rule.onNodeWithTag("ctl:play").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        showing = true; rule.waitForIdle()
        assertEquals("assist", rule.onNode(isFocused()).fetchSemanticsNode().let(::tagsUp).first())
        rule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }; rule.waitForIdle()
        assertEquals("ctl:play", rule.onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let(::tagsUp)?.first())
    }
}
