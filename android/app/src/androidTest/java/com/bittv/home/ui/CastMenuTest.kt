package com.bittv.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.AppEntry
import com.bittv.home.model.CastDevice
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

class CastMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val devices = listOf(
        CastDevice("iphone", "Ludovico’s iPhone", CastDevice.Kind.IPHONE),
        CastDevice("pixel", "Pixel 9", CastDevice.Kind.ANDROID),
    )
    private val actions = object : HomeActions {
        override fun openTitle(t: Title) {}; override fun openApp(a: AppEntry) {}
        override fun openSearch() {}; override fun openSettings() {}; override fun toggleWatchLater(t: Title) {}
    }

    @Before fun setUp() {
        rule.setContent {
            BitTvTheme {
                HomeScreen(HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps, emptySet(), emptyList(),
                    null, emptyList(), null, castDevices = devices, tvName = "Living Room TV"), actions)
            }
        }
        rule.onNodeWithTag("sc:Cast").performSemanticsAction(SemanticsActions.RequestFocus); rule.waitForIdle()
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }
    private fun focusedTags(): List<String> {
        val out = mutableListOf<String>(); var c: SemanticsNode? = rule.onNode(isFocused()).fetchSemanticsNode()
        while (c != null) { c.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }; c = c.parent }
        return out
    }

    @Test fun pressingCastOpensTheDeviceListWithNames() {
        press(Key.DirectionCenter)
        rule.onNodeWithTag("cast:menu").assertIsDisplayed()
        rule.onNodeWithText("Ludovico’s iPhone").assertIsDisplayed()
        rule.onNodeWithText("Pixel 9").assertIsDisplayed()
        rule.onNodeWithText("iPhone · AirPlay").assertIsDisplayed()
        rule.onNodeWithText("Android · Google Cast").assertIsDisplayed()
        assertEquals("cast:iphone", focusedTags().first())
    }

    @Test fun focusStaysInsideTheMenu() {
        press(Key.DirectionCenter)
        press(Key.DirectionUp); press(Key.DirectionLeft); press(Key.DirectionRight)
        assertEquals("cast:iphone", focusedTags().first())
        press(Key.DirectionDown); press(Key.DirectionDown); press(Key.DirectionDown); press(Key.DirectionDown)
        assertTrue("still in the menu", "cast:menu" in focusedTags())
    }

    @Test fun backClosesTheMenuAndReturnsToCast() {
        press(Key.DirectionCenter)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; rule.waitForIdle()
        rule.onAllNodesWithTag("cast:menu").assertCountEquals(0)
        assertEquals("sc:Cast", focusedTags().first())
    }

    @Test fun choosingADeviceSaysWhatToDoOnIt() {
        press(Key.DirectionCenter)
        press(Key.DirectionCenter)
        rule.onNodeWithTag("cast:howto", useUnmergedTree = true).assertIsDisplayed()
            .assertTextContains("Screen Mirroring", substring = true).assertTextContains("Living Room TV", substring = true)
    }
}
