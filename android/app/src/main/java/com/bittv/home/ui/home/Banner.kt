package com.bittv.home.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * The suggested-title slideshow (7 s per slide, cross-fade), with dots. Down from the banner focuses the dots: there
 * Left/Right change the slide, OK opens it, Up goes back to the banner and Down to the row below ([dotsDown]).
 */
@Composable
fun Banner(items: List<Title>, index: Int, subs: Set<Svc>, apps: List<AppEntry>, width: Dp, height: Dp,
           onClick: (Title) -> Unit, onFocus: (Title) -> Unit, modifier: Modifier = Modifier,
           onIndexChange: (Int) -> Unit = {}, dots: FocusRequester = remember { FocusRequester() },
           bannerRequester: FocusRequester? = null, dotsDown: FocusRequester? = null, onDotsFocus: (Boolean) -> Unit = {}) {
    if (items.isEmpty()) { Box(modifier.size(width, height)); return }
    val current = items[index % items.size]
    var dotsFocused by remember { mutableStateOf(false) }
    FocusTile(onClick = { onClick(current) }, onFocus = { onFocus(current) }, width = width, height = height,
        shape = RoundedCornerShape(17.dp), focusScale = 1.025f, lit = dotsFocused,
        modifier = modifier.focusProperties { if (items.size > 1) down = dots }) {
        Crossfade(current, animationSpec = LocalMotion.current.tween(400), label = "slide") { t ->
            Box(Modifier.fillMaxSize().testTag("slide:${t.id}").graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) {
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
        // the dots sit where they always did: the focus pill only adds a background around them
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp - DotsPadV)
            .dotsZone(items.size > 1, dots, bannerRequester, dotsDown, onFocused = { dotsFocused = it; onDotsFocus(it) },
                onStep = { d -> onIndexChange(((index + d) % items.size + items.size) % items.size) }, onOpen = { onClick(current) })
            .background(if (dotsFocused) Color(0x8C000000) else Color.Transparent, RoundedCornerShape(50))
            .border(1.5.dp, if (dotsFocused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = DotsPadH, vertical = DotsPadV),
            horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            items.indices.forEach { i ->
                val on = i == index % items.size
                Box(Modifier.height(6.dp).width(if (on) 20.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (on) 1f else 0.4f), RoundedCornerShape(3.dp)))
            }
        }
    }
}

private val DotsPadH = 9.dp
private val DotsPadV = 6.dp

/** The dots as a focus stop of their own; Left/Right never leave it (no wandering into the app bar). */
private fun Modifier.dotsZone(enabled: Boolean, self: FocusRequester, up: FocusRequester?, down: FocusRequester?,
                              onFocused: (Boolean) -> Unit, onStep: (Int) -> Unit, onOpen: () -> Unit): Modifier {
    if (!enabled) return this
    return focusRequester(self)
        .focusProperties { up?.let { this.up = it }; down?.let { this.down = it } }
        .onFocusChanged { onFocused(it.isFocused) }
        .onKeyEvent { e ->
            when (e.key) {
                Key.DirectionLeft, Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown) onStep(if (e.key == Key.DirectionRight) 1 else -1); true }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (e.type == KeyEventType.KeyUp) onOpen(); true }
                else -> false
            }
        }
        // its own node: inside the banner's clickable it would otherwise merge into the banner
        .semantics(mergeDescendants = true) {}
        .testTag("hero:dots")
        .focusable()
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
