package com.bittv.home.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bittv.home.BuildConfig
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import kotlinx.coroutines.delay

/** The tile's current scale, exposed for UI tests. */
val TileScale = SemanticsPropertyKey<Float>("TileScale")
private var SemanticsPropertyReceiver.tileScale by TileScale

/** Whether the tvOS sway is running (it settles after two gentle cycles so an idle screen stops redrawing). */
val TileSwaying = SemanticsPropertyKey<Boolean>("TileSwaying")
private var SemanticsPropertyReceiver.tileSwaying by TileSwaying

private const val SWAY_LEG_MS = 4_500

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
    /** False for live video (a TvView is a separate surface: it can't scale or sway with the tile). */
    lift: Boolean = true,
    onFocus: () -> Unit = {},
    /** Shown as focused while something inside it has focus (the banner while its dots are focused). */
    lit: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = LocalMotion.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus || lit
    val scale by animateFloatAsState(if (focused && lift) focusScale else 1f, motion.tween(160), label = "tileScale")
    var swaying by remember { mutableStateOf(false) }
    LaunchedEffect(focused, motion.loops) {
        swaying = focused && motion.loops && lift
        if (swaying) { delay(160L + 2 * SWAY_LEG_MS); swaying = false }
    }
    Box(
        modifier
            .onFocusChanged { if (it.isFocused != hasFocus) { hasFocus = it.isFocused; if (hasFocus) onFocus() } }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .zIndex(if (focused) 1f else 0f)
            // test hook only: in release it would rebuild the semantics tree on every animation frame
            .then(if (BuildConfig.DEBUG) Modifier.semantics { tileScale = scale; tileSwaying = swaying } else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (focused && glow) Modifier.shadow(14.dp, shape, ambientColor = Color.White, spotColor = Color.White) else Modifier)
            .size(width, height),
    ) {
        TileContent(shape, focused, swaying, content)
    }
}

/**
 * The tile's face: clipped, a white border while focused, and while [swaying] a lift, a sway (±1.5° / ±2.5°) and a
 * moving light — one 4.5 s swing out and back after the focus scale, then rest. The content stays put in the
 * composition whether or not it sways: rebuilding it would drop focus from anything focusable inside (the banner's
 * dots) and restart what it runs. The sway is read in the layer and draw phases only: no recomposition per frame.
 */
@Composable
private fun TileContent(shape: Shape, focused: Boolean, swaying: Boolean, content: @Composable BoxScope.() -> Unit) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(swaying) {
        if (!swaying) { anim.snapTo(0f); return@LaunchedEffect }
        delay(160)
        anim.animateTo(1f, tween(SWAY_LEG_MS))
        anim.animateTo(0f, tween(SWAY_LEG_MS))
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (swaying) Modifier.graphicsLayer {
                val t = anim.value
                rotationX = 1.5f - 3f * t
                rotationY = -2.5f + 5f * t
                cameraDistance = 24f * density
            } else Modifier)
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, Color.White, shape) else Modifier)
            .then(if (swaying) Modifier.drawWithContent {
                drawContent()
                val t = anim.value
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * (0.25f + 0.3f * t), size.height * (0.1f + 0.15f * t)),
                    radius = size.width * 0.55f,
                ))
            } else Modifier),
        content = content,
    )
}
