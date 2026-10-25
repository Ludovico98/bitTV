package com.bittv.home.ui.details

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Prefs
import com.bittv.home.model.Progress
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.mainService
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.PillButton
import com.bittv.home.ui.components.PosterArt
import com.bittv.home.ui.components.posterStyle
import com.bittv.home.ui.home.Glyph
import com.bittv.home.ui.home.GlyphIcon
import com.bittv.home.ui.home.meta
import com.bittv.home.ui.theme.Bg
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Type

/** A title's page: Play/Resume on its app, Watch Later, and the apps it is on. */
@Composable
fun DetailsScreen(
    title: Title, prefs: Prefs, progress: Progress?, apps: List<AppEntry>,
    onPlay: (Svc) -> Unit, onToggleWatchLater: () -> Unit, onBack: () -> Unit,
) {
    val svc = title.mainService(prefs.subs)
    val app = apps.firstOrNull { it.svc == svc }
    val saved = title.id in prefs.watchLater
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(title.id) { playFocus.requestFocus() }
    Box(Modifier.fillMaxSize().background(Bg)) {
        PosterArt(title, Modifier.fillMaxSize(), showTitle = false, imageWidth = 1280)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF505070B), 0.45f to Color(0xBF05070B), 1f to Color(0x2605070B))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE605070B))))
        Column(Modifier.padding(start = 80.dp, top = 112.dp).widthIn(max = 480.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                app?.let { AppIcon(it, 17.dp, 17.dp, radius = 4.5.dp) }
                Spacer(Modifier.width(6.dp))
                Text(svc.label.uppercase(), style = Type.Kicker.copy(color = Color(0xB3FFFFFF)))
            }
            Spacer(Modifier.height(9.dp))
            Text(if (title.font == TitleFont.BEBAS || title.font == TitleFont.OSWALD) title.name.uppercase() else title.name,
                style = posterStyle(title.font, 2.9f), maxLines = 2)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("★ ${"%.1f".format(title.score)}", style = Type.Label.copy(color = Color(0xFFFFD84D), fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(9.dp))
                Text(meta(title) + (progress?.let { " · ${it.label}" } ?: ""), style = Type.Label.copy(color = Ink2, fontWeight = FontWeight.Medium))
            }
            Spacer(Modifier.height(12.dp))
            Text(title.blurb, style = Type.Body.copy(fontSize = 15.sp, color = Color(0xE0FFFFFF), lineHeight = 21.sp))
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton("${if (progress != null) "Resume" else "Play"} on ${svc.label}", { onPlay(svc) },
                    Modifier.focusRequester(playFocus).testTag("play"),
                    leading = { app?.let { a -> AppIcon(a, 20.dp, 20.dp, radius = 5.dp) } })
                PillButton(if (saved) "In Watch Later" else "Watch Later", onToggleWatchLater, Modifier.testTag("watchlater"),
                    leading = { c -> GlyphIcon(if (saved) Glyph.STAR_FILLED else Glyph.STAR, c, Modifier.size(15.dp)) })
                PillButton("Back", onBack, leading = { c -> GlyphIcon(Glyph.BACK, c, Modifier.size(15.dp)) })
            }
            Spacer(Modifier.height(24.dp))
            Text("AVAILABLE ON", style = Type.Kicker)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                title.services.forEach { s ->
                    val on = s in prefs.subs
                    PillButton("${s.label} · ${if (!on) "Not subscribed" else if (s.free) "Free" else "Subscribed"}", { onPlay(s) }, height = 30.dp,
                        leading = { apps.firstOrNull { it.svc == s }?.let { a -> AppIcon(a, 20.dp, 20.dp, radius = 5.dp) } })
                }
            }
        }
    }
}
