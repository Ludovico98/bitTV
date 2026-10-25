package com.bittv.home.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max

private val DefaultA = Color(0x405A78FF)
private val DefaultB = Color(0xCC281E5A)

/**
 * The background takes the highlighted poster's colours. Two gradient layers cross-fade with alpha: each layer's
 * drawing is recorded once, so a colour change costs a compositor fade, not a full-screen repaint every frame.
 */
@Composable
fun AmbientBackground(colors: () -> Pair<Color, Color>?, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    // read here, not by the caller: a focus change recomposes only the background, not the whole screen
    val c1 = colors()?.first; val c2 = colors()?.second
    var slots by remember { mutableStateOf(listOf<Pair<Color, Color>?>(DefaultA to DefaultB, null)) }
    var front by remember { mutableIntStateOf(0) }
    LaunchedEffect(c1, c2) {
        val next = if (c1 != null && c2 != null) ambientPair(c1, c2) else DefaultA to DefaultB
        if (slots[front] != next) {
            val back = 1 - front
            slots = slots.toMutableList().also { it[back] = next }
            front = back
        }
    }
    val a0 by animateFloatAsState(if (front == 0) 1f else 0f, motion.tween(350), label = "amb0")
    val a1 by animateFloatAsState(if (front == 1) 1f else 0f, motion.tween(350), label = "amb1")
    Box(modifier.fillMaxSize().drawWithCache { val b = baseBrush(size.width, size.height); onDrawBehind { drawRect(b) } }) {
        slots[0]?.let { GradientLayer(it, Modifier.graphicsLayer { alpha = a0 }) }
        slots[1]?.let { GradientLayer(it, Modifier.graphicsLayer { alpha = a1 }) }
    }
}

/** The prototype's mix: accent at 34 % top-left, a lightened base colour bottom-right. */
fun ambientPair(c1: Color, c2: Color): Pair<Color, Color> =
    c2.copy(alpha = 0.34f) to Color(c1.red + (1 - c1.red) * 0.18f, c1.green + (1 - c1.green) * 0.18f, c1.blue + (1 - c1.blue) * 0.18f, 0.92f)

private fun baseBrush(w: Float, h: Float) =
    Brush.radialGradient(listOf(Color(0xFF0B0F18), Color(0xFF05070B)), center = Offset(w / 2, h / 2), radius = max(w, h) * 0.7f)

@Composable
private fun GradientLayer(colors: Pair<Color, Color>, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val (a, b) = colors
            val top = Brush.radialGradient(listOf(a, Color.Transparent), center = Offset(size.width * 0.18f, size.height * 0.08f), radius = size.width * 0.55f)
            val bottom = Brush.radialGradient(listOf(b, Color.Transparent), center = Offset(size.width * 0.92f, size.height), radius = size.width * 0.6f)
            onDrawBehind { drawRect(top); drawRect(bottom) }
        },
    )
}
