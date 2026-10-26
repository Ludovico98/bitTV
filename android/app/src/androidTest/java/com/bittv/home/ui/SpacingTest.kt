package com.bittv.home.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Prefs
import com.bittv.home.model.Title
import com.bittv.home.today.TodayInfo
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.search.SearchScreen
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Even room around the cards: measured unfocused (a focused tile is scaled up), unclipped (positionInRoot + size). */
class SpacingTest {
    @get:Rule val rule = createComposeRule()

    private val actions = object : HomeActions {
        override fun openTitle(t: Title) {}
        override fun openApp(a: AppEntry) {}
        override fun openSearch() {}
        override fun openSettings() {}
        override fun toggleWatchLater(t: Title) {}
    }

    private fun setHome() = rule.setContent {
        BitTvTheme {
            HomeScreen(HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps,
                DemoCatalog.apps.map { it.id }.toSet() - "paramount", TodayInfo.items(LocalDate.of(2026, 10, 25)),
                nowPlaying = null, news = emptyList(), weather = null), actions)
        }
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun tiles(prefix: String) = rule.onAllNodes(SemanticsMatcher(prefix) { n ->
        n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }, useUnmergedTree = true).fetchSemanticsNodes()
    private val SemanticsNode.top get() = positionInRoot.y
    private val SemanticsNode.bottom get() = positionInRoot.y + size.height
    private val SemanticsNode.left get() = positionInRoot.x
    private fun dp(px: Float) = with(rule.density) { px.toDp().value }
    private fun near(expected: Float, actual: Float, what: String) = assertEquals(what, expected, actual, 0.6f)

    @Test
    fun everyRowsTilesSitTheSameDistanceFromEachEdgeOfTheirStrip() {
        setHome()
        rule.onNodeWithTag("tile:cw:epidemic").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        press(Key.DirectionDown); press(Key.DirectionUp)   // the Devices row on screen, nothing focused in wl or dev
        for (row in listOf("wl", "dev")) {
            val strip = node("strip:$row")
            val ts = tiles("tile:$row:")
            val tall = ts.maxBy { it.size.height }
            val first = ts.minBy { it.left }
            val top = dp(tall.top - strip.top); val bottom = dp(strip.bottom - tall.bottom)
            near(top, bottom, "$row: $top dp above its tiles, $bottom dp below")
            near(top, dp(first.left - strip.left) - com.bittv.home.ui.home.RowStartPad.value, "$row: the tiles' left inset")
        }
    }

    @Test
    fun theRowsAreAsFarApartAsTheFirstRowIsFromTheBanner() {
        setHome()
        val fromBanner = dp(node("strip:wl").top - node("hero").bottom)
        val betweenRows = dp(node("strip:cw").top - node("strip:wl").bottom)
        near(fromBanner, betweenRows, "the first row starts $fromBanner dp under the banner, rows are $betweenRows dp apart")
    }

    @Test
    fun theSearchGridLeavesTheSameGapAcrossAndDown() {
        rule.setContent { BitTvTheme { SearchScreen(DemoCatalog.titles, Prefs().subs, DemoCatalog.apps, onOpen = {}, onBack = {}) } }
        val a = node("result:0"); val right = node("result:1"); val below = node("result:4")
        near(dp(right.left - (a.left + a.size.width)), dp(below.top - a.bottom), "across vs down")
    }
}
