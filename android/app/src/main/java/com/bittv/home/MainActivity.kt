package com.bittv.home

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import com.bittv.home.ui.boot.BootGate
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
    /** When the screen last came on (TV woken from standby): BitTV coming back right after plays the boot animation. */
    private var lastScreenOn = 0L
    private val screenOn = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) { lastScreenOn = android.os.SystemClock.elapsedRealtime() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BitTvApp).graph
        // the boot animation plays once per TV boot, not every time the launcher process is recreated;
        // benchmarks and tests pass skipBoot
        val skipBoot = intent.getBooleanExtra(EXTRA_SKIP_BOOT, false) ||
            savedInstanceState != null || !bootGate().shouldPlay(bootCount())
        val demoCatalog = intent.getBooleanExtra(EXTRA_DEMO_CATALOG, false)
        setContent { BitTvTheme { TvCanvas { WindowBackdrop { BitTvRoot(graph, skipBoot, homeSignal.intValue, demoCatalog, askSignal.intValue, wakeSignal.intValue) } } } }
    }

    /** Home's background becomes the window's own (Home is always composed underneath every other screen). */
    @Composable
    private fun WindowBackdrop(content: @Composable () -> Unit) {
        val backdrop = rememberHomeBackdrop(LocalWindowInfo.current.containerSize, isWindowBackground = true)
        LaunchedEffect(backdrop) { backdrop?.let { window.setBackgroundDrawable(BitmapDrawable(resources, it.bitmap.asAndroidBitmap())) } }
        CompositionLocalProvider(LocalHomeBackdrop provides backdrop, content = content)
    }

    // singleTask: pressing Home while BitTV is already in front arrives here
    override fun onStart() {
        super.onStart()
        androidx.core.content.ContextCompat.registerReceiver(this, screenOn, android.content.IntentFilter(Intent.ACTION_SCREEN_ON),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        if (com.bittv.home.platform.WakeGate.playOnResume(lastScreenOn, android.os.SystemClock.elapsedRealtime())) { lastScreenOn = 0; wakeSignal.intValue++ }
    }

    override fun onDestroy() { runCatching { unregisterReceiver(screenOn) }; super.onDestroy() }

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

    companion object { const val EXTRA_SKIP_BOOT = "skipBoot"; const val EXTRA_DEMO_CATALOG = "demoCatalog"; const val ACTION_ASK = "com.bittv.home.ASK" }
}
