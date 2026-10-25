package com.bittv.home.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density

/** The width BitTV is designed on (1920×1080 at density 2, Google TV's usual setting). */
const val CanvasWidthDp = 960f

/**
 * Lays BitTV out on the same 960×540 dp canvas on any TV, whatever density and size it reports (1080p at 320,
 * 1080p at 240, a 4K UI at 640…), so the screen always looks as designed. Text scale is fixed with it: the layout's
 * labels are sized to their tiles.
 */
@Composable
fun TvCanvas(content: @Composable () -> Unit) {
    val width = LocalWindowInfo.current.containerSize.width
    val density = if (width > 0) width / CanvasWidthDp else LocalDensity.current.density
    CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1f), content = content)
}
