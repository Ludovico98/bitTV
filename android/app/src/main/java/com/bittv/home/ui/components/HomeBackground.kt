package com.bittv.home.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max

/**
 * Home's background, drawn once into a bitmap the size of the screen. It is the window's background (one bitmap
 * draw per frame instead of a colour fill plus three full-screen gradients, which cost the TV's GPU dearly).
 */
class HomeBackdrop(val bitmap: ImageBitmap, val isWindowBackground: Boolean)

val LocalHomeBackdrop = staticCompositionLocalOf<HomeBackdrop?> { null }

/** Null until the screen's size is known; rebuilt when it changes. */
@Composable
fun rememberHomeBackdrop(size: IntSize, isWindowBackground: Boolean): HomeBackdrop? = remember(size, isWindowBackground) {
    if (size.width <= 0 || size.height <= 0) return@remember null
    val bitmap = ImageBitmap(size.width, size.height, ImageBitmapConfig.Argb8888)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(size.width.toFloat(), size.height.toFloat())) { drawBackdrop() }
    HomeBackdrop(bitmap, isWindowBackground)
}

/** A fixed dark gradient with a soft blue glow top-left and violet bottom-right. */
private fun DrawScope.drawBackdrop() {
    val w = size.width; val h = size.height
    drawRect(Brush.radialGradient(listOf(Color(0xFF0B0F18), Color(0xFF05070B)), center = Offset(w / 2, h / 2), radius = max(w, h) * 0.7f))
    drawRect(Brush.radialGradient(listOf(Color(0x405A78FF), Color.Transparent), center = Offset(w * 0.18f, h * 0.08f), radius = w * 0.55f))
    drawRect(Brush.radialGradient(listOf(Color(0xCC281E5A), Color.Transparent), center = Offset(w * 0.92f, h), radius = w * 0.6f))
}

/** Nothing to draw when the backdrop already is the window's background; otherwise (tests, previews) the gradients. */
@Composable
fun HomeBackground(modifier: Modifier = Modifier) {
    val backdrop = LocalHomeBackdrop.current
    if (backdrop?.isWindowBackground == true) return
    Box(modifier.fillMaxSize().drawWithCache {
        onDrawBehind { if (backdrop != null) drawImage(backdrop.bitmap) else drawBackdrop() }
    })
}
