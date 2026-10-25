package com.bittv.home.platform

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.bittv.home.MainActivity

/** The Home decisions, pure so they are tested on the JVM. */
object HomeRouting {
    const val GOOGLE_HOME = "com.google.android.apps.tv.launcherx"
    enum class Key { PASS, SWALLOW, OPEN_BITTV, ASK_BITTV }
    private val MIC = setOf(KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST)

    /** The remote's mic is BitTV's (answers in the active section) only while BitTV is in front; in apps it stays Google's. */
    fun mic(code: Int, action: Int, foreground: String?): Key = when {
        code !in MIC || foreground != "com.bittv.home" -> Key.PASS
        action == KeyEvent.ACTION_UP -> Key.ASK_BITTV
        else -> Key.SWALLOW
    }

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
    /** The app in front, from window changes: the mic is BitTV's only while BitTV is. */
    private var foreground: String? = null

    // the TV shows Google's home at start-up before this service is running, so it can't see it appear: take over now
    override fun onServiceConnected() {
        openBitTv()
        androidx.core.content.ContextCompat.registerReceiver(this, screen,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) },
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Standby: this service is never frozen, so it notes the TV going to sleep even when BitTV's process is; on wake
     * it brings BitTV in front (whatever app was open), where the logo plays — unless BitTV already played it.
     */
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val power = getSystemService(PowerManager::class.java)
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> wakeGate().markAsleep(power.isInteractive)
                Intent.ACTION_SCREEN_ON -> if (wakeGate().pending)
                    startActivity(Intent(c, MainActivity::class.java).setAction(MainActivity.ACTION_WAKE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
    }

    override fun onDestroy() { runCatching { unregisterReceiver(screen) }; super.onDestroy() }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val home = HomeRouting.key(event.keyCode, event.action)
        val r = if (home != HomeRouting.Key.PASS) home else HomeRouting.mic(event.keyCode, event.action, foreground)
        return when (r) {
            HomeRouting.Key.PASS -> false
            HomeRouting.Key.SWALLOW -> true
            HomeRouting.Key.OPEN_BITTV -> { openBitTv(); true }
            HomeRouting.Key.ASK_BITTV -> { startActivity(Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_ASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); true }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.className?.toString()?.endsWith("Activity") == true) foreground = event.packageName?.toString()
        if (HomeRouting.replaceWindow(event.packageName, event.className)) openBitTv()
    }

    override fun onInterrupt() {}

    private fun openBitTv() {
        startActivity(Intent(this, MainActivity::class.java).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
