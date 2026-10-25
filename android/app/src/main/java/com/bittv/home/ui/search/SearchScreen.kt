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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.focus.focusRequester
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
import com.bittv.home.model.mainService
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

private val KEY_ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl-", "zxcvbnm'.⌫")
private val DEMO_VOICE = listOf("crime series", "the long tide", "comedy movies", "sci-fi on apple tv", "commissario")

/**
 * Search over titles on his apps: a QWERTY keyboard for typing and one Speak button in the search bar for voice.
 * [listen] returns what was said (plan 2: Android speech recognition); the default plays demo phrases.
 */
@Composable
fun SearchScreen(
    pool: List<Title>, subs: Set<Svc>, apps: List<AppEntry>, onOpen: (Title) -> Unit, onBack: () -> Unit,
    listen: suspend () -> String? = defaultDemoListener(),
    query: String? = null,
    onQueryChange: ((String) -> Unit)? = null,
) {
    var local by remember { mutableStateOf("") }
    val query = query ?: local
    fun setQuery(v: String) { if (onQueryChange != null) onQueryChange(v) else local = v }
    var listening by remember { mutableStateOf(false) }
    val result = remember(query, subs) { QueryMatcher.search(query, pool, subs) }
    val qFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { qFocus.requestFocus() }
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
            SpeakButton(listening) { listening = true }
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
        LazyVerticalGrid(
            GridCells.Fixed(4),
            Modifier.padding(start = 410.dp, top = 88.dp).fillMaxHeight(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 10.dp, end = 40.dp, top = 8.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            item(span = { GridItemSpan(4) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (query.isBlank()) "Top picks on your apps" else "Results for “${query.trim()}”", style = Type.Title.copy(fontSize = 15.sp))
                    if (query.isNotBlank()) { Spacer(Modifier.width(6.dp)); Text("${result.results.size} on your apps", style = Type.Label.copy(color = Ink3)) }
                }
            }
            if (result.results.isEmpty()) {
                item(span = { GridItemSpan(4) }) {
                    Text("Nothing on your apps matches “${query.trim()}”. Apps that are switched off can be turned on in Settings.",
                        style = Type.Body.copy(color = Ink2))
                }
            }
            itemsIndexed(result.results, key = { _, t -> "r:" + t.id }) { i, t -> ResultTile(t, subs, apps, "result:$i", onOpen) }
            if (result.similar.isNotEmpty()) {
                item(span = { GridItemSpan(4) }) { Text("Similar Content", style = Type.Title.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 10.dp)) }
                itemsIndexed(result.similar, key = { _, t -> "s:" + t.id }) { i, t -> ResultTile(t, subs, apps, "similar:$i", onOpen) }
            }
        }
    }
}

@Composable
private fun ResultTile(t: Title, subs: Set<Svc>, apps: List<AppEntry>, tag: String, onOpen: (Title) -> Unit) {
    FocusTile(onClick = { onOpen(t) }, width = 110.dp, height = 62.dp,
        modifier = Modifier.testTag(tag).semantics { contentDescription = t.name }) {
        PosterArt(t, Modifier.fillMaxSize(), titleScale = 0.72f, overlay = {
            val svc = t.mainService(subs)
            apps.firstOrNull { it.svc == svc }?.let { AppIcon(it, 14.dp, 14.dp, Modifier.align(Alignment.TopEnd).padding(5.dp), radius = 4.dp) }
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
private fun SpeakButton(listening: Boolean, onClick: () -> Unit) {
    val (focused, look) = rememberFocusLook()
    Row(
        Modifier.testTag("mic").then(look).clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
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
