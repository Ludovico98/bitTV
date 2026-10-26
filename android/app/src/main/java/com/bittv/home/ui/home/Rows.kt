package com.bittv.home.ui.home

import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.shape.CircleShape
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
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.FocusTile
import com.bittv.home.ui.components.PosterArt
import com.bittv.home.ui.theme.Glass
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type
import kotlinx.coroutines.delay

/** Left room for the focus glow (the strip starts here, on the banner's edge), and the cut-off peek on the right. */
val RowStartPad = 32.dp
/** Tiles sit this far inside the glass strip on every side, so a row's cards are centred in it. */
val TileInset = 12.dp
private val RowPeek = 120.dp

/** How long the page must be still before a row that came into view loads its posters. */
const val IMAGE_SETTLE_MS = 120L

/**
 * One titled row. Left/Right stay inside it (Right at the end does nothing); if the focused tile disappears
 * (e.g. removed from the Watch List) focus moves to its neighbour instead of being lost.
 *
 * Its posters load once the page has been still for [IMAGE_SETTLE_MS] ([pageMoving]): rows that only fly past while
 * Down is held keep their drawn placeholders, so a glide never queues a burst of image decodes. Once loaded they stay.
 * [lifted]: he is moving this row (held OK); [dimmed]: he is moving another one.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeRowSection(
    row: HomeRow, subs: Set<Svc>, apps: List<AppEntry>,
    onOpen: (Title) -> Unit, modifier: Modifier = Modifier,
    onTileFocused: (FocusRequester) -> Unit = {},
    rowRequester: FocusRequester? = null,
    /** True while focus is (or was last, if it was lost) in this row. */
    ownsFocus: () -> Boolean = { true },
    pageMoving: () -> Boolean = { false },
    lifted: Boolean = false,
    dimmed: Boolean = false,
    /** Bumped each time the lifted row moves: its focused tile is brought back to the focus line. */
    moveToken: Int = 0,
) {
    val density = LocalDensity.current
    val spec = remember(density) { with(density) { RowBringIntoView((RowStartPad + TileInset).toPx(), RowPeek.toPx()) } }
    val requesters = remember(row.key) { mutableMapOf<String, FocusRequester>() }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var images by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (!images) {
            snapshotFlow { pageMoving() }.first { !it }
            delay(IMAGE_SETTLE_MS)
            if (!pageMoving()) images = true
        }
    }
    LaunchedEffect(row.items) {
        val k = focusedKey ?: return@LaunchedEffect
        // only for the row in use: the half-hour reshuffle replaces tiles in rows left long ago, and pulled focus back
        if (!ownsFocus()) return@LaunchedEffect
        if (row.items.none { it.key == k } && row.items.isNotEmpty()) {
            val next = row.items[focusedIndex.coerceAtMost(row.items.lastIndex)]
            requesters[next.key]?.requestFocus()
        }
    }
    Column(modifier.testTag("row:${row.key}").graphicsLayer { alpha = if (dimmed) 0.45f else 1f }) {
        Row(Modifier.padding(start = RowStartPad), verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, style = Type.Title)
            row.note?.let { Spacer(Modifier.width(6.dp)); Text(it, style = Type.Label.copy(color = Ink3, fontWeight = FontWeight.Medium)) }
            if (lifted) {   // as on the app bar: up and down arrows say "move me"
                Spacer(Modifier.width(10.dp))
                listOf(90f, -90f).forEach { turn ->
                    Box(Modifier.padding(end = 4.dp).size(16.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        GlyphIcon(Glyph.BACK, Color(0xFF111111), Modifier.size(10.dp).graphicsLayer { rotationZ = turn })
                    }
                }
                Text("Move with Up and Down · OK to place", style = Type.Label.copy(color = Ink2), modifier = Modifier.testTag("row:moving"))
            }
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
                        drawRoundRect(if (lifted) Color(0x33FFFFFF) else Glass, Offset(x, 0f), Size(size.width - x + 40.dp.toPx(), size.height), CornerRadius(15.dp.toPx()))
                    }
                    .focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Right) cancelFocusChange() } },
                contentPadding = PaddingValues(start = RowStartPad + TileInset, end = 40.dp, top = TileInset, bottom = TileInset),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(row.items, key = { it.key }, contentType = { it::class }) { item ->
                    val fr = remember(item.key) { FocusRequester().also { requesters[item.key] = it } }
                    val view = remember { BringIntoViewRequester() }
                    LaunchedEffect(moveToken) { if (moveToken > 0 && item.key == focusedKey) view.bringIntoView() }
                    val mod = Modifier.bringIntoViewRequester(view).focusRequester(fr).onFocusChanged {
                        if (it.isFocused) { focusedKey = item.key; focusedIndex = row.items.indexOfFirst { x -> x.key == item.key }; onTileFocused(fr) }
                    }
                    when (item) {
                        is RowItem.Show -> TitleTile(item, row.key, subs, apps, mod, images, onOpen = { onOpen(item.title) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleTile(item: RowItem.Show, rowKey: String, subs: Set<Svc>, apps: List<AppEntry>, modifier: Modifier, images: Boolean,
                      onOpen: () -> Unit) {
    val t = item.title
    val svc = t.mainService(subs)
    FocusTile(onClick = onOpen, width = 160.dp, height = 90.dp,
        modifier = modifier.testTag("tile:$rowKey:${t.id}")) {
        PosterArt(t, Modifier.fillMaxSize(), load = images, overlay = {
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
