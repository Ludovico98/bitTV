package com.bittv.home.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Type

private val Dark = Color(0xFF0B0F18)

/** The most a focused control may grow on each side, so it never reaches the control next to it. */
private val MaxGrow = 5.dp

/**
 * Focus look shared by buttons: white fill, dark content, a lift and a soft glow. Small controls scale by
 * [scaleTo]; wide ones grow by at most [MaxGrow] per side (tvOS: wide controls lift, they don't balloon).
 */
@Composable
fun rememberFocusLook(scaleTo: Float = 1.1f): Pair<Boolean, Modifier> {
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, LocalMotion.current.tween(160), label = "btn")
    return focused to Modifier.onFocusChanged { focused = it.isFocused }.zIndex(if (focused) 1f else 0f).graphicsLayer {
        val grow = if (size.width > 0f) minOf(scaleTo - 1f, 2 * MaxGrow.toPx() / size.width) else scaleTo - 1f
        val s = 1f + grow * p
        scaleX = s; scaleY = s
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 38.dp,
               leading: (@Composable (Color) -> Unit)? = null) {
    val (focused, look) = rememberFocusLook()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier.then(look).clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .then(if (focused) Modifier.shadow(14.dp, shape, ambientColor = Color.White, spotColor = Color.White) else Modifier)
            .background(if (focused) Color.White else Color(0x1FFFFFFF), shape)
            .height(height).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val c = if (focused) Dark else Color.White
        leading?.invoke(c)
        Text(text, style = Type.Body.copy(color = c, fontWeight = FontWeight.Bold))
    }
}

/** A settings row: label on the left, a switch or a value on the right. Exposes toggle state to accessibility/tests. */
@Composable
fun OptionRow(label: String, modifier: Modifier = Modifier, checked: Boolean? = null, value: String? = null,
              onClick: () -> Unit, leading: (@Composable () -> Unit)? = null) {
    val (focused, look) = rememberFocusLook(1.06f)
    val shape = RoundedCornerShape(9.dp)
    val interaction = remember { MutableInteractionSource() }
    val action = if (checked != null) Modifier.toggleable(checked, interaction, null, role = Role.Switch) { onClick() }
    else Modifier.clickable(interaction, null, onClick = onClick)
    Row(
        modifier.then(look).then(action).background(if (focused) Color.White else Color(0x0FFFFFFF), shape)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading?.invoke()
        // options with an app icon use a slightly smaller label so names like "Paramount+" stay on one line
        Text(label, style = (if (leading != null) Type.Caption.copy(fontWeight = FontWeight.SemiBold) else Type.Label).copy(color = if (focused) Dark else Ink),
            modifier = Modifier.weight(1f), maxLines = 1)
        if (checked != null) Switch(checked, focused)
        if (value != null) Text(value, style = Type.Caption.copy(color = if (focused) Color(0xFF5B6475) else Ink2, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun Switch(on: Boolean, focused: Boolean) {
    val x by animateFloatAsState(if (on) 10f else 0f, LocalMotion.current.tween(160), label = "sw")
    Box(Modifier.size(25.dp, 15.dp).background(if (on) Color(0xFF34C759) else if (focused) Color(0xFFC9CED8) else Color(0x33FFFFFF), RoundedCornerShape(8.dp))) {
        Box(Modifier.padding(2.dp).offset(x = x.dp).size(11.dp).background(Color.White, CircleShape))
    }
}
