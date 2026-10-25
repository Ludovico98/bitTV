package com.bittv.home.ui.home

import android.media.tv.TvContract
import android.media.tv.TvView
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Text
import com.bittv.home.platform.HdmiPreview
import com.bittv.home.platform.PreviewSignal
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Type

/**
 * The live picture of an HDMI device, muted, through the system's TvView, only for a device the TV reports as on.
 * While a session is open the TCL runs its HDMI picture path for the whole panel — and its dynamic backlight dims
 * everything on a black picture — so a preview that shows nothing ([PreviewSignal]) is closed at once and reported
 * through [onNoPicture]; the tile then says so instead.
 */
@Composable
fun TvPreview(inputId: String, modifier: Modifier = Modifier, onNoPicture: () -> Unit = {}) {
    var status by remember(inputId) { mutableStateOf<String?>("Connecting…") }
    val gaveUp by rememberUpdatedState(onNoPicture)
    Box(modifier.background(Color.Black).testTag("preview:$inputId")) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx) { status = null; gaveUp() }.apply {
                    setCallback(object : TvView.TvInputCallback() {
                        override fun onVideoAvailable(id: String) { hadPicture = true; status = null }
                        override fun onVideoUnavailable(id: String, reason: Int) {
                            if (PreviewSignal.blank(reason, hadPicture)) post { giveUp() }   // not from inside the session's callback
                        }
                        override fun onConnectionFailed(id: String) { status = "Preview not available" }
                        override fun onDisconnected(id: String) { status = "Disconnected" }
                    })
                    postDelayed(timeout, PreviewSignal.GIVE_UP_MS)
                    setStreamVolume(0f)
                    // passive: no CEC device select (it would wake the device and hand the TV over to it)
                    tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId),
                        Bundle().apply { HdmiPreview.TUNE_FLAGS.forEach { (k, v) -> putBoolean(k, v) } })
                }
            },
            onRelease = { it.release() },
            modifier = Modifier.fillMaxSize(),
        )
        status?.let { Text(it, style = Type.Caption.copy(color = Ink2), modifier = Modifier.align(Alignment.Center)) }
    }
}

/** A TvView that closes its session for good the first time it gives up, and never reports after it was released. */
private class PreviewView(ctx: android.content.Context, private val onGiveUp: () -> Unit) : TvView(ctx) {
    var hadPicture = false
    private var done = false
    val timeout = Runnable { if (!hadPicture) giveUp() }
    fun giveUp() { if (!done) { done = true; reset(); onGiveUp() } }
    fun release() { done = true; removeCallbacks(timeout); reset() }
}
