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
