package com.bittv.home.platform

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.bittv.home.MainActivity

/** The Home decisions, pure so they are tested on the JVM. */
object HomeRouting {
    const val GOOGLE_HOME = "com.google.android.apps.tv.launcherx"
    enum class Key { PASS, SWALLOW, OPEN_BITTV }

    /** Home is taken (down swallowed, BitTV opened on release); every other key goes on as usual. */
    fun key(code: Int, action: Int): Key = when {
        code != KeyEvent.KEYCODE_HOME -> Key.PASS
        action == KeyEvent.ACTION_UP -> Key.OPEN_BITTV
        else -> Key.SWALLOW
    }

    /** Google's home screen itself is replaced by BitTV; its other pages (Assistant results, profiles) are left alone. */
    fun replaceWindow(pkg: CharSequence?, cls: CharSequence?): Boolean =
        pkg == GOOGLE_HOME && cls?.toString()?.endsWith(".home.HomeActivity") == true
}

/**
 * Keeps BitTV the home screen while Google TV's own home stays enabled (Google Assistant only works while it is):
 * catches the remote's Home button, and switches to BitTV whenever Google's home screen comes up anyway (after a
 * restart, or when an app sends the TV "home"). Enabled once by install.sh.
 */
class HomeKeyService : AccessibilityService() {
    override fun onKeyEvent(event: KeyEvent): Boolean = when (HomeRouting.key(event.keyCode, event.action)) {
        HomeRouting.Key.PASS -> false
        HomeRouting.Key.SWALLOW -> true
        HomeRouting.Key.OPEN_BITTV -> { openBitTv(); true }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && HomeRouting.replaceWindow(event.packageName, event.className)) openBitTv()
    }

    override fun onInterrupt() {}

    private fun openBitTv() {
        startActivity(Intent(this, MainActivity::class.java).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
