package com.bittv.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import com.bittv.home.ui.boot.BootGate
import com.bittv.home.platform.wakeGate
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bittv.home.ui.root.BitTvRoot
import com.bittv.home.ui.theme.BitTvTheme
import com.bittv.home.ui.theme.TvCanvas
import com.bittv.home.ui.components.LocalHomeBackdrop
import com.bittv.home.ui.components.rememberHomeBackdrop
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalWindowInfo

class MainActivity : ComponentActivity() {
    private val homeSignal = mutableIntStateOf(0)
    private val askSignal = mutableIntStateOf(0)
    private val wakeSignal = mutableIntStateOf(0)
    /** The TV going to standby: the next time BitTV is in front it plays the boot animation (see WakeGate). */
    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) { wakeGate().markAsleep(interactive()) }
    }
    private fun interactive() = getSystemService(android.os.PowerManager::class.java).isInteractive

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BitTvApp).graph
        // the boot animation plays once per TV boot, not every time the launcher process is recreated, and after a
        // wake from standby even when the process died meanwhile; benchmarks and tests pass skipBoot
        val woke = wakeGate().consumeWake()
        val skipBoot = intent.getBooleanExtra(EXTRA_SKIP_BOOT, false) ||
            !woke && (savedInstanceState != null || !bootGate().shouldPlay(bootCount()))
        val demoCatalog = intent.getBooleanExtra(EXTRA_DEMO_CATALOG, false)
        setContent { BitTvTheme { TvCanvas { WindowBackdrop { BitTvRoot(graph, skipBoot, homeSignal.intValue, demoCatalog, askSignal.intValue, wakeSignal.intValue) } } } }
        // Compose's per-frame accessibility work only when a screen reader is on (see ComposeA11y); re-applied after
        // Compose re-reads the services: on attach (this post runs after it), on every change, and on resume
        androidx.core.content.ContextCompat.registerReceiver(this, screenOff, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        a11y.addAccessibilityStateChangeListener(a11yChanged)
        a11y.addTouchExplorationStateChangeListener(touchChanged)
        quietA11y()
    }

    /** Home's background becomes the window's own (Home is always composed underneath every other screen). */
    @Composable
    private fun WindowBackdrop(content: @Composable () -> Unit) {
        val backdrop = rememberHomeBackdrop(LocalWindowInfo.current.containerSize, isWindowBackground = true)
        LaunchedEffect(backdrop) { backdrop?.let { window.setBackgroundDrawable(BitmapDrawable(resources, it.bitmap.asAndroidBitmap())) } }
        CompositionLocalProvider(LocalHomeBackdrop provides backdrop, content = content)
    }

    // going to standby stops BitTV while the TV is already non-interactive (seen on the TCL): that alone marks it,
    // without waiting for a broadcast a frozen process may get late
    override fun onStop() {
        super.onStop()
        wakeGate().markAsleep(interactive())
    }

    override fun onResume() {
        super.onResume()
        quietA11y()
        if (wakeGate().consumeWake()) wakeSignal.intValue++
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        a11y.removeAccessibilityStateChangeListener(a11yChanged); a11y.removeTouchExplorationStateChangeListener(touchChanged)
        super.onDestroy()
    }

    private val a11y by lazy { getSystemService(android.view.accessibility.AccessibilityManager::class.java) }
    private val a11yChanged = android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener { quietA11y() }
    private val touchChanged = android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener { quietA11y() }
    private fun quietA11y() { window.decorView.post { com.bittv.home.platform.ComposeA11y.apply(window.decorView, a11y) } }

    // singleTask: pressing Home while BitTV is already in front arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
        if (intent.action == ACTION_ASK) askSignal.intValue++   // the remote's mic, sent by the Home helper
    }

    private fun bootGate(): BootGate {
        val sp = getSharedPreferences("bittv_boot", MODE_PRIVATE)
        return BootGate(read = { if (sp.contains("count")) sp.getInt("count", 0) else null },
            write = { sp.edit().putInt("count", it).apply() })
    }

    private fun bootCount(): Int? = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()

    companion object { const val EXTRA_SKIP_BOOT = "skipBoot"; const val EXTRA_DEMO_CATALOG = "demoCatalog"; const val ACTION_ASK = "com.bittv.home.ASK"
        /** Sent by the Home helper when the TV wakes: brings BitTV in front, where onResume plays the logo. */
        const val ACTION_WAKE = "com.bittv.home.WAKE" }
}
