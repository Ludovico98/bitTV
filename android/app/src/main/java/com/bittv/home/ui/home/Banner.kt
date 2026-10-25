package com.bittv.home.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import com.bittv.home.model.mainService
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.FocusTile
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.components.PosterArt
import com.bittv.home.ui.components.posterStyle
import com.bittv.home.ui.theme.Type

fun meta(t: Title) = "${t.year} · ${if (t.type == TitleType.SERIES) "Series" else "Movie"} · ${t.genres.joinToString(", ")}"

/** The suggested-title slideshow (7 s per slide, cross-fade), with dots. */
@Composable
fun Banner(items: List<Title>, index: Int, subs: Set<Svc>, apps: List<AppEntry>, width: Dp, height: Dp,
           onClick: (Title) -> Unit, onFocus: (Title) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) { Box(modifier.size(width, height)); return }
    val current = items[index % items.size]
    FocusTile(onClick = { onClick(current) }, onFocus = { onFocus(current) }, width = width, height = height,
        shape = RoundedCornerShape(17.dp), focusScale = 1.025f, modifier = modifier) {
        Crossfade(current, animationSpec = LocalMotion.current.tween(400), label = "slide") { t ->
            Box(Modifier.fillMaxSize()) {
                PosterArt(t, Modifier.fillMaxSize(), showTitle = false, imageWidth = 1280, cornerRadius = 17.dp)
                // real artwork is busy: a deeper shade behind the text (and along the bottom) than the drawn posters need
                val deep = t.art != null
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = if (deep) 0.88f else 0.72f), 0.55f to Color.Black.copy(alpha = if (deep) 0.5f else 0.25f),
                    0.85f to Color.Transparent)))
                if (deep) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                SlideInfo(t, subs, apps, Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 32.dp).widthIn(max = 320.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            items.indices.forEach { i ->
                val on = i == index % items.size
                Box(Modifier.height(6.dp).width(if (on) 20.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
private fun SlideInfo(t: Title, subs: Set<Svc>, apps: List<AppEntry>, modifier: Modifier) {
    val svc = t.mainService(subs)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            apps.firstOrNull { it.svc == svc }?.let { AppIcon(it, 17.dp, 17.dp, radius = 4.5.dp) }
            Spacer(Modifier.width(6.dp))
            Text("FEATURED ON ${svc.label.uppercase()}", style = Type.Kicker.copy(color = Color(0xB3FFFFFF)))
        }
        Spacer(Modifier.height(7.dp))
        Text(if (t.font == TitleFont.BEBAS || t.font == TitleFont.OSWALD) t.name.uppercase() else t.name,
            // long real titles step down in size so they fit on two lines instead of being cut
            style = posterStyle(t.font, when { t.name.length <= 14 -> 2.65f; t.name.length <= 24 -> 2.05f; else -> 1.6f }),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(t.blurb, style = Type.Body.copy(color = Color(0xDBFFFFFF)), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(meta(t), style = Type.Label.copy(color = Color(0x9EFFFFFF)))
    }
}
