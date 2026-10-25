package com.bittv.home.platform

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRoutingTest {
    @Test fun homeKeyIsTakenAndOpensBitTvOnRelease() {
        assertEquals(HomeRouting.Key.SWALLOW, HomeRouting.key(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_DOWN))
        assertEquals(HomeRouting.Key.OPEN_BITTV, HomeRouting.key(KeyEvent.KEYCODE_HOME, KeyEvent.ACTION_UP))
    }

    @Test fun otherKeysPassThrough() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_SEARCH))
            assertEquals(HomeRouting.Key.PASS, HomeRouting.key(k, KeyEvent.ACTION_DOWN))
    }

    @Test fun googleHomeScreenIsReplacedButNotItsOtherPages() {
        assertTrue(HomeRouting.replaceWindow("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.home.HomeActivity"))
        // the Assistant's search results and profile pages live in the same app: leave them alone
        assertFalse(HomeRouting.replaceWindow("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.search.SearchActivity"))
        assertFalse(HomeRouting.replaceWindow("com.netflix.ninja", "com.netflix.ninja.MainActivity"))
        assertFalse(HomeRouting.replaceWindow(null, null))
    }
}

class MicRoutingTest {
    @Test fun micIsBitTvsOnlyWhileBitTvIsInFront() {
        for (code in listOf(KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST)) {
            assertEquals(HomeRouting.Key.SWALLOW, HomeRouting.mic(code, KeyEvent.ACTION_DOWN, "com.bittv.home"))
            assertEquals(HomeRouting.Key.ASK_BITTV, HomeRouting.mic(code, KeyEvent.ACTION_UP, "com.bittv.home"))
            assertEquals(HomeRouting.Key.PASS, HomeRouting.mic(code, KeyEvent.ACTION_UP, "com.netflix.ninja"))   // Google, inside apps
        }
        assertEquals(HomeRouting.Key.PASS, HomeRouting.mic(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP, "com.bittv.home"))
    }
}
