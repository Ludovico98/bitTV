package com.bittv.home.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.tv.material3.Text
import com.bittv.home.model.NewsItem
import com.bittv.home.model.NowPlaying
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.bittv.home.today.TodayItem
import com.bittv.home.today.TodayKind
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.components.SpotifyGlyph
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type

/**
 * The active section: Spotify now playing with its controls (no position: the session's clock wasn't reliable), a
 * news headline in the room that leaves, and information about the day. Its size never changes.
 */
@Composable
fun ActiveSection(
    nowPlaying: NowPlaying?, news: List<NewsItem>, today: List<TodayItem>, actions: HomeActions, playRequester: FocusRequester,
    width: Dp, height: Dp, modifier: Modifier = Modifier,
) {
    Box(
        modifier.testTag("active").size(width, height).clip(RoundedCornerShape(17.dp))
            .background(Color(0x12FFFFFF)).border(1.dp, GlassLine, RoundedCornerShape(17.dp)),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 11.dp)) {
            NowPlayingBlock(nowPlaying, actions, playRequester)
            Divider()
            // the news takes the room left between the player and the day
            Box(Modifier.weight(1f).fillMaxWidth()) { if (news.isNotEmpty()) NewsCarousel(news, actions) }
            Divider()
            today.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(if (item.kind == TodayKind.CLOCK) Glyph.CLOCK else Glyph.CALENDAR, Ink2, Modifier.size(11.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(item.text, style = Type.Caption.copy(color = Color(0xE0FFFFFF)), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).background(GlassLine))

@Composable
private fun NowPlayingBlock(np: NowPlaying?, actions: HomeActions, playRequester: FocusRequester) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(Color(0xFF2B1055), Color.Black)))) {
            if (np?.art != null) AsyncImage(np.art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)   // the real cover
            else Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFFFF5FA2), Color.Transparent), Offset(30f, 10f), 70f)))
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            // the controls sit beside the label, so the news below gets the row they used to take
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpotifyGlyph(Modifier.size(10.dp)); Spacer(Modifier.width(4.dp))
                Text("Spotify", style = Type.Caption.copy(color = Color(0xFF1ED760), fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    CircleButton(Glyph.PREV, "prev", size = 22.dp) { actions.spotify("prev") }
                    CircleButton(if (np?.playing == true) Glyph.PAUSE else Glyph.PLAY, "play", Modifier.focusRequester(playRequester), size = 22.dp) { actions.spotify("toggle") }
                    CircleButton(Glyph.NEXT, "next", size = 22.dp) { actions.spotify("next") }
                }
            }
            Text(np?.track ?: "Nothing playing", style = Type.Body.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(np?.artist ?: "Spotify is idle", style = Type.Label.copy(color = Ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CircleButton(glyph: Glyph, tag: String, modifier: Modifier = Modifier, size: Dp = 26.dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) 1.18f else 1f, LocalMotion.current.tween(160), label = "cb")
    Box(
        modifier.testTag("ctl:$tag").zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .graphicsLayer { scaleX = s; scaleY = s }
            .size(size).background(if (focused) Color.White else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (focused) Color(0xFF0B0F18) else Ink, Modifier.size(size * 0.54f)) }
}

/**
 * The news, one story at a time: the first is this turn's; with the card focused, Right goes on to the next story
 * (round to the first after the last) and Left back. Left on the first leaves the card as before, OK opens the
 * story on show. When the stories take their next turn, the card starts again from the new first one, unless he is
 * reading through them.
 */
@Composable
private fun NewsCarousel(news: List<NewsItem>, actions: HomeActions) {
    var focused by remember { mutableStateOf(false) }
    var at by remember { mutableIntStateOf(0) }
    LaunchedEffect(news.first().headline) { if (!focused) at = 0 }
    val i = at.coerceIn(0, news.size - 1)
    Column(
        Modifier.testTag("news").fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || news.size < 2) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight -> { at = (i + 1) % news.size; true }
                    Key.DirectionLeft -> if (i > 0) { at = i - 1; true } else false
                    else -> false
                }
            }
            .clickable(remember { MutableInteractionSource() }, null) { actions.openNews(news[i]) }
            .background(if (focused) Color(0x1FFFFFFF) else Color.Transparent)
            .then(if (focused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        // a quick fade and nudge in the direction travelled; the size never changes
        val motion = LocalMotion.current
        AnimatedContent(
            targetState = i,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(motion.tween(160)) + slideInHorizontally(motion.tween<IntOffset>(160)) { dir * it / 8 }) togetherWith fadeOut(motion.tween(120))
            },
            label = "news",
        ) { shown ->
            val n = news[shown.coerceIn(0, news.size - 1)]
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.source, style = Type.Caption.copy(color = Ink3, fontWeight = FontWeight.SemiBold), maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (news.size > 1) Text(if (focused) "‹ ${shown + 1} / ${news.size} ›" else "${shown + 1} / ${news.size}",
                        style = Type.Caption.copy(color = if (focused) Ink else Ink3), modifier = Modifier.testTag("news:count"))
                }
                Text(n.headline, style = Type.Body.copy(color = Ink, fontWeight = FontWeight.Medium), maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("news:headline"))
            }
        }
    }
}
