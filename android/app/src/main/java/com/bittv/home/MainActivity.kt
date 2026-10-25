package com.bittv.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bittv.home.ui.root.BitTvRoot
import com.bittv.home.ui.theme.BitTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BitTvApp).graph
        // the boot animation plays on a cold start only; benchmarks and tests pass skipBoot
        val skipBoot = intent.getBooleanExtra(EXTRA_SKIP_BOOT, false)
        setContent { BitTvTheme { BitTvRoot(graph, skipBoot) } }
    }

    companion object { const val EXTRA_SKIP_BOOT = "skipBoot" }
}
