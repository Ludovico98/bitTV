package com.bittv.home.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bittv.home.model.AppEntry
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.data.tmdb.TmdbSearch
import com.bittv.home.model.mainService
import com.bittv.home.model.offer
import com.bittv.home.search.QueryMatcher
import com.bittv.home.ui.components.AppIcon
import com.bittv.home.ui.components.FocusTile
import com.bittv.home.ui.components.PosterArt
import com.bittv.home.ui.components.rememberFocusLook
import com.bittv.home.ui.home.Glyph
import com.bittv.home.ui.home.GlyphIcon
import com.bittv.home.ui.theme.Bg
import com.bittv.home.ui.theme.GlassLine
import com.bittv.home.ui.theme.Ink
import com.bittv.home.ui.theme.Ink2
import com.bittv.home.ui.theme.Ink3
import com.bittv.home.ui.theme.Type
import kotlinx.coroutines.delay

/** The same gap across and down between result tiles (the title page's Similar Content uses this grid too). */
internal val TileGap = 20.dp
internal val TileW = 110.dp
private val KEY_ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl-", "zxcvbnm'.⌫")
private val DEMO_VOICE = listOf("crime series", "the long tide", "comedy movies", "sci-fi on apple tv", "commissario")

/** A results column: two tiles across, two rows down, scrolling down for more. */
private val ColumnW = TileW * 2 + TileGap
private val GridH = 62.dp * 2 + TileGap

