package com.bittv.home.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Prefs
import com.bittv.home.model.Title
import com.bittv.home.platform.HdmiInput
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Rows move like the app bar's apps (hold OK on a tile, Up/Down, OK or Back), and the Inputs menu. */
class RowReorderTest {
    @get:Rule(order = 0) val noTouch = object : org.junit.rules.ExternalResource() {
        override fun before() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private val saved = mutableListOf<List<String>>()
    private val opened = mutableListOf<String>()
    private val switched = mutableListOf<String>()
    private val actions = object : HomeActions {
        override fun openTitle(t: Title) { opened += t.id }
        override fun openApp(a: AppEntry) {}
        override fun openSearch() {}
        override fun openSettings() {}
        override fun toggleWatchLater(t: Title) {}
        override fun reorderRows(keys: List<String>) { saved += keys }
        override fun openInput(inputId: String) { switched += inputId }
        override suspend fun findInputs() = DemoCatalog.inputs
    }

    @Before fun setUp() {
        rule.setContent {
            BitTvTheme {
                HomeScreen(HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps,
                    DemoCatalog.apps.map { it.id }.toSet(), emptyList(), null, emptyList(), null), actions)
            }
        }
    }

    private fun focus(tag: String) { rule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle() }
    private fun hold(code: Int) {
        val t = SystemClock.uptimeMillis()
        inst.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        for (i in 1..4) { SystemClock.sleep(60); inst.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, i)) }
        inst.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0)); rule.waitForIdle()
    }
    private fun press(code: Int) { inst.sendKeyDownUpSync(code); rule.waitForIdle() }
    /** The row keys from top to bottom, as laid out. */
    private fun rowOrder(): List<String> = rule.onAllNodes(SemanticsMatcher("row") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.let { t -> t.startsWith("row:") && t != "row:moving" } == true
    }).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }   // the list keeps a few rows composed off screen, unplaced
        .sortedBy { it.positionInRoot.y }.map { it.config[SemanticsProperties.TestTag].removePrefix("row:") }

    @Test fun holdOkOnATileThenDownMovesTheRowAndOkSavesIt() {
        focus("tile:cw:epidemic")
        hold(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.onNodeWithTag("row:moving", useUnmergedTree = true).assertExists()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(listOf("wl", "comedy", "cw"), rowOrder().take(3))
        rule.onNodeWithTag("tile:cw:epidemic").assertIsFocused()   // the tile he held comes along
        // and stays on screen: the page follows the row to its new place
        SystemClock.sleep(600); rule.waitForIdle()
        val tile = rule.onNodeWithTag("tile:cw:epidemic").fetchSemanticsNode()
        val h = rule.onRoot().fetchSemanticsNode().size.height
        assertTrue("held tile at ${tile.positionInRoot.y}..${tile.positionInRoot.y + tile.size.height} of $h",
            tile.positionInRoot.y >= 0 && tile.positionInRoot.y + tile.size.height <= h)
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        val shown = setOf("wl", "comedy", "cw")
        assertEquals(listOf("wl", "comedy", "cw"), saved.last().filter { it in shown })
        // every row is in the saved order, also those with nothing to show today (they keep their place)
        assertEquals(com.bittv.home.ui.home.DEFAULT_ROW_ORDER.toSet(), saved.last().toSet())
        assertTrue("holding OK must not open the title: $opened", opened.isEmpty())
        assertTrue("move mode ended", rule.onAllNodesWithTag("row:moving", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        press(KeyEvent.KEYCODE_DPAD_CENTER)   // a normal press opens the title again
        assertEquals(listOf("epidemic"), opened)
    }

    @Test fun backAlsoPlacesTheRow() {
        focus("tile:cw:epidemic")
        hold(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_BACK)
        assertEquals(listOf("cw", "wl"), saved.last().take(2))
        assertEquals(listOf("cw", "wl"), rowOrder().take(2))
    }

    @Test fun theInputsButtonListsWhatIsPluggedInByNameElseByPortAndOkSwitches() {
        rule.onNodeWithTag("sc:Inputs").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("inputs:menu").assertExists()
        rule.onNodeWithText("PlayStation 5").assertExists()
        rule.onNodeWithText("HDMI 1").assertExists()
        rule.onNodeWithTag("inputs:demo/HDMI200004").assertIsFocused()
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("demo/HDMI200004"), switched)
        assertTrue("the menu closed", rule.onAllNodesWithTag("inputs:menu").fetchSemanticsNodes().isEmpty())
    }
}
