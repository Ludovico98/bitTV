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
import androidx.compose.runtime.mutableIntStateOf
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
import com.bittv.home.platform.HdmiInput
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Type

private val MenuShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(12.dp)
private val OnLight = Color(0xFF0B0F18)

/**
 * The Inputs button's dropdown, like Cast's: what is plugged into the TV, by the name it gives, then the other HDMI ports,
 * read each time it opens. Reading asks the devices nothing, so nothing is woken. OK switches the TV to that input;
 * focus stays inside; Back closes it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InputMenu(find: suspend () -> List<HdmiInput>, onOpen: (HdmiInput) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val first = remember { FocusRequester() }
    val open = remember { Animatable(if (motion.enabled) 0f else 1f) }
    var run by remember { mutableIntStateOf(0) }
    var inputs by remember { mutableStateOf<List<HdmiInput>?>(null) }
    LaunchedEffect(run) { inputs = null; inputs = find() }
    LaunchedEffect(inputs) { if (inputs != null) first.requestFocus() }
    LaunchedEffect(Unit) { first.requestFocus(); open.animateTo(1f, motion.tween(160)) }
    BackHandler(onBack = onClose)
    Column(
        modifier
            .testTag("inputs:menu")
            .width(300.dp)
            .graphicsLayer {
                val v = open.value; alpha = v; scaleX = 0.94f + 0.06f * v; scaleY = 0.94f + 0.06f * v
                transformOrigin = TransformOrigin(0.1f, 0f)
            }
            .shadow(28.dp, MenuShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Color(0xF2121722), MenuShape)
            .border(1.dp, GlassLine, MenuShape)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("INPUTS", style = Type.Kicker, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp))
        val list = inputs
        when {
            list == null -> Text("Checking the HDMI ports…", style = Type.Body.copy(color = Ink2), modifier = Modifier.padding(12.dp))
            list.isEmpty() -> Text("Nothing is plugged into the HDMI ports.", style = Type.Body.copy(color = Ink2), modifier = Modifier.padding(12.dp))
        }
        list.orEmpty().forEachIndexed { i, input ->
            MenuRow(input.label, if (input.name != null) input.port else "No name reported", "inputs:${input.inputId}", Glyph.HDMI,
                if (i == 0) Modifier.focusRequester(first) else Modifier) { onOpen(input) }
        }
        // always a focus target, also while checking or when nothing is plugged in
        MenuRow("Check again", null, "inputs:again", Glyph.SEARCH, if (list.isNullOrEmpty()) Modifier.focusRequester(first) else Modifier) { run++ }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MenuRow(title: String, sub: String?, tag: String, glyph: Glyph, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) OnLight else Ink
    Row(
        modifier
            .testTag(tag)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .fillMaxWidth()
            .background(if (focused) Color.White else Color.Transparent, RowShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, fg, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = Type.Label.copy(color = fg, fontWeight = FontWeight.SemiBold))
            if (sub != null) Text(sub, style = Type.Caption.copy(color = if (focused) OnLight.copy(alpha = 0.65f) else Ink2))
        }
    }
}
