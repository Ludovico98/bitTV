package com.bittv.home.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Debug
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bittv.home.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
private val appContext get() = ApplicationProvider.getApplicationContext<android.content.Context>()
private fun shell(cmd: String) = instrumentation.uiAutomation.executeShellCommand(cmd).close()

/**
 * The TCL 55C6K-UK runs Google TV 14 (API 34) on a MediaTek MT9653 with 2 GB RAM, driven by its remote. These tests
 * put BitTV through the TV's conditions on the emulator: real key events from the input system (not test shortcuts),
 * the launcher's manifest contract, the overscan safe area and memory after browsing everything.
 */
class TvReadinessTest {
    private val intent = Intent(appContext, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_SKIP_BOOT, true).putExtra(MainActivity.EXTRA_DEMO_CATALOG, true)
    /** A TV has no touchscreen, so it is never in touch mode; test runs start in touch mode unless told otherwise. */
    @get:Rule(order = 0) val noTouchMode = object : org.junit.rules.ExternalResource() {
        override fun before() {
            instrumentation.setInTouchMode(false)
            // the demo catalogue's own Watch Later (a real-catalogue run on this emulator may have replaced it)
            kotlinx.coroutines.runBlocking { (appContext as com.bittv.home.BitTvApp).graph.prefs.setWatchLater(com.bittv.home.model.Prefs().watchLater) }
        }
    }
    @get:Rule(order = 1) val rule = AndroidComposeTestRule(ActivityScenarioRule<MainActivity>(intent)) { r -> var a: MainActivity? = null; r.scenario.onActivity { a = it }; a!! }

    /** A remote press: a real KeyEvent down/up injected into the window, as the TV's input system delivers it. */
    private fun remote(code: Int, times: Int = 1) = repeat(times) { instrumentation.sendKeyDownUpSync(code); rule.waitForIdle() }

    /** Holding a button: one down, [repeats] auto-repeat downs (repeatCount > 0) about 50 ms apart, then up. */
    private fun hold(code: Int, repeats: Int) {
        val t = SystemClock.uptimeMillis()
        instrumentation.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        for (i in 1..repeats) { SystemClock.sleep(50); instrumentation.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, code, i)) }
        instrumentation.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
        rule.waitForIdle()
    }

    private fun focusedTags(): List<String> {
        val out = mutableListOf<String>(); var c: SemanticsNode? = rule.onNode(isFocused()).fetchSemanticsNode()
        while (c != null) { c.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }; c = c.parent }
        return out
    }

    @Test fun launcherContract() {
        val pm = appContext.packageManager
        val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(appContext.packageName), 0)
        assertEquals(MainActivity::class.java.name, home?.activityInfo?.name)
        val leanback = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(appContext.packageName), 0)
        assertEquals(MainActivity::class.java.name, leanback?.activityInfo?.name)
        val info = pm.getPackageInfo(appContext.packageName, PackageManager.GET_CONFIGURATIONS)
        val touch = info.reqFeatures?.firstOrNull { it.name == "android.hardware.touchscreen" }
        assertNotNull("touchscreen must be declared not required (TVs have none)", touch)
        assertEquals(0, touch!!.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED)
        assertTrue("TV banner set", pm.getApplicationInfo(appContext.packageName, 0).banner != 0)
    }

    @Test fun remoteDpadOkAndBackThroughTheInputSystem() {
        assertEquals("hero", focusedTags().first())
        remote(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("Down from the banner reaches Watch Later: ${focusedTags()}", "row:wl" in focusedTags())
        remote(KeyEvent.KEYCODE_DPAD_CENTER)
        rule.onNodeWithTag("watchlater").assertIsDisplayed()          // the title page opened
        remote(KeyEvent.KEYCODE_BACK)
        assertTrue("Back returns to the same row", "row:wl" in focusedTags())
        remote(KeyEvent.KEYCODE_BACK)
        assertEquals("Back on Home goes to the top", "hero", focusedTags().first())
    }

    @Test fun keyboardEnterWorksLikeOk() {
        remote(KeyEvent.KEYCODE_DPAD_DOWN)
        remote(KeyEvent.KEYCODE_ENTER)
        rule.onNodeWithTag("watchlater").assertIsDisplayed()
    }

    @Test fun holdingDownGlidesThroughRowsAndNeverEntersTheAppBar() {
        hold(KeyEvent.KEYCODE_DPAD_DOWN, repeats = 12)
        val tags = focusedTags()
        assertFalse("never in the app bar", "rail" in tags)
        assertTrue("ended on a row further down: $tags", tags.any { it.startsWith("row:") && it != "row:wl" })
        hold(KeyEvent.KEYCODE_DPAD_UP, repeats = 20)
        assertTrue("holding Up returns to the top", focusedTags().first().let { it == "hero" || it.startsWith("sc:") })
    }

    @Test fun everythingFixedSitsInsideTheOverscanSafeArea() {
        // 5 % action-safe area of a 1080p TV, in this window's own pixels
        val w = rule.activity.window.decorView.width.toFloat(); val h = rule.activity.window.decorView.height.toFloat()
        val safe = androidx.compose.ui.geometry.Rect(w * 0.025f, h * 0.025f, w * 0.975f, h * 0.975f)
        for (tag in listOf("rail", "hero", "sc:Search", "sc:Settings")) {
            val b = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $b outside $safe", b.left >= safe.left && b.top >= safe.top && b.right <= safe.right && b.bottom <= safe.bottom)
        }
    }

    @Test fun memoryStaysWithinBudgetAfterBrowsingEveryRow() {
        repeat(9) { remote(KeyEvent.KEYCODE_DPAD_DOWN); remote(KeyEvent.KEYCODE_DPAD_RIGHT, 6) }
        repeat(9) { remote(KeyEvent.KEYCODE_DPAD_UP) }
        Runtime.getRuntime().gc(); SystemClock.sleep(500)
        val pssMb = Debug.getPss() / 1024   // this process: the app and the test runner together
        assertTrue("PSS $pssMb MB over the 180 MB budget (2 GB TV)", pssMb < 180)
    }
}

