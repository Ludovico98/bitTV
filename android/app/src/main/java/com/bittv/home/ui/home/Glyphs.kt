package com.bittv.home.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bittv.home.model.WeatherKind

/** Line icons on a 24-unit grid, drawn in [color] (the prototype's SVG icons). */
enum class Glyph { SEARCH, CAST, GLOBE, MEDIA, SETTINGS, PREV, PLAY, PAUSE, NEXT, CLOCK, CALENDAR, MIC, BACK, STAR, STAR_FILLED, HDMI, POWER, MUTED, PHONE, TABLET, LAPTOP }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier) = Canvas(modifier) { drawGlyph(glyph, color) }

fun DrawScope.drawGlyph(g: Glyph, color: Color) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val st = Stroke(width = 1.9f * u, cap = StrokeCap.Round)
    fun path(block: Path.() -> Unit) = Path().apply(block)
    when (g) {
        Glyph.SEARCH -> { drawCircle(color, 7 * u, p(11f, 11f), style = st); drawLine(color, p(20f, 20f), p(16f, 16f), st.width, StrokeCap.Round) }
        Glyph.PHONE -> { drawRoundRect(color, p(7f, 2f), Size(10 * u, 20 * u), CornerRadius(2.5f * u), style = st)
            drawLine(color, p(10.5f, 18.5f), p(13.5f, 18.5f), st.width) }
        Glyph.TABLET -> { drawRoundRect(color, p(4f, 2.5f), Size(16 * u, 19 * u), CornerRadius(2.5f * u), style = st)
            drawLine(color, p(11f, 18.5f), p(13f, 18.5f), st.width) }
        Glyph.LAPTOP -> { drawRoundRect(color, p(4.5f, 5f), Size(15 * u, 10.5f * u), CornerRadius(1.5f * u), style = st)
            drawLine(color, p(2f, 18.5f), p(22f, 18.5f), st.width) }
        Glyph.CAST -> {
            drawPath(path { moveTo(3 * u, 7 * u); lineTo(3 * u, 5 * u); quadraticTo(3 * u, 3 * u, 5 * u, 3 * u); lineTo(19 * u, 3 * u)
                quadraticTo(21 * u, 3 * u, 21 * u, 5 * u); lineTo(21 * u, 19 * u); quadraticTo(21 * u, 21 * u, 19 * u, 21 * u); lineTo(15 * u, 21 * u) }, color, style = st)
            for (r in listOf(3f, 7f, 11f)) drawArc(color, 270f, 90f, false, p(3f - r, 21f - r), Size(2 * r * u, 2 * r * u), style = st)
        }
        Glyph.GLOBE -> { drawCircle(color, 9 * u, p(12f, 12f), style = st); drawLine(color, p(3f, 12f), p(21f, 12f), st.width)
            drawOval(color, p(7.5f, 3f), Size(9 * u, 18 * u), style = st) }
        Glyph.MEDIA -> { drawRoundRect(color, p(3f, 5f), Size(18 * u, 14 * u), CornerRadius(2.5f * u), style = st)
            drawPath(path { moveTo(10 * u, 9 * u); lineTo(15 * u, 12 * u); lineTo(10 * u, 15 * u); close() }, color) }
        Glyph.SETTINGS -> {
            listOf(Triple(6f, 15f, 4f), Triple(12f, 9f, 20f), Triple(18f, 17f, 4f)).forEach { (y, x, _) ->
                drawLine(color, p(4f, y), p(20f, y), st.width, StrokeCap.Round); drawCircle(Color(0xFF1B2130), 2.6f * u, p(x, y)); drawCircle(color, 2f * u, p(x, y), style = Stroke(1.6f * u)) }
        }
        Glyph.PREV -> { drawPath(path { moveTo(19 * u, 5 * u); lineTo(9 * u, 12 * u); lineTo(19 * u, 19 * u); close() }, color); drawLine(color, p(5f, 5f), p(5f, 19f), 2.2f * u) }
        Glyph.NEXT -> { drawPath(path { moveTo(5 * u, 5 * u); lineTo(15 * u, 12 * u); lineTo(5 * u, 19 * u); close() }, color); drawLine(color, p(19f, 5f), p(19f, 19f), 2.2f * u) }
        Glyph.PLAY -> drawPath(path { moveTo(7 * u, 4.5f * u); lineTo(19 * u, 12 * u); lineTo(7 * u, 19.5f * u); close() }, color)
        Glyph.PAUSE -> { drawRect(color, p(7f, 4.5f), Size(3.5f * u, 15 * u)); drawRect(color, p(13.5f, 4.5f), Size(3.5f * u, 15 * u)) }
        Glyph.CLOCK -> { drawCircle(color, 9 * u, p(12f, 12f), style = st); drawPath(path { moveTo(12 * u, 7 * u); lineTo(12 * u, 12 * u); lineTo(15 * u, 14 * u) }, color, style = st) }
        Glyph.CALENDAR -> { drawRoundRect(color, p(3f, 5f), Size(18 * u, 16 * u), CornerRadius(2.5f * u), style = st); drawLine(color, p(3f, 10f), p(21f, 10f), st.width)
            drawLine(color, p(8f, 3f), p(8f, 7f), st.width, StrokeCap.Round); drawLine(color, p(16f, 3f), p(16f, 7f), st.width, StrokeCap.Round) }
        Glyph.MIC -> { drawRoundRect(color, p(9f, 3f), Size(6 * u, 11 * u), CornerRadius(3 * u)); drawArc(color, 0f, 180f, false, p(5.5f, 4.5f), Size(13 * u, 13 * u), style = Stroke(2 * u))
            drawLine(color, p(12f, 17.5f), p(12f, 21f), 2 * u); drawLine(color, p(8.5f, 21f), p(15.5f, 21f), 2 * u, StrokeCap.Round) }
        Glyph.BACK -> drawPath(path { moveTo(15 * u, 5 * u); lineTo(8 * u, 12 * u); lineTo(15 * u, 19 * u) }, color, style = st)
        Glyph.STAR, Glyph.STAR_FILLED -> {
            val star = path { moveTo(12 * u, 3 * u); lineTo(14.8f * u, 8.7f * u); lineTo(21 * u, 9.6f * u); lineTo(16.5f * u, 14 * u); lineTo(17.5f * u, 20.2f * u)
                lineTo(12 * u, 17.3f * u); lineTo(6.5f * u, 20.2f * u); lineTo(7.5f * u, 14 * u); lineTo(3 * u, 9.6f * u); lineTo(9.2f * u, 8.7f * u); close() }
            if (g == Glyph.STAR_FILLED) drawPath(star, color) else drawPath(star, color, style = st)
        }
        Glyph.HDMI -> { drawPath(path { moveTo(3 * u, 8 * u); lineTo(21 * u, 8 * u); lineTo(21 * u, 13 * u); lineTo(18 * u, 16 * u); lineTo(6 * u, 16 * u); lineTo(3 * u, 13 * u); close() }, color, style = st)
            drawLine(color, p(7f, 11f), p(17f, 11f), st.width) }
        Glyph.POWER -> { drawLine(color, p(12f, 3f), p(12f, 11f), st.width, StrokeCap.Round); drawArc(color, -50f, 280f, false, p(4f, 4.5f), Size(16 * u, 16 * u), style = st) }
        Glyph.MUTED -> { drawPath(path { moveTo(4 * u, 9 * u); lineTo(8 * u, 9 * u); lineTo(13 * u, 5 * u); lineTo(13 * u, 19 * u); lineTo(8 * u, 15 * u); lineTo(4 * u, 15 * u); close() }, color)
            drawLine(color, p(16f, 9f), p(21f, 15f), st.width, StrokeCap.Round); drawLine(color, p(21f, 9f), p(16f, 15f), st.width, StrokeCap.Round) }
    }
}

