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
import com.bittv.home.ui.details.NotInstalledScreen
import com.bittv.home.ui.search.SearchScreen
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Rule
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

class RootNavigationTest {
    private val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra(MainActivity.EXTRA_SKIP_BOOT, true)
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
