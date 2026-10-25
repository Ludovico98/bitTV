package com.bittv.home.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.bittv.home.model.WeatherNow
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.theme.Glass
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type
import androidx.compose.foundation.Canvas

data class Shortcut(val label: String, val glyph: Glyph, val onClick: () -> Unit)

/** Round shortcut buttons; the name appears under the highlighted one. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ShortcutButtons(items: List<Shortcut>, modifier: Modifier = Modifier) {
    // Right at the last button stays put: left/right never changes row
    Row(modifier.focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() } }.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { RoundButton(it) }
    }
}

@Composable
private fun RoundButton(s: Shortcut) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, LocalMotion.current.tween(160), label = "sc")
    Box(Modifier.zIndex(if (focused) 1f else 0f)) {
        Box(
            Modifier
                .testTag("sc:${s.label}")
                .onFocusChanged { focused = it.isFocused }
                .clickable(remember { MutableInteractionSource() }, null, onClick = s.onClick)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (focused) Modifier.shadow(12.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White) else Modifier)
                .size(36.dp)
                .background(if (focused) Color.White else Glass, CircleShape)
                .border(1.dp, if (focused) Color.White else GlassLine, CircleShape),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(s.glyph, if (focused) Color(0xFF0B0F18) else Ink, Modifier.size(16.dp)) }
        // the name takes no width, so hidden labels don't push the buttons apart
        Box(Modifier.align(Alignment.BottomCenter).offset(y = 23.dp).requiredSize(0.dp), contentAlignment = Alignment.Center) {
            Text(s.label, style = Type.Caption.copy(color = Ink, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.wrapContentSize(unbounded = true).alpha(if (focused) 1f else 0f), softWrap = false)
        }
    }
}

/** "22:00 | 25/10/2026 | ☀ 11°C"; the weather part is left out when it is unknown (never invented). */
@Composable
fun ClockLine(time: String, date: String, weather: WeatherNow?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(time, style = Type.Clock.copy(fontSize = 22.sp))
        Sep()
        Text(date, style = Type.Clock.copy(color = Ink2))
        if (weather != null) {
            Sep()
            Canvas(Modifier.size(20.dp)) { drawWeatherSymbol(weather.kind) }
            Spacer(Modifier.width(5.dp))
            Text("${weather.tempC}°C", style = Type.Clock)
        }
    }
}

@Composable
private fun Sep() = Text("|", style = Type.Clock.copy(color = Ink3, fontWeight = FontWeight.Light), modifier = Modifier.padding(horizontal = 9.dp))
