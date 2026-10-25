package com.bittv.home.ui.assistant

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.bittv.home.model.WeatherKind
import com.bittv.home.ui.components.LocalMotion
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated weather: a sun with a soft round halo and turning rays, drifting clouds, falling rain. Drawn without a
 * clip, so glows fade out on their own instead of being cut into a square. Still when Animations are off.
 */
@Composable
fun WeatherScene(kind: WeatherKind, modifier: Modifier) {
    val loops = LocalMotion.current.loops
    val tr = rememberInfiniteTransition(label = "wx")
    val spin = if (loops) tr.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin").value else 0f
    val breathe = if (loops) tr.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(3_000), RepeatMode.Reverse), label = "halo").value else 1f
    val drift = if (loops) tr.animateFloat(-5f, 6f, infiniteRepeatable(tween(6_000), RepeatMode.Reverse), label = "drift").value else 0f
    val fall = if (loops) tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "fall").value else 0.4f
    Canvas(modifier) {
        val u = size.width / 85f   // scene drawn on an 85 x 75 grid
        when (kind) {
            WeatherKind.SUN -> sun(Offset(42.5f * u, 37.5f * u), 25f * u, spin, breathe)
            WeatherKind.CLOUD -> { sun(Offset(28f * u, 24f * u), 17f * u, spin, breathe); cloud(Offset((20f + drift * 0.6f) * u, 22f * u), 0.7f * u, Color(0xB3E3E9F3)); cloud(Offset((17f + drift) * u, 36f * u), u, Color(0xFFE3E9F3)) }
            WeatherKind.RAIN -> {
                cloud(Offset((3f + drift * 0.6f) * u, 20f * u), 0.7f * u, Color(0xB38F9CB2)); cloud(Offset((17f + drift) * u, 36f * u), u, Color(0xFF8F9CB2))
                for (i in 0 until 14) {
                    val ph = (fall + (i * 0.13f) % 0.9f) % 1f
                    val x = (6f + i * 5.2f - ph * 3f) * u; val y = (52f + ph * 23f) * u
                    drawLine(Color(0xFF6FB6FF).copy(alpha = 1f - ph), Offset(x, y), Offset(x - 1.5f * u, y + 8f * u), 1.5f * u, StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float, spin: Float, breathe: Float) {
    // soft round halo, centred on the sun, fading to nothing well inside its own circle
    drawCircle(Brush.radialGradient(0f to Color(0x80FFC94D), 0.48f to Color(0x33FFC94D), 1f to Color(0x00FFC94D), center = c, radius = r * 2f * breathe),
        radius = r * 2f * breathe, center = c)
    rotate(spin, c) {
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0); val cs = cos(a).toFloat(); val sn = sin(a).toFloat()
            drawLine(Color(0xB3FFD666), Offset(c.x + cs * r * 1.25f, c.y + sn * r * 1.25f), Offset(c.x + cs * r * 1.45f, c.y + sn * r * 1.45f), r * 0.11f, StrokeCap.Round)
        }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1B0), Color(0xFFFFC94D), Color(0xFFFF9F1A)), center = c, radius = r), r, c)
}

private fun DrawScope.cloud(o: Offset, s: Float, color: Color) {
    drawRoundRect(color, Offset(o.x, o.y), androidx.compose.ui.geometry.Size(60f * s, 23f * s), androidx.compose.ui.geometry.CornerRadius(11.5f * s))
    drawCircle(color, 15.5f * s, Offset(o.x + 25.5f * s, o.y + 1f * s))
    drawCircle(color, 11f * s, Offset(o.x + 41f * s, o.y + 4f * s))
}
