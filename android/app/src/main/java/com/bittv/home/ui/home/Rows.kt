package com.bittv.home.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.mainService
import com.bittv.home.platform.HdmiPreview
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.FocusTile
import com.bittv.home.ui.components.PosterArt
import com.bittv.home.ui.theme.Glass
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Left room for the focus glow (the strip starts here, on the banner's edge), and the cut-off peek on the right. */
val RowStartPad = 32.dp
/** Tiles sit this far inside the glass strip on every side, so a row's cards are centred in it. */
val TileInset = 12.dp
private val RowPeek = 120.dp

/**
 * One titled row. Left/Right stay inside it (Right at the end does nothing); if the focused tile disappears
 * (e.g. removed from Watch Later) focus moves to its neighbour instead of being lost.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeRowSection(
    row: HomeRow, subs: Set<Svc>, apps: List<AppEntry>, animateDevices: Boolean,
    onOpen: (Title) -> Unit, onFocusTitle: (Title?) -> Unit, onDevice: (String) -> Unit, modifier: Modifier = Modifier,
    onTileFocused: (FocusRequester) -> Unit = {},
    rowRequester: FocusRequester? = null,
    /** HDMI devices that are on may show their live picture (the row has focus and Home is in front). */
    previewDevices: Boolean = false,
    onPreviewBlank: (String) -> Unit = {},
    /** True while focus is (or was last, if it was lost) in this row. */
    ownsFocus: () -> Boolean = { true },
) {
    val density = LocalDensity.current
    val spec = remember(density) { with(density) { RowBringIntoView((RowStartPad + TileInset).toPx(), RowPeek.toPx()) } }
    val requesters = remember(row.key) { mutableMapOf<String, FocusRequester>() }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(row.items) {
        val k = focusedKey ?: return@LaunchedEffect
        // only for the row in use: the half-hour reshuffle replaces tiles in rows left long ago, and pulled focus back
        if (!ownsFocus()) return@LaunchedEffect
        if (row.items.none { it.key == k } && row.items.isNotEmpty()) {
            val next = row.items[focusedIndex.coerceAtMost(row.items.lastIndex)]
            requesters[next.key]?.requestFocus()
        }
    }
    Column(modifier.testTag("row:${row.key}")) {
        Row(Modifier.padding(start = RowStartPad), verticalAlignment = Alignment.Bottom) {
            Text(row.title, style = Type.Title)
            row.note?.let { Spacer(Modifier.width(6.dp)); Text(it, style = Type.Label.copy(color = Ink3, fontWeight = FontWeight.Medium)) }
        }
        Spacer(Modifier.height(10.dp))
        if (row.items.isEmpty()) {
            Box(Modifier.padding(start = RowStartPad, top = TileInset, bottom = TileInset).size(340.dp, 90.dp)
                .border(1.dp, Color(0x2EFFFFFF), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                Text(row.empty.orEmpty(), style = Type.Label.copy(color = Ink2), textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp))
            }
            return@Column
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                Modifier
                    .then(if (rowRequester != null) Modifier.focusRequester(rowRequester) else Modifier)
                    .fillMaxWidth()
                    .testTag("strip:${row.key}")
                    .fadeIntoGap()
                    .drawBehind { // the glass strip behind the tiles, open on the right; its left edge lines up with the banner,
                        // leaving the gap to the app bar clear (tiles scrolled past it still peek into that gap)
                        val x = RowStartPad.toPx()
                        drawRoundRect(Glass, Offset(x, 0f), Size(size.width - x + 40.dp.toPx(), size.height), CornerRadius(15.dp.toPx()))
                    }
                    .focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() } },
                contentPadding = PaddingValues(start = RowStartPad + TileInset, end = 40.dp, top = TileInset, bottom = TileInset),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(row.items, key = { it.key }, contentType = { it::class }) { item ->
                    val fr = remember(item.key) { FocusRequester().also { requesters[item.key] = it } }
                    val mod = Modifier.focusRequester(fr).onFocusChanged {
                        if (it.isFocused) { focusedKey = item.key; focusedIndex = row.items.indexOfFirst { x -> x.key == item.key }; onTileFocused(fr) }
                    }
                    when (item) {
                        is RowItem.Show -> TitleTile(item, row.key, subs, apps, mod, onOpen = { onOpen(item.title) }, onFocus = { onFocusTitle(item.title) })
                        is RowItem.Device -> DeviceTile(item, animateDevices, previewDevices, mod, onClick = { onDevice(item.key) }, onFocus = { onFocusTitle(null) },
                            onNoPicture = { item.inputId?.let(onPreviewBlank) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleTile(item: RowItem.Show, rowKey: String, subs: Set<Svc>, apps: List<AppEntry>, modifier: Modifier,
                      onOpen: () -> Unit, onFocus: () -> Unit) {
    val t = item.title
    val svc = t.mainService(subs)
    FocusTile(onClick = onOpen, onFocus = onFocus, width = 160.dp, height = 90.dp,
        modifier = modifier.testTag("tile:$rowKey:${t.id}")) {
        PosterArt(t, Modifier.fillMaxSize(), overlay = {
            svc?.let { s -> apps.firstOrNull { it.svc == s } }?.let { AppIcon(it, 18.dp, 18.dp, Modifier.align(Alignment.TopEnd).padding(6.dp), radius = 5.dp) }
            if (item.star) GlyphIcon(Glyph.STAR_FILLED, Color(0xFFFFD84D), Modifier.align(Alignment.TopStart).padding(7.dp).size(12.dp))
            item.progress?.let { p ->
                Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 9.dp, vertical = 6.dp).fillMaxWidth().height(3.dp)
                    .background(Color(0x4DFFFFFF), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(p).height(3.dp).background(Color.White, RoundedCornerShape(2.dp)))
                }
            }
        })
    }
}

@Composable
private fun DeviceTile(item: RowItem.Device, animate: Boolean, preview: Boolean, modifier: Modifier, onClick: () -> Unit, onFocus: () -> Unit,
                       onNoPicture: () -> Unit) {
    val live = item.live
    val w = if (live) 280.dp else 160.dp; val h = if (live) 158.dp else 90.dp
    // a session switches the TV's picture path to HDMI: open one only once the row has kept focus for a moment
    val wanted = preview && item.on && item.inputId != null
    var tuned by remember { mutableStateOf(false) }
    LaunchedEffect(wanted) { tuned = false; if (wanted) { delay(HdmiPreview.DWELL_MS); tuned = true } }
    FocusTile(onClick = onClick, onFocus = onFocus, width = w, height = h, lift = item.inputId == null,
        modifier = modifier.testTag("tile:dev:${item.key}")) {
        if (live) {
            when {
                item.inputId == null -> Ps5Preview(animate, Modifier.fillMaxSize())
                item.on && tuned && wanted -> TvPreview(item.inputId, Modifier.fillMaxSize(), onNoPicture)
                item.on -> Box(Modifier.fillMaxSize().background(DeviceShade))
                // resting: say so, never connect (that would wake it)
                else -> Box(Modifier.fillMaxSize().background(DeviceShade), contentAlignment = Alignment.Center) {
                    Text(if (item.noPicture) "No picture" else "In rest mode", style = Type.Label.copy(color = Ink2))
                }
            }
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.on) Text("LIVE", style = Type.Caption.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.background(Color(0xFFE5322D), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.label, style = Type.Label.copy(color = Color.White))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp).background(Color(0x73000000), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) { GlyphIcon(Glyph.MUTED, Color.White, Modifier.size(11.dp)) }
        } else {
            Column(Modifier.fillMaxSize().background(DeviceShade),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GlyphIcon(Glyph.HDMI, Ink3, Modifier.size(27.dp))
                Text(item.label, style = Type.Label.copy(color = Ink2))
                Text("No signal", style = Type.Caption.copy(color = Ink3))
            }
        }
    }
}

private val DeviceShade = Brush.linearGradient(listOf(Color(0xFF1A1F2B), Color(0xFF0B0E14)))

private val GAMES = listOf("Astro’s Playroom", "Gran Turismo 7", "EA Sports FC", "Spider-Man 2", "Returnal", "Ratchet & Clank")
private val GAME_COLORS = listOf(0xFF3AA0FF, 0xFFD6362E, 0xFF25C26E, 0xFFC3122F, 0xFF7B4DFF, 0xFFFF9F1A).map { Color(it) }

/**
 * Stand-in for the live HDMI picture (the emulator has no HDMI input). Animates only while the Devices row is
 * near the focus line and Animations are on; otherwise a still frame.
 */
@Composable
private fun Ps5Preview(animate: Boolean, modifier: Modifier) {
    val t = if (animate) rememberInfiniteTransition(label = "ps5").animateFloat(0f, 60f,
        infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart), label = "ps5t").value else 0f
    val sel = ((t / 2.4f).toInt()) % GAMES.size
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF04102E), Color(0xFF0B3A8C))))
            for (i in 0 until 3) {
                val p = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    while (x <= size.width) { lineTo(x, size.height * 0.6f + sin(x / 45f + t * 0.6f + i * 1.7f) * 8.dp.toPx() + i * 11.dp.toPx()); x += 6f }
                    lineTo(size.width, size.height); close()
                }
                drawPath(p, Color(0xFF5096FF).copy(alpha = 0.08f + i * 0.05f))
            }
            var x = 14.dp.toPx()
            GAME_COLORS.forEachIndexed { i, c ->
                val s = (if (i == sel) 39 else 29).dp.toPx(); val y = (if (i == sel) 31 else 36).dp.toPx()
                drawRoundRect(c, Offset(x, y), Size(s, s), CornerRadius(6.dp.toPx()))
                if (i == sel) drawRoundRect(Color.White, Offset(x, y), Size(s, s), CornerRadius(6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                x += s + 7.dp.toPx()
            }
        }
        Column(Modifier.padding(start = 14.dp, top = 84.dp)) {
            Text(GAMES[sel], style = Type.Label.copy(color = Color.White))
            Text("Resume · Trophies 62%", style = Type.Caption.copy(color = Color(0xA6FFFFFF)))
        }
    }
}

private val GlowBleed = 28.dp

/**
 * Tiles scrolled left of the glass strip fade out across the gap to the app bar instead of running into it. The
 * fade is a mask over an offscreen layer, so the layer is grown by [GlowBleed] above and below: the focus glow is
 * never cut off square. Keep the layer: it is also each row's render cache (measured on the TV: drawing the fade as
 * a plain overlay without the layer made every frame ~15 ms slower, as unchanged rows were redrawn tile by tile).
 */
private fun Modifier.fadeIntoGap(): Modifier = this
    .layout { m, c ->
        val b = GlowBleed.roundToPx()
        val p = m.measure(c.copy(minHeight = c.minHeight + 2 * b, maxHeight = if (c.hasBoundedHeight) c.maxHeight + 2 * b else c.maxHeight))
        layout(p.width, p.height - 2 * b) { p.place(0, -b) }
    }
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        // clear next to the app bar, fully visible from the strip's edge
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 10.dp.toPx(), endX = RowStartPad.toPx()), blendMode = BlendMode.DstIn)
    }
    .padding(vertical = GlowBleed)
