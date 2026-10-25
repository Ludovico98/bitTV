package com.bittv.home.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bittv.home.model.CastDevice
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Type

private val MenuShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(12.dp)
private val OnLight = Color(0xFF0B0F18)

/** What to do on the device itself: casting always starts on the phone, the TV only has to be ready and visible. */
fun castHowTo(d: CastDevice, tvName: String): String = when (d.kind) {
    CastDevice.Kind.IPHONE, CastDevice.Kind.IPAD ->
        "On the ${d.kind.label}: Control Centre → Screen Mirroring → $tvName. In apps, tap the AirPlay icon."
    CastDevice.Kind.MAC -> "On the Mac: Control Centre → Screen Mirroring → $tvName."
    CastDevice.Kind.ANDROID -> "On the phone: tap Cast in an app → $tvName, or Quick Settings → Screen Cast for the whole screen."
}

/**
 * The Cast button's dropdown: the devices that can cast to this TV, by name. Focus stays inside it; Back closes it.
 * OK on a device shows the one step to take on that device.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CastMenu(devices: List<CastDevice>, tvName: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val first = remember { FocusRequester() }
    var chosen by remember { mutableStateOf<String?>(null) }
    val open = remember { Animatable(if (motion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { first.requestFocus(); open.animateTo(1f, motion.tween(160)) }
    BackHandler(onBack = onClose)
    Column(
        modifier
            .testTag("cast:menu")
            .width(320.dp)
            .graphicsLayer {   // drops down from the button: scale from the top-left corner and fade in
                val v = open.value; alpha = v; scaleX = 0.94f + 0.06f * v; scaleY = 0.94f + 0.06f * v
                transformOrigin = TransformOrigin(0.1f, 0f)
            }
            .shadow(28.dp, MenuShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Color(0xF2121722), MenuShape)
            .border(1.dp, GlassLine, MenuShape)
            // the focus trap: no direction leaves the menu, only Back (or choosing again) closes it
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("CAST TO $tvName".uppercase(), style = Type.Kicker, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp))
        if (devices.isEmpty()) {
            Text("No phones or computers seen yet. Open the Cast or AirPlay menu on your device and it appears here.",
                style = Type.Body.copy(color = Ink2), modifier = Modifier.padding(12.dp))
        }
        devices.forEachIndexed { i, d ->
            DeviceRow(d, if (chosen == d.id) castHowTo(d, tvName) else null,
                if (i == 0) Modifier.focusRequester(first) else Modifier) { chosen = if (chosen == d.id) null else d.id }
        }
        Spacer(Modifier.height(2.dp))
        Text("Miracast and T-Cast mirroring are on too.", style = Type.Caption, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
    }
}

@Composable
private fun DeviceRow(d: CastDevice, howTo: String?, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) OnLight else Ink
    Column(
        modifier
            .testTag("cast:${d.id}")
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .fillMaxWidth()
            .background(if (focused) Color.White else Color.Transparent, RowShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(when (d.kind) { CastDevice.Kind.IPAD -> Glyph.TABLET; CastDevice.Kind.MAC -> Glyph.LAPTOP; else -> Glyph.PHONE },
                fg, Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(d.name, style = Type.Label.copy(color = fg, fontWeight = FontWeight.SemiBold))
                Text("${d.kind.label} · ${d.kind.via}", style = Type.Caption.copy(color = if (focused) OnLight.copy(alpha = 0.65f) else Ink2))
            }
        }
        if (howTo != null) {
            Text(howTo, style = Type.Caption.copy(color = fg), modifier = Modifier.testTag("cast:howto").padding(start = 34.dp, top = 6.dp))
        }
    }
}