/** The layout must cover the same share of the screen whatever density and size the TV reports (TCL may not use 320). */
class TvDisplayConfigTest {
    private fun bannerRightShare(): Float {
        val i = Intent(appContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_SKIP_BOOT, true)
            .putExtra(MainActivity.EXTRA_DEMO_CATALOG, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val scenario = androidx.test.core.app.ActivityScenario.launch<MainActivity>(i)
        var share = 0f
        try {
            SystemClock.sleep(2500)
            scenario.onActivity { a ->
                val root = a.window.decorView
                // the banner's right edge, found through the Compose view's semantics
                val view = (root as android.view.ViewGroup).findComposeView()
                val owner = view as androidx.compose.ui.node.RootForTest
                fun find(n: SemanticsNode): SemanticsNode? =
                    if (n.config.getOrNull(SemanticsProperties.TestTag) == "hero") n else n.children.firstNotNullOfOrNull(::find)
                val node = find(owner.semanticsOwner.rootSemanticsNode)!!
                share = node.boundsInRoot.right / root.width
            }
        } finally { scenario.close() }
        return share
    }

    private fun android.view.ViewGroup.findComposeView(): android.view.View {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is androidx.compose.ui.node.RootForTest) return c
            if (c is android.view.ViewGroup) runCatching { return c.findComposeView() }
        }
        error("no compose view")
    }

    @Test fun sameLayoutAtEveryTvDensityAndSize() {
        val reference = bannerRightShare()   // the emulator's 1920×1080 @ 320
        try {
            for ((size, density) in listOf("1920x1080" to 240, "1920x1080" to 213, "3840x2160" to 640, "3840x2160" to 480)) {
                shell("wm size $size"); shell("wm density $density"); SystemClock.sleep(2000)
                val s = bannerRightShare()
                assertEquals("banner edge at $size @ $density", reference, s, 0.01f)
            }
        } finally { shell("wm size reset"); shell("wm density reset"); SystemClock.sleep(2000) }
    }
}
