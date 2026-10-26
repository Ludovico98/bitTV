package com.bittv.home.platform

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.bittv.home.MainActivity

/** The Home decisions, pure so they are tested on the JVM. */
object HomeRouting {
    const val GOOGLE_HOME = "com.google.android.apps.tv.launcherx"
    enum class Key { PASS, SWALLOW, OPEN_BITTV }

    /** Home is taken (down swallowed, BitTV opened on release); every other key, the mic included (Google Assistant), goes on as usual. */
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
    // the TV shows Google's home at start-up before this service is running, so it can't see it appear: take over now
    override fun onServiceConnected() {
        openBitTv()
        androidx.core.content.ContextCompat.registerReceiver(this, screen,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Standby: this service is never frozen, so it notes the TV going to sleep and waking even when BitTV's process
     * is. It does not pull BitTV in front on wake: the TCL returns to Home by itself (seen 2026-10-25), and a wake
     * into a cast or the PS5 must stay there.
     */
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> wakeGate().markAsleep(getSystemService(PowerManager::class.java).isInteractive)
                Intent.ACTION_SCREEN_ON -> wakeGate().markAwake(SystemClock.elapsedRealtime())
            }
        }
    }

    override fun onDestroy() { runCatching { unregisterReceiver(screen) }; super.onDestroy() }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return when (HomeRouting.key(event.keyCode, event.action)) {
            HomeRouting.Key.PASS -> false
            HomeRouting.Key.SWALLOW -> true
            HomeRouting.Key.OPEN_BITTV -> { openBitTv(); true }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (HomeRouting.replaceWindow(event.packageName, event.className)) openBitTv()
    }

    override fun onInterrupt() {}

    private fun openBitTv() {
        startActivity(Intent(this, MainActivity::class.java).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
