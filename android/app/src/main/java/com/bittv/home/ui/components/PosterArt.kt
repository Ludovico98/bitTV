package com.bittv.home.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxSize
import coil3.compose.AsyncImage
import com.bittv.home.model.tmdbImage
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.ui.theme.Bebas
import com.bittv.home.ui.theme.Inter
import com.bittv.home.ui.theme.Oswald
import com.bittv.home.ui.theme.Playfair
import kotlin.math.max

/** Mix toward white (amt > 0) or black (amt < 0), as the prototype's mixA(). */
fun Color.mix(amt: Float): Color {
    val t = if (amt < 0) 0f else 1f; val k = kotlin.math.abs(amt)
    return Color(red + (t - red) * k, green + (t - green) * k, blue + (t - blue) * k, alpha)
}

/**
 * A title's artwork: the TMDB backdrop when there is one (the prototype's gradient shows while it loads), else the
 * demo gradient; then the title in its wordmark font. [imageWidth] is the TMDB size to load (780 tiles, 1280 large).
 */
@Composable
fun PosterArt(title: Title, modifier: Modifier = Modifier, titleScale: Float = 1f, showTitle: Boolean = true,
              imageWidth: Int = 780, overlay: @Composable BoxScope.() -> Unit = {}) {
    val c1 = Color(title.c1); val c2 = Color(title.c2)
    Box(
        modifier.drawWithCache {
            val w = size.width; val h = size.height
            val glow = Brush.radialGradient(listOf(c2.copy(alpha = 0.85f), c2.copy(alpha = 0f)), Offset(w * 0.8f, h * 0.15f), max(w, h) * 0.6f)
            val low = Brush.radialGradient(listOf(c1.mix(0.12f), Color.Transparent), Offset(w * 0.08f, h), max(w, h) * 0.7f)
            val base = Brush.linearGradient(listOf(c1.mix(0.2f), c1, c1.mix(-0.55f)), Offset.Zero, Offset(w * 0.35f, h))
            val streak = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                Offset(w * 0.3f, 0f), Offset(w * 0.55f, h * 0.4f))
            val vignette = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), startY = h * 0.45f)
            onDrawBehind { drawRect(base); drawRect(low); drawRect(glow); drawRect(streak); drawRect(vignette) }
        },
    ) {
        title.art?.let { art ->
            AsyncImage(tmdbImage(art, imageWidth), null, Modifier.fillMaxSize().drawWithContent {
                drawContent()   // the same bottom shade as the drawn posters keeps the title readable on any artwork
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = size.height * 0.4f))
            }, contentScale = ContentScale.Crop)
        }
        if (showTitle) {
            Text(
                if (title.font == TitleFont.BEBAS || title.font == TitleFont.OSWALD) title.name.uppercase() else title.name,
                style = posterStyle(title.font, titleScale), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 9.dp, bottom = 8.dp),
            )
        }
        overlay()
    }
}

fun posterStyle(font: TitleFont, scale: Float): TextStyle {
    val shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 2f), 12f)
    fun s(v: Float): TextUnit = (v * scale).sp
    return when (font) {
        TitleFont.BEBAS -> TextStyle(fontFamily = Bebas, fontSize = s(25f), letterSpacing = 0.5.sp, color = Color.White, shadow = shadow, lineHeight = s(25f))
        TitleFont.PLAYFAIR -> TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, fontSize = s(17f), color = Color.White, shadow = shadow, lineHeight = s(18f))
        TitleFont.OSWALD -> TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = s(16f), letterSpacing = 1.sp, color = Color.White, shadow = shadow, lineHeight = s(17f))
        TitleFont.INTER -> TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = s(15.5f), color = Color.White, shadow = shadow, lineHeight = s(16.5f))
    }
}
