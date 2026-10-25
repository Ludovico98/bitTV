package com.bittv.home.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isFocused
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
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.today.TodayInfo
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeFocusTest {
    @get:Rule val rule = createComposeRule()

    private var prefs by mutableStateOf(Prefs())

    private val actions = object : HomeActions {
        override fun openTitle(t: Title) {}
        override fun openApp(a: AppEntry) {}
        override fun openSearch() {}
        override fun openSettings() {}
        override fun toggleWatchLater(t: Title) {
            prefs = prefs.copy(watchLater = if (t.id in prefs.watchLater) prefs.watchLater - t.id else listOf(t.id) + prefs.watchLater)
        }
    }

    private fun setHome() = rule.setContent {
        BitTvTheme {
            HomeScreen(
                HomeState(prefs, DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps,
                    DemoCatalog.apps.map { it.id }.toSet() - "paramount", TodayInfo.items(LocalDate.of(2026, 10, 25)),
                    nowPlaying = null, news = emptyList(), weather = null),
                actions,
            )
        }
    }

    private fun focus(tag: String) { rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle() }
    private fun press(k: Key, times: Int = 1) = repeat(times) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    private fun focusedNode(): SemanticsNode = rule.onNode(isFocused()).fetchSemanticsNode()
    private fun tagsUp(n: SemanticsNode): List<String> {
        val out = mutableListOf<String>(); var c: SemanticsNode? = n
        while (c != null) { c.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }; c = c.parent }
        return out
    }
    private fun focusedTag() = tagsUp(focusedNode()).first()
    private fun focusedRow() = tagsUp(focusedNode()).firstOrNull { it.startsWith("row:") }
    private fun inRail() = "rail" in tagsUp(focusedNode())

    @Test
    fun downNeverEntersRail() {
        setHome()
        for (tile in listOf("tile:wl:longtide", "tile:wl:loworbit", "tile:cw:epidemic", "tile:cw:eclipse")) {
            for (k in listOf(Key.DirectionDown, Key.DirectionUp)) {
                focus(tile); press(k)
                assertFalse("$tile + $k went into the rail", inRail())
            }
        }
        // walk the whole page down and back up (lazy rows are built as they are reached)
        focus("tile:cw:eclipse")
        repeat(9) { press(Key.DirectionDown); assertFalse("Down #$it went into the rail", inRail()) }
        repeat(14) { press(Key.DirectionUp); assertFalse("Up #$it went into the rail", inRail()) }
    }

    @Test
    fun leftFromFirstTileEntersRail() {
        setHome(); focus("tile:cw:epidemic"); press(Key.DirectionLeft)
        assertTrue(inRail())
    }

    @Test
    fun leftFromSecondTileStaysInRow() {
        setHome(); focus("tile:cw:eclipse"); press(Key.DirectionLeft)
        assertEquals("tile:cw:epidemic", focusedTag())
    }

    @Test
    fun rightFromRailRestoresLastTile() {
        setHome(); focus("tile:cw:eclipse"); press(Key.DirectionLeft); press(Key.DirectionLeft)
        assertTrue(inRail())
        press(Key.DirectionDown, 2)
        press(Key.DirectionRight)
        assertEquals("tile:cw:epidemic", focusedTag())
    }

    @Test
    fun rightAtEndOfRowStaysInRow() {
        setHome(); focus("tile:wl:static"); press(Key.DirectionRight)
        assertEquals("row:wl", focusedRow())
    }

    @Test
    fun holdDownReachesLastRowWithoutSkipping() {
        setHome(); focus("tile:wl:longtide")
        val visited = mutableListOf(focusedRow())
        rule.mainClock.autoAdvance = false
        repeat(20) {
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.mainClock.advanceTimeBy(33)
            focusedRow()?.let { if (visited.last() != it) visited += it }
        }
        rule.mainClock.autoAdvance = true
        assertEquals(listOf("row:wl", "row:cw", "row:dev", "row:sug", "row:series", "row:movies", "row:comedy", "row:it", "row:free"), visited)
    }

    @Test
    fun removingFocusedWatchLaterKeepsFocusInRow() {
        setHome(); focus("tile:wl:loworbit")
        rule.runOnIdle { actions.toggleWatchLater(DemoCatalog.titles.first { it.id == "loworbit" }) }
        rule.waitForIdle()
        assertEquals("row:wl", focusedRow())
    }

    @Test
    fun bannerSurvivesSubscriptionChange() {
        setHome()
        rule.mainClock.advanceTimeBy(7_000L * 4)       // banner on its last slide
        rule.runOnIdle { prefs = prefs.copy(subs = setOf(Svc.TUBI)) }
        rule.waitForIdle()
        assertNotNull(rule.onNodeWithTag("hero").fetchSemanticsNode())
    }
}
