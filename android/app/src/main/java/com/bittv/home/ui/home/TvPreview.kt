package com.bittv.home.ui.home

import android.media.tv.TvContract
import android.media.tv.TvView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Type

/**
 * The live picture of an HDMI device, muted, through the system's TvView. While the device shows no picture
 * (standby, off) the tile says so instead of a black box.
 */
@Composable
fun TvPreview(inputId: String, modifier: Modifier = Modifier) {
    var status by remember(inputId) { mutableStateOf<String?>("Connecting…") }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                TvView(ctx).apply {
                    setCallback(object : TvView.TvInputCallback() {
                        override fun onVideoAvailable(id: String) { status = null }
                        override fun onVideoUnavailable(id: String, reason: Int) { status = "No picture — the device may be in standby" }
                        override fun onConnectionFailed(id: String) { status = "Preview not available" }
                        override fun onDisconnected(id: String) { status = "Disconnected" }
                    })
                    setStreamVolume(0f)
                    tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId))
                }
            },
            onRelease = { it.reset() },
            modifier = Modifier.fillMaxSize(),
        )
        status?.let { Text(it, style = Type.Caption.copy(color = Ink2), modifier = Modifier.align(Alignment.Center)) }
    }
}
