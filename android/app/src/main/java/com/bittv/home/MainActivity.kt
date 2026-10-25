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

class MainActivity : ComponentActivity() {
    private val homeSignal = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BitTvApp).graph
        // the boot animation plays once per TV boot, not every time the launcher process is recreated;
        // benchmarks and tests pass skipBoot
        val skipBoot = intent.getBooleanExtra(EXTRA_SKIP_BOOT, false) ||
            savedInstanceState != null || !bootGate().shouldPlay(bootCount())
        val demoCatalog = intent.getBooleanExtra(EXTRA_DEMO_CATALOG, false)
        setContent { BitTvTheme { TvCanvas { BitTvRoot(graph, skipBoot, homeSignal.intValue, demoCatalog) } } }
    }

    // singleTask: pressing Home while BitTV is already in front arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal.intValue++
    }

    private fun bootGate(): BootGate {
        val sp = getSharedPreferences("bittv_boot", MODE_PRIVATE)
        return BootGate(read = { if (sp.contains("count")) sp.getInt("count", 0) else null },
            write = { sp.edit().putInt("count", it).apply() })
    }

    private fun bootCount(): Int? = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull()

    companion object { const val EXTRA_SKIP_BOOT = "skipBoot"; const val EXTRA_DEMO_CATALOG = "demoCatalog" }
}
