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
        setContent { BitTvTheme { BitTvRoot(graph) } }
    }
}
