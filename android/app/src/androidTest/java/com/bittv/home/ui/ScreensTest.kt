package com.bittv.home.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import android.content.Intent
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bittv.home.MainActivity
import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.Prefs
import androidx.compose.ui.test.onAllNodesWithText
import com.bittv.home.ui.details.DetailsScreen
import com.bittv.home.ui.details.NotInstalledScreen
import com.bittv.home.ui.search.SearchScreen
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Rule
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.bittv.home.search.Related
import org.junit.Test

class SearchScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun setSearch() = rule.setContent {
        BitTvTheme { SearchScreen(DemoCatalog.titles, Prefs().subs, DemoCatalog.apps, onOpen = {}, onBack = {}) }
    }

    @Test
    fun typingOnKeyboardFiltersResults() {
        setSearch()
        rule.onNodeWithTag("key:c").performClick()
        rule.onNodeWithTag("key:r").performClick()
        rule.onNodeWithTag("query").assert(hasText("cr"))
        // a title containing what was typed ranks first, then crime titles by rating
        rule.onNode(hasTestTag("result:0") and SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hollow Crown Hill"))).assertExists()
        rule.onNode(hasTestTag("result:1") and SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Tenements"))).assertExists()
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    @Test
    fun theSpeakButtonHasFocusWhenSearchOpensAndDownGoesToTheKeyboard() {
        setSearch()
        rule.waitForIdle()
        rule.onNodeWithTag("mic").assertIsFocused()
        press(Key.DirectionDown)
        rule.onNodeWithTag("key:q").assertIsFocused()
    }

    @Test
    fun matchesOffHisAppsGoInThePurchaseColumnAndDownFromSpeakReachesIncluded() {
        rule.setContent { BitTvTheme { SearchScreen(DemoCatalog.titles, Prefs().subs, DemoCatalog.apps, onOpen = {}, onBack = {}, query = "harbour") } }
        rule.waitForIdle()
        rule.onNodeWithTag("col:result").assertExists()
        rule.onNode(hasTestTag("purchase:0") and SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Red Harbour"))).assertExists()
        rule.onNodeWithText("Paramount+", useUnmergedTree = true).assertExists()   // how he could get it
        rule.onNode(hasTestTag("result:0") and SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Harbour Lights"))).assertExists()
        rule.onAllNodesWithText("Similar Content").assertCountEquals(1)
        press(Key.DirectionDown)
        rule.onNodeWithTag("result:0").assertIsFocused()
    }

    @Test
    fun downFromSpeakLandsOnTheBestMatchEvenInALongColumn() {
        // every title is a match ("e") and none is on his apps: a long Purchase column, Included empty
        rule.setContent { BitTvTheme { SearchScreen(DemoCatalog.titles, emptySet(), DemoCatalog.apps, onOpen = {}, onBack = {}, query = "e") } }
        rule.waitForIdle()
        press(Key.DirectionDown)
        rule.onNodeWithTag("purchase:0").assertIsFocused()
        // Down walks the column a row at a time
        press(Key.DirectionDown)
        rule.onNodeWithTag("purchase:2").assertIsFocused()
    }

    @Test
    fun speakButtonIsTheOnlyMic() {
        setSearch()
        rule.onAllNodesWithTag("mic").assertCountEquals(1)
    }
}

class NotInstalledScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun notInstalledShowsTextButtonOnly() {
        val app = DemoCatalog.apps.first { it.id == "paramount" }
        rule.setContent { BitTvTheme { NotInstalledScreen(app, onInstall = {}, onBack = {}) } }
        rule.onNodeWithText("Paramount+ isn’t installed").assertExists()
        rule.onNodeWithText("Install Paramount+").assertExists()
        rule.onAllNodesWithTag("icon").assertCountEquals(0)   // no Play Store artwork
    }
}

class DetailsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun onlyPlayAndWatchLaterNoBackButtonOrAvailableOn() {
        val title = DemoCatalog.titles.first()
        rule.setContent { BitTvTheme { DetailsScreen(title, Prefs(), null, DemoCatalog.apps, onPlay = {}, onToggleWatchLater = {}) } }
        rule.onNodeWithTag("play").assertExists()
        rule.onNodeWithTag("watchlater").assertExists()
        rule.onAllNodesWithText("Back").assertCountEquals(0)          // the remote's Back goes back
        rule.onAllNodesWithText("AVAILABLE ON").assertCountEquals(0)  // the Play button already names the app
        rule.onAllNodesWithText("Subscribed", substring = true).assertCountEquals(0)
    }

    private val opened = mutableListOf<String>()
    private val title = DemoCatalog.titles.first()
    private val related = Related.to(title, DemoCatalog.titles, Prefs().subs)
    private fun setDetails() = rule.setContent {
        BitTvTheme { DetailsScreen(title, Prefs(), null, DemoCatalog.apps, onPlay = {}, onToggleWatchLater = {}, related = related, onOpen = { opened += it.id }) }
    }
    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun onScreen(tag: String): Boolean {
        val n = node(tag); val h = rule.onRoot().fetchSemanticsNode().size.height
        return n.positionInRoot.y >= 0 && n.positionInRoot.y + n.size.height <= h
    }

    @Test
    fun downFromTheButtonsGoesToSimilarTitlesWhichOpenTheirPage() {
        assertTrue(related.size > 4)
        setDetails()
        press(Key.DirectionDown)
        rule.onNodeWithTag("related:0").assertIsFocused()
        assertTrue("the first similar title is fully on screen", onScreen("related:0"))
        press(Key.DirectionCenter)
        assertEquals(listOf(related[0].id), opened)
    }

    @Test
    fun theSimilarTitlesScrollVerticallyAndUpComesBackToTheButtons() {
        setDetails()
        press(Key.DirectionDown); press(Key.DirectionDown)
        val focused = rule.onNode(isFocused()).fetchSemanticsNode().config[SemanticsProperties.TestTag]
        assertTrue("Down again stays in the grid, got $focused", focused.startsWith("related:") && focused != "related:0")
        assertTrue("$focused is scrolled into view", onScreen(focused))
        press(Key.DirectionUp); press(Key.DirectionUp)
        rule.onNodeWithTag("play").assertIsFocused()
        assertTrue("the title's page is back at its top", onScreen("play"))
    }

    /** Google TV's own scroll rule: the focused thing is held 30 % down the screen. */
    @OptIn(ExperimentalFoundationApi::class)
    private val pivot = object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = offset - 0.3f * containerSize
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun underTheTvsScrollRuleThePageStillOpensAtItsTopAndComesBackToIt() {
        rule.setContent { BitTvTheme { CompositionLocalProvider(LocalBringIntoViewSpec provides pivot) {
            DetailsScreen(title, Prefs(), null, DemoCatalog.apps, onPlay = {}, onToggleWatchLater = {}, related = related, onOpen = {})
        } } }
        val kicker = title.services.first().label.uppercase()
        fun kickerTop() = rule.onNodeWithText(kicker).fetchSemanticsNode().positionInRoot.y
        val atOpen = kickerTop()
        assertEquals("the kicker stays where it is laid out (112 dp down)", with(rule.density) { 112.dp.toPx() }, atOpen, with(rule.density) { 4.dp.toPx() })
        press(Key.DirectionDown); press(Key.DirectionDown); press(Key.DirectionUp); press(Key.DirectionUp)
        rule.onNodeWithTag("play").assertIsFocused()
        assertEquals(atOpen, kickerTop(), 1f)
    }

    @Test
    fun theSimilarTitlesAreLaidOutLikeTheSearchGrid() {
        setDetails()
        val a = node("related:0"); val right = node("related:1")
        val below = (1 until related.size).map { node("related:$it") }.first { it.positionInRoot.y > a.positionInRoot.y }
        val across = right.positionInRoot.x - (a.positionInRoot.x + a.size.width)
        val down = below.positionInRoot.y - (a.positionInRoot.y + a.size.height)
        assertEquals(across, down, 1f)
        assertEquals(with(rule.density) { 110.dp.toPx() }, a.size.width.toFloat(), 1f)   // the Search page's tile
    }
}

class RootNavigationTest {
    private val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SKIP_BOOT, true).putExtra(MainActivity.EXTRA_DEMO_CATALOG, true)
    @get:Rule val rule = AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(intent)) { r ->
        var a: MainActivity? = null; r.scenario.onActivity { a = it }; a!!
    }

    @Test
    fun backFromSearchReturnsToHome() {
        rule.onNodeWithTag("sc:Search").performClick()
        rule.onNodeWithTag("query").assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("hero").assertExists()
        rule.onAllNodesWithTag("query").assertCountEquals(0)
    }

    @Test
    fun animationsSwitchPersists() {
        rule.onNodeWithTag("sc:Settings").performClick()
        val on = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On)
        val off = SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off)
        fun waitFor(m: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("opt:animations") and m).fetchSemanticsNodes().isNotEmpty() }
        waitFor(on)
        rule.onNodeWithTag("opt:animations").performClick()
        waitFor(off)                                   // saved in the background
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("sc:Settings").performClick()
        waitFor(off)                                   // still off after the activity is recreated
        rule.onNodeWithTag("opt:animations").performClick()   // leave the device as we found it
        waitFor(on)
    }
}
