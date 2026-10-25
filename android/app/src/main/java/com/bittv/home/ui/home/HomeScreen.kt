package com.bittv.home.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.bittv.home.model.Title
import com.bittv.home.ui.components.AmbientBackground
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

private val ContentX = 137.dp      // where the content area (and the rows' clip edge) starts
private val BannerW = 488.dp
private val BannerH = 215.dp
private val ActiveW = 243.dp

/** True while the screen is resumed: every loop (banner, clock, previews) runs only then. */
@Composable
fun rememberResumed(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/**
 * Home. The content column moves vertically on a critically damped spring so that the focused row sits on a fixed
 * line (where the first row rests): one row per press, and holding Down glides because a new target keeps the
 * current speed. Up/Down never leave the content for the app bar.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    assistantOverlay: (@Composable () -> Unit)? = null,
    now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    val motion = LocalMotion.current
    val resumed = rememberResumed()
    val rows = remember(state) { homeRows(state) }
    val banner = remember(state.titles, state.prefs.subs) { bannerTitles(state) }
    var bannerIndex by remember { mutableIntStateOf(0) }
    var ambient by remember { mutableStateOf<Pair<Color, Color>?>(null) }
    var focusedSection by remember { mutableStateOf("hero") }
    var clock by remember { mutableStateOf(now()) }
    val heroRequester = remember { FocusRequester() }
    var lastContent by remember { mutableStateOf<FocusRequester?>(null) }   // where Right from the app bar returns
    val playRequester = remember { FocusRequester() }

    LaunchedEffect(resumed, banner.size) {
        if (!resumed || banner.isEmpty()) return@LaunchedEffect
        while (true) { delay(7_000); bannerIndex = (bannerIndex + 1) % banner.size }
    }
    LaunchedEffect(resumed) {
        while (resumed) { clock = now(); delay(60_000L - clock.second * 1000L) }
    }
    LaunchedEffect(Unit) { heroRequester.requestFocus() }

    // Vertical page: a LazyColumn whose bring-into-view rule implements the focus line. Lazy layouts take part in
    // focus search (rows beyond the screen are composed on demand), which a hand-translated column cannot do.
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val pageSpec = remember(density, motion) { PageBringIntoView(density, motion) }
    pageSpec.section = focusedSection
    pageSpec.firstRowKey = rows.firstOrNull()?.key

    val showAmbient = state.prefs.ambient
    Box(modifier.fillMaxSize()) {
        AmbientBackground(if (showAmbient) ambient?.first else null, if (showAmbient) ambient?.second else null)

        AppBar(state.apps, state.installed, actions::openApp,
            Modifier.padding(start = 40.dp, top = 30.dp, bottom = 30.dp).width(97.dp).fillMaxHeightCompat(),
            returnTo = { lastContent })

        CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(start = ContentX)
                    // applies to the list's own focus group: Up/Down never leave the content for the app bar
                    .focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) cancelFocusChange() } },
                state = listState,
                contentPadding = PaddingValues(top = 30.dp, bottom = 400.dp),
            ) {
                item(key = "top", contentType = "top") {
                    Row(Modifier.section("top") { focusedSection = it }.padding(start = RowStartPad, end = 40.dp).fillMaxWidth().height(36.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ShortcutButtons(listOf(
                            Shortcut("Search", Glyph.SEARCH, actions::openSearch),
                            Shortcut("Cast", Glyph.CAST, actions::cast),
                            Shortcut("Browser", Glyph.GLOBE) { },
                            Shortcut("Media", Glyph.MEDIA) { },
                            Shortcut("Settings", Glyph.SETTINGS, actions::openSettings),
                        ))
                        Spacer(Modifier.weight(1f))
                        ClockLine(clock.format(TIME), clock.format(DATE), state.weather)
                    }
                }
                item(key = "hero", contentType = "hero") {
                    Row(Modifier.section("hero") { focusedSection = it }.padding(start = RowStartPad, top = 20.dp, bottom = 18.dp)) {
                        Banner(banner, bannerIndex, state.prefs.subs, state.apps, BannerW, BannerH,
                            onClick = actions::openTitle, onFocus = { t: Title -> ambient = Color(t.c1) to Color(t.c2); lastContent = heroRequester },
                            modifier = Modifier.testTag("hero").focusRequester(heroRequester).focusProperties { right = playRequester })
                        Spacer(Modifier.width(20.dp))
                        ActiveSection(state.nowPlaying, state.news.firstOrNull(), state.today, actions, playRequester,
                            ActiveW, BannerH, overlay = assistantOverlay)
                    }
                }
                val near = rows.indexOfFirst { it.key == focusedSection }
                val devIndex = rows.indexOfFirst { it.key == "dev" }
                items(rows, key = { it.key }, contentType = { if (it.key == "dev") "dev" else "row" }) { row ->
                    HomeRowSection(
                        row, state.prefs.subs, state.apps,
                        animateDevices = motion.loops && resumed && devIndex >= 0 && kotlin.math.abs(near - devIndex) <= 1,
                        onOpen = actions::openTitle,
                        onFocusTitle = { t -> ambient = t?.let { Color(it.c1) to Color(it.c2) } ?: (Color(0xFF0B2A6B) to Color(0xFF2F7BFF)) },
                        onDevice = { },
                        modifier = Modifier.section(row.key) { focusedSection = it }.padding(top = 8.dp),
                        onTileFocused = { lastContent = it },
                    )
                }
            }
        }

        // a small clock once the top bar has scrolled away
        val scrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
        Text(clock.format(TIME), style = Type.Clock.copy(fontSize = 17.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 40.dp).alpha(if (scrolledAway) 1f else 0f)
                .background(Color(0x99080A10), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

private fun Modifier.section(key: String, onFocused: (String) -> Unit) = onFocusChanged { if (it.hasFocus) onFocused(key) }

/**
 * The focus line: the focused row's tiles sit where the first row's tiles rest, so every press moves exactly one
 * row and holding Down glides (critically damped spring). The top bar, the banner and the first row bring the page
 * back to the top; a taller row (Devices) rises just enough to fit above the bottom safe area.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PageBringIntoView(density: androidx.compose.ui.unit.Density, private val motion: com.bittv.home.ui.components.Motion) : BringIntoViewSpec {
    var section: String = "hero"
    var firstRowKey: String? = null
    private val line = with(density) { 370.dp.toPx() }      // where the first row's tiles rest on screen
    private val safe = with(density) { 30.dp.toPx() }
    private val caption = with(density) { 27.dp.toPx() }
    private val devTile = with(density) { 158.dp.toPx() * 1.1f }   // focused PS5 tile, scaled

    @Deprecated("Animation is still honoured by the scrollable; kept for the spring feel")
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> get() = motion.pageSpring()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (section == "top" || section == "hero" || section == firstRowKey) return -100_000f   // clamped at the top
        var d = offset - line
        // the Devices row is laid out by its tallest tile (the PS5 preview), whichever tile is focused
        val h = if (section == "dev") maxOf(size, devTile) else size
        val bottom = offset + h + caption - d
        if (bottom > containerSize - safe) d += bottom - (containerSize - safe)
        return d
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun Modifier.fillMaxHeightCompat() = this.then(Modifier.fillMaxSize())