/**
 * Search over every title: a QWERTY keyboard for typing and one Speak button in the search bar for voice, which has
 * focus when the page opens. Results split into two columns, Included (on his subscriptions) and Purchase (a service
 * he doesn't have, to rent or buy), with Similar Content underneath. [listen] returns what was said (plan 2: Android
 * speech recognition); the default plays demo phrases.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    pool: List<Title>, subs: Set<Svc>, apps: List<AppEntry>, onOpen: (Title) -> Unit, onBack: () -> Unit,
    listen: suspend () -> String? = defaultDemoListener(),
    query: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
    /** Online search (TMDB) for titles beyond the downloaded catalogue; null on the demo. */
    online: (suspend (String) -> List<Title>)? = null,
    /** What TMDB recommends after a title, for Similar Content; null on the demo. */
    related: (suspend (Title) -> List<Title>)? = null,
) {
    var local by remember { mutableStateOf("") }
    val query = query ?: local
    fun setQuery(v: String) { if (onQueryChange != null) onQueryChange(v) else local = v }
    var listening by remember { mutableStateOf(false) }
    val localResult = remember(query, subs, pool) { QueryMatcher.search(query, pool, subs) }
    // online results join a moment after typing stops (one request per pause, not per letter)
    var onlineFound by remember { mutableStateOf<Pair<String, List<Title>>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (online == null || query.isBlank()) { onlineFound = null; searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        onlineFound = query to online(query)
        searching = false
    }
    val found = remember(localResult, onlineFound, subs, pool) {
        onlineFound?.takeIf { it.first == query }?.let { TmdbSearch.merge(localResult, it.second, subs, pool, query) } ?: localResult
    }
    // Similar Content leads with what TMDB recommends after the top match
    val top = if (query.isBlank()) null else (found.included + found.purchase).firstOrNull()
    var recs by remember { mutableStateOf<Pair<String, List<Title>>?>(null) }
    LaunchedEffect(top?.id) {
        if (related == null || top == null) return@LaunchedEffect
        if (recs?.first != top.id) recs = top.id to related(top)
    }
    val result = remember(found, recs, top?.id) {
        recs?.takeIf { it.first == top?.id && it.second.isNotEmpty() }
            ?.let { found.copy(similar = QueryMatcher.similar(found.included + found.purchase, pool, subs, it.second)) } ?: found
    }
    val qFocus = remember { FocusRequester() }
    val micFocus = remember { FocusRequester() }
    val includedFocus = remember { FocusRequester() }
    val purchaseFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { micFocus.requestFocus() }
    LaunchedEffect(listening) {
        if (!listening) return@LaunchedEffect
        val said = listen()
        if (said != null) setQuery(said.lowercase())
        listening = false
    }
    fun type(c: Char) = setQuery(when (c) { '⌫' -> query.dropLast(1); else -> if (query.length < 40) query + c else query })

    Box(Modifier.fillMaxSize().background(Bg)) {
        // search bar with the Speak button
        Row(
            Modifier.padding(start = 40.dp, end = 40.dp, top = 30.dp).fillMaxWidth().height(46.dp)
                .background(Color(0x14FFFFFF), RoundedCornerShape(23.dp))
                .border(if (listening) 2.dp else 1.dp, if (listening) Color(0xFF5B9BFF) else GlassLine, RoundedCornerShape(23.dp))
                .padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(if (listening) Glyph.MIC else Glyph.SEARCH, Ink2, Modifier.size(22.dp))
            Spacer(Modifier.width(11.dp))
            Text(
                when { listening -> "Listening…"; query.isEmpty() -> "Search your apps, or press Speak"; else -> query },
                style = Type.Title.copy(fontSize = 20.sp, color = if (query.isEmpty() || listening) Ink3 else Ink, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f).testTag("query"), maxLines = 1,
            )
            Text("${subs.size} apps", style = Type.Caption.copy(color = Ink3))
            Spacer(Modifier.width(9.dp))
            // Down from Speak: the keyboard until there is something to show, then the first results
            SpeakButton(listening, Modifier.focusRequester(micFocus).focusProperties {
                down = when {
                    query.isBlank() -> qFocus
                    result.included.isNotEmpty() -> includedFocus
                    result.purchase.isNotEmpty() -> purchaseFocus
                    else -> qFocus
                }
            }) { listening = true }
        }
        // keyboard
        Column(
            Modifier.padding(start = 40.dp, top = 95.dp).width(350.dp)
                .background(Color(0x0EFFFFFF), RoundedCornerShape(15.dp)).border(1.dp, GlassLine, RoundedCornerShape(15.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KEY_ROWS.forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { c -> Key(c.uppercase().takeIf { c != '⌫' } ?: "⌫", "key:$c", 29.dp,
                        if (r == 1 && c == 'q') Modifier.focusRequester(qFocus) else Modifier) { type(c) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Key("space", "key:space", 29.dp * 8 + 28.dp) { type(' ') }
                Key("clear", "key:clear", 29.dp * 2 + 4.dp) { setQuery("") }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.MIC, Color(0xFF8FB8FF), Modifier.size(13.dp)); Spacer(Modifier.width(6.dp))
                Text("Press Speak to search by voice", style = Type.Caption)
            }
        }
        // results
        Column(
            Modifier.padding(start = 410.dp, top = 88.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 40.dp, top = 8.dp, bottom = 80.dp),
        ) {
            if (query.isBlank()) {
                Text("Top picks on your apps", style = Type.Title.copy(fontSize = 15.sp))
                Spacer(Modifier.height(TileGap))
                TileFlow(result.included.take(12), "result", subs, apps, onOpen)
            } else {
                Text("Results for “${query.trim()}”", style = Type.Title.copy(fontSize = 15.sp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                    ResultColumn("Included", "on your apps", result.included, "result", includedFocus, subs, apps, onOpen,
                        empty = if (searching) "Searching…" else "Nothing on your apps")
                    ResultColumn("Purchase", "rent, buy or subscribe", result.purchase, "purchase", purchaseFocus, subs, apps, onOpen,
                        empty = if (searching) "Searching…" else "Nothing else matches")
                }
            }
            if (result.similar.isNotEmpty()) {
                Text("Similar Content", style = Type.Title.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 28.dp))
                Spacer(Modifier.height(TileGap))
                TileFlow(result.similar, "similar", subs, apps, onOpen)
            }
        }
    }
}

/**
 * One of the two result columns: its heading, then its tiles two across in reading order, two rows tall, scrolling
 * a whole row at a time for more. The grid reaches half a gap above and below the rows so a focused tile's glow
 * isn't cut off square; it can't reach a neighbouring row, which is a full gap away.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultColumn(label: String, hint: String, titles: List<Title>, tag: String, focus: FocusRequester,
                         subs: Set<Svc>, apps: List<AppEntry>, onOpen: (Title) -> Unit, empty: String) {
    Column(Modifier.width(ColumnW).testTag("col:$tag")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = Type.Label.copy(color = Ink, fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(6.dp))
            Text(if (titles.isEmpty()) hint else "${titles.size} · $hint", style = Type.Caption.copy(color = Ink3), maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(ColumnW).height(GridH)) {
            if (titles.isEmpty()) Text(empty, style = Type.Body.copy(color = Ink2))
            else CompositionLocalProvider(LocalBringIntoViewSpec provides RowSnap) {
                val state = rememberLazyGridState()
                val first = remember { FocusRequester() }
                var inside by remember { mutableStateOf(false) }
                // online matches arrive after the local ones and can rank above them: show the new best match, unless
                // he is already browsing this column (the grid would otherwise hold its old first tile at the top)
                LaunchedEffect(titles.first().id) { if (!inside) state.scrollToItem(0) }
                LazyVerticalGrid(
                    GridCells.Fixed(2),
                    Modifier.requiredHeight(GridH + TileGap).fillMaxWidth().onFocusChanged { inside = it.hasFocus }.focusRequester(focus)
                        // coming in from Speak or the keyboard: the best match, not whichever tile is nearest (that can be
                        // one composed below the fold, which then scrolls the column away from the top)
                        .focusProperties { onEnter = { if (state.firstVisibleItemIndex == 0) first.requestFocus() } }
                        .focusGroup(),
                    state = state,
                    contentPadding = PaddingValues(vertical = TileGap / 2),
                    horizontalArrangement = Arrangement.spacedBy(TileGap), verticalArrangement = Arrangement.spacedBy(TileGap),
                ) {
                    itemsIndexed(titles, key = { _, t -> t.id }) { i, t ->
                        ResultTile(t, subs, apps, "$tag:$i", onOpen, if (i == 0) Modifier.focusRequester(first) else Modifier)
                    }
                }
            }
        }
    }
}

/**
 * Scrolls a result column just enough to show the focused row inside the half-gap margins, so the two rows on show
 * always sit square in the column (the TV's own rule would hold the focused row 30 % down and cut rows in half).
 */
@OptIn(ExperimentalFoundationApi::class)
private object RowSnap : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val pad = (containerSize - size * 2) / 4   // the half gap: the viewport is two rows, a gap between, a half gap round
        return when {
            offset < pad -> offset - pad
            offset + size > containerSize - pad -> offset + size - (containerSize - pad)
            else -> 0f
        }
    }
}

/** Tiles four across, the same gaps across and down. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileFlow(titles: List<Title>, tag: String, subs: Set<Svc>, apps: List<AppEntry>, onOpen: (Title) -> Unit) {
    FlowRow(maxItemsInEachRow = 4, horizontalArrangement = Arrangement.spacedBy(TileGap), verticalArrangement = Arrangement.spacedBy(TileGap)) {
        titles.forEachIndexed { i, t -> ResultTile(t, subs, apps, "$tag:$i", onOpen) }
    }
}

/** A title's tile: its app's icon top right and, for one not on his subscriptions, how he could get it top left (clear of the name). */
@Composable
internal fun ResultTile(t: Title, subs: Set<Svc>, apps: List<AppEntry>, tag: String, onOpen: (Title) -> Unit,
                        modifier: Modifier = Modifier) {
    FocusTile(onClick = { onOpen(t) }, width = TileW, height = 62.dp,
        modifier = modifier.testTag(tag).semantics { contentDescription = t.name }) {
        PosterArt(t, Modifier.fillMaxSize(), titleScale = 0.72f, overlay = {
            val svc = t.mainService(subs)
            svc?.let { s -> apps.firstOrNull { it.svc == s } }?.let { AppIcon(it, 14.dp, 14.dp, Modifier.align(Alignment.TopEnd).padding(5.dp), radius = 4.dp) }
            t.offer(subs)?.let { o ->
                Text(o, style = Type.Caption.copy(fontSize = 9.sp, color = Ink, fontWeight = FontWeight.SemiBold), maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        })
    }
}

@Composable
private fun Key(label: String, tag: String, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (focused, look) = rememberFocusLook(1.18f)
    Box(
        modifier.testTag(tag).then(look).clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .size(width, 29.dp).background(if (focused) Color.White else Color(0x0FFFFFFF), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.Body.copy(color = if (focused) Color(0xFF0B0F18) else Ink, fontWeight = FontWeight.SemiBold)) }
}

@Composable
private fun SpeakButton(listening: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (focused, look) = rememberFocusLook()
    Row(
        modifier.testTag("mic").then(look).clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .height(30.dp).background(if (focused) Color.White else Color(0x335B9BFF), RoundedCornerShape(15.dp)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (focused) Color(0xFF0B0F18) else Color(0xFFD6E5FF)
        GlyphIcon(Glyph.MIC, c, Modifier.size(14.dp)); Spacer(Modifier.width(5.dp))
        Text(if (listening) "Listening" else "Speak", style = Type.Label.copy(color = c))
    }
}

/** Until speech recognition is wired (plan 2): pretend to listen briefly and return the next demo phrase. */
@Composable
private fun defaultDemoListener(): suspend () -> String? {
    var i by remember { mutableIntStateOf(0) }
    return { delay(1800); DEMO_VOICE[i++ % DEMO_VOICE.size] }
}
