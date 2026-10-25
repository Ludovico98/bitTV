package com.bittv.home.ui

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.platform.app.InstrumentationRegistry
import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Prefs
import com.bittv.home.model.Title
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.theme.BitTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Hold OK on an app to lift it, Up/Down to move it, OK or Back to drop it (tvOS); the order is saved, nothing opens. */
class AppBarReorderTest {
    @get:Rule(order = 0) val noTouch = object : org.junit.rules.ExternalResource() {
        override fun before() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val opened = mutableListOf<String>()
    private val saved = mutableListOf<List<String>>()
    private val actions = object : HomeActions {
        override fun openTitle(t: Title) {}; override fun openApp(a: AppEntry) { opened += a.id }
        override fun openSearch() {}; override fun openSettings() {}; override fun toggleWatchLater(t: Title) {}
        override fun reorderApps(ids: List<String>) { saved += ids }
    }

    @Before fun setUp() {
        rule.setContent {
            BitTvTheme {
                HomeScreen(HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps,
                    DemoCatalog.apps.map { it.id }.toSet(), emptyList(), null, emptyList(), null), actions)
            }
        }
        // as on the TV: the content has focus first, then the app bar (reached with Left)
        rule.onNodeWithTag("hero").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
        rule.onNodeWithTag("app:prime").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
    }

    private fun hold(code: Int) {
        val t = SystemClock.uptimeMillis()
        inst.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        for (i in 1..4) { SystemClock.sleep(60); inst.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, i)) }
        inst.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0)); rule.waitForIdle()
    }
    private fun press(code: Int) { inst.sendKeyDownUpSync(code); rule.waitForIdle() }
    private fun railOrder(): List<String> = rule.onAllNodes(SemanticsMatcher("app tile") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("app:") == true
    }).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.TestTag].removePrefix("app:") }

    @Test fun holdOkThenDownMovesTheAppAndOkSavesIt() {
        hold(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.onNodeWithTag("rail:moving").assertExists()
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(listOf("netflix", "now", "prime"), railOrder().take(3))
        press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("netflix", "now", "prime"), saved.last().take(3))
        assertTrue("holding OK must not open the app: $opened", opened.isEmpty())
        rule.onAllNodes(SemanticsMatcher("moving") { it.config.getOrNull(SemanticsProperties.TestTag) == "rail:moving" })
            .fetchSemanticsNodes().let { assertTrue("move mode ended", it.isEmpty()) }
    }

    @Test fun backAlsoDropsItAndAShortPressStillOpens() {
        hold(KeyEvent.KEYCODE_DPAD_CENTER)
        press(KeyEvent.KEYCODE_DPAD_UP)
        press(KeyEvent.KEYCODE_BACK)
        assertEquals(listOf("prime", "netflix"), saved.last().take(2))
        press(KeyEvent.KEYCODE_DPAD_CENTER)   // a normal press now opens the app it's on
        assertEquals(listOf("prime"), opened)
    }
}
