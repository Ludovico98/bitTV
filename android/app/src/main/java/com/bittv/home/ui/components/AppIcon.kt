package com.bittv.home.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bittv.home.model.AppEntry
import com.bittv.home.ui.theme.Bebas
import com.bittv.home.ui.theme.Inter
import com.bittv.home.ui.theme.Playfair

/** Brand-coloured tvOS-shaped app tile (landscape rounded rectangle), drawn rather than shipped as bitmaps. */
@Composable
fun AppIcon(app: AppEntry, width: Dp, height: Dp, modifier: Modifier = Modifier, radius: Dp = 10.dp) {
    val look = looks[app.id] ?: Look(Brush.linearGradient(listOf(Color(0xFF263238), Color(0xFF263238))))
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(radius)).background(look.bg),
        contentAlignment = Alignment.Center,
    ) {
        val fs = with(LocalDensity.current) { height.toSp() }
        look.glyph(fs)
    }
}

private class Look(val bg: Brush, val glyph: @Composable (TextUnit) -> Unit = {})

private fun solid(c: Long) = Brush.linearGradient(listOf(Color(c), Color(c)))
private fun diag(a: Long, b: Long) = Brush.linearGradient(listOf(Color(a), Color(b)))

@Composable
private fun Wordmark(text: String, size: TextUnit, color: Color = Color.White, family: FontFamily = Inter,
                     weight: FontWeight = FontWeight.Bold, style: FontStyle = FontStyle.Normal, brush: Brush? = null) {
    val base = TextStyle(fontFamily = family, fontSize = size, fontWeight = weight, fontStyle = style,
        color = color, textAlign = TextAlign.Center, lineHeight = size * 1.05f)
    Text(text, style = if (brush != null) base.copy(brush = brush) else base)
}

private val looks: Map<String, Look> = mapOf(
    "netflix" to Look(solid(0xFF000000)) { Wordmark("N", it * 0.8f, Color(0xFFE50914), Bebas, FontWeight.Normal) },
    "prime" to Look(solid(0xFF0F171E)) {
        Text(buildAnnotatedString {
            append("prime\n"); withStyle(SpanStyle(color = Color(0xFF1A98FF))) { append("video") }
        }, style = TextStyle(fontFamily = Inter, fontSize = it * 0.22f, fontWeight = FontWeight.Bold, color = Color.White,
            textAlign = TextAlign.Center, lineHeight = it * 0.24f))
    },
    "now" to Look(solid(0xFF000000)) { Wordmark("NOW", it * 0.3f, weight = FontWeight.ExtraBold) },
    "paramount" to Look(diag(0xFF0064FF, 0xFF0A2A8A)) { Wordmark("P+", it * 0.36f) },
    "appletv" to Look(solid(0xFF000000)) { Wordmark("tv", it * 0.38f, weight = FontWeight.SemiBold) },
    "spotify" to Look(solid(0xFF121212)) { SpotifyGlyph(Modifier.size(28.dp)) },
    "smarttube" to Look(solid(0xFFE62117)) { SmartTubeGlyph(Modifier.size(30.dp, 22.dp)) },
    "disney" to Look(diag(0xFF0B1C4F, 0xFF1F5FE0)) { Wordmark("D+", it * 0.34f, family = Playfair, style = FontStyle.Italic) },
    "tubi" to Look(solid(0xFF7408FF)) { Wordmark("tubi", it * 0.32f, Color(0xFFFFF200), weight = FontWeight.ExtraBold) },
    "itvx" to Look(solid(0xFF000000)) {
        Wordmark("ITVX", it * 0.28f, weight = FontWeight.ExtraBold,
            brush = Brush.horizontalGradient(listOf(Color(0xFFFF7A3D), Color(0xFFFF3D8B), Color(0xFF7B5CFF))))
    },
    "la7" to Look(solid(0xFFFFFFFF)) {
        Text(buildAnnotatedString { append("La"); withStyle(SpanStyle(color = Color(0xFFD7141A))) { append("7") } },
            style = TextStyle(fontFamily = Inter, fontSize = it * 0.34f, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111111)))
    },
    "raiplay" to Look(solid(0xFF0A2D6E)) {
        Text(buildAnnotatedString { append("Rai"); withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append("Play") } },
            style = TextStyle(fontFamily = Inter, fontSize = it * 0.22f, fontWeight = FontWeight.ExtraBold, color = Color.White))
    },
    "playstore" to Look(solid(0xFFFFFFFF)) { PlayStoreGlyph(Modifier.size(26.dp)) },
    "buttonmapper" to Look(solid(0xFF263238)) { RemoteGlyph(Modifier.size(26.dp)) },
)

@Composable
fun SpotifyGlyph(modifier: Modifier) = Canvas(modifier) {
    val s = size.minDimension
    drawCircle(Color(0xFF1ED760), s / 2)
    val stroke = Stroke(width = s * 0.07f, cap = StrokeCap.Round)
    for ((i, w) in listOf(0.62f, 0.52f, 0.42f).withIndex()) {
        val y = s * (0.38f + i * 0.13f)
        val path = Path().apply { moveTo(s * (0.5f - w / 2), y); quadraticTo(s * 0.5f, y - s * 0.08f, s * (0.5f + w / 2), y + s * 0.04f) }
        drawPath(path, Color(0xFF121212), style = stroke)
    }
}

@Composable
private fun SmartTubeGlyph(modifier: Modifier) = Canvas(modifier) {
    drawRoundRect(Color.White, cornerRadius = CornerRadius(size.height * 0.28f))
    val p = Path().apply {
        moveTo(size.width * 0.4f, size.height * 0.28f); lineTo(size.width * 0.66f, size.height * 0.5f)
        lineTo(size.width * 0.4f, size.height * 0.72f); close()
    }
    drawPath(p, Color(0xFFE62117))
}

@Composable
private fun PlayStoreGlyph(modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    fun poly(c: Long, pts: List<Offset>) = drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }, Color(c))
    poly(0xFF4285F4, listOf(Offset(w * .17f, h * .1f), Offset(w * .56f, h * .5f), Offset(w * .17f, h * .9f)))
    poly(0xFF34A853, listOf(Offset(w * .17f, h * .1f), Offset(w * .68f, h * .37f), Offset(w * .56f, h * .5f)))
    poly(0xFFEA4335, listOf(Offset(w * .17f, h * .9f), Offset(w * .56f, h * .5f), Offset(w * .68f, h * .63f)))
    poly(0xFFFBBC04, listOf(Offset(w * .68f, h * .37f), Offset(w * .86f, h * .5f), Offset(w * .68f, h * .63f), Offset(w * .56f, h * .5f)))
}

@Composable
private fun RemoteGlyph(modifier: Modifier) = Canvas(modifier) {
    val w = size.width; val h = size.height
    drawRoundRect(Color.White, topLeft = Offset(w * .3f, h * .1f), size = Size(w * .4f, h * .8f),
        cornerRadius = CornerRadius(w * .2f), style = Stroke(w * .07f))
    drawCircle(Color(0xFF4FC3F7), w * .08f, Offset(w * .5f, h * .33f))
    drawLine(Color.White, Offset(w * .42f, h * .55f), Offset(w * .58f, h * .55f), w * .06f, StrokeCap.Round)
    drawLine(Color.White, Offset(w * .42f, h * .68f), Offset(w * .58f, h * .68f), w * .06f, StrokeCap.Round)
}
