package com.bittv.home.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Home's background: a fixed dark gradient with a soft blue glow top-left and violet bottom-right, built once and
 * never redrawn (the ambient colour bleed was removed: it cost too much on the TV's GPU).
 */
@Composable
fun HomeBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().drawWithCache {
        val w = size.width; val h = size.height
        val base = Brush.radialGradient(listOf(Color(0xFF0B0F18), Color(0xFF05070B)), center = Offset(w / 2, h / 2), radius = max(w, h) * 0.7f)
        val top = Brush.radialGradient(listOf(Color(0x405A78FF), Color.Transparent), center = Offset(w * 0.18f, h * 0.08f), radius = w * 0.55f)
        val bottom = Brush.radialGradient(listOf(Color(0xCC281E5A), Color.Transparent), center = Offset(w * 0.92f, h), radius = w * 0.6f)
        onDrawBehind { drawRect(base); drawRect(top); drawRect(bottom) }
    })
}