/** Small weather symbols for the clock and forecasts. */
fun DrawScope.drawWeatherSymbol(kind: WeatherKind) {
    val u = size.minDimension / 24f
    val cloud = Color(0xFFC9D3E3)
    fun cloudAt(dy: Float) {
        drawCircle(cloud, 4.2f * u, Offset(9.5f * u, (12.5f + dy) * u)); drawCircle(cloud, 5.4f * u, Offset(14 * u, (11f + dy) * u))
        drawRoundRect(cloud, Offset(4 * u, (12.5f + dy) * u), Size(16.5f * u, 6 * u), CornerRadius(3 * u))
    }
    when (kind) {
        WeatherKind.SUN -> {
            val sun = Color(0xFFFFC94D)
            drawCircle(sun, 4.5f * u, Offset(12 * u, 12 * u))
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0); val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
                drawLine(sun, Offset((12 + 7.5f * c) * u, (12 + 7.5f * s) * u), Offset((12 + 9.5f * c) * u, (12 + 9.5f * s) * u), 1.9f * u, StrokeCap.Round)
            }
        }
        WeatherKind.CLOUD -> cloudAt(0f)
        WeatherKind.RAIN -> {
            cloudAt(-3f)
            for (x in listOf(8f, 12f, 16f)) drawLine(Color(0xFF6FB6FF), Offset(x * u, 18 * u), Offset((x - 1) * u, 21 * u), 1.8f * u, StrokeCap.Round)
        }
    }
}
