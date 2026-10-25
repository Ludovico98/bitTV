package com.bittv.home.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/** The tile's current scale, exposed for UI tests. */
val TileScale = SemanticsPropertyKey<Float>("TileScale")
private var SemanticsPropertyReceiver.tileScale by TileScale

/**
 * A focusable card: on focus it grows to 1.10 with a white border and glow in 160 ms, then (tvOS style) lifts,
 * sways gently and catches the light while it stays focused. Only the focused tile gets layers and loops.
 */
@Composable
fun FocusTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp,
    glow: Boolean = true,
    shape: Shape = RoundedCornerShape(9.dp),
    focusScale: Float = 1.10f,
    onFocus: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = LocalMotion.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, motion.tween(160), label = "tileScale")
    Box(
        modifier
            .onFocusChanged { if (it.isFocused != focused) { focused = it.isFocused; if (focused) onFocus() } }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .zIndex(if (focused) 1f else 0f)
            .semantics { tileScale = scale }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused && glow) Modifier.shadow(14.dp, shape, ambientColor = Color.White, spotColor = Color.White) else Modifier)
            .size(width, height),
    ) {
        if (focused && motion.loops) SwayingContent(shape, content) else StillContent(shape, focused, content)
    }
}

@Composable
private fun StillContent(shape: Shape, focused: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clip(shape).then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier), content = content)
}

/** Lift, sway (±1.5° / ±2.5°) and a moving light, 4.5 s back and forth, after the focus scale has finished. */
@Composable
private fun SwayingContent(shape: Shape, content: @Composable BoxScope.() -> Unit) {
    val t by rememberInfiniteTransition(label = "sway").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4500, delayMillis = 160), RepeatMode.Reverse), label = "swayT",
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationX = 1.5f - 3f * t
                rotationY = -2.5f + 5f * t
                cameraDistance = 24f * density
            }
            .clip(shape)
            .border(2.dp, Color.White, shape),
    ) {
        content()
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val light = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * (0.25f + 0.3f * t), size.height * (0.1f + 0.15f * t)),
                    radius = size.width * 0.55f,
                )
                onDrawBehind { drawRect(light) }
            },
        )
    }
}
