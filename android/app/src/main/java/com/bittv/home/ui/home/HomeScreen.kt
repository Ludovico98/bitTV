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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.border
import com.bittv.home.ui.theme.GlassLine
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
import com.bittv.home.ui.components.HomeBackground
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
    /** False while another screen covers Home: its loops pause. */
    active: Boolean = true,
    /** Bumped when Home is shown again: focus returns to where it was. */
    returnToken: Int = 0,
    /** Bumped by Back on Home: focus (and the page) go back to the top. */
    topToken: Int = 0,
) {
    val motion = LocalMotion.current
    val resumed = rememberResumed() && active
    val rows = remember(state) { homeRows(state) }
    val banner = remember(state.titles, state.prefs.subs) { bannerTitles(state) }
    var bannerIndex by remember { mutableIntStateOf(0) }
    var focusedSection by remember { mutableStateOf("hero") }
    var clock by remember { mutableStateOf(now()) }
    val heroRequester = remember { FocusRequester() }
    val dotsRequester = remember { FocusRequester() }
    val searchRequester = remember { FocusRequester() }
    var lastContent by remember { mutableStateOf<FocusRequester?>(null) }   // where Right from the app bar (and Back) return
    val playRequester = remember { FocusRequester() }
    val overlayRequester = remember { FocusRequester() }
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var lastRowKey by remember { mutableStateOf<String?>(null) }
    var castOpen by remember { mutableStateOf(false) }
    var castReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(castOpen) { if (!castOpen) castReturn?.let { castReturn = null; it.requestFocus() } }
    LaunchedEffect(active) { if (!active) castOpen = false }   // another screen opened: the menu goes
    fun tryFocus(r: FocusRequester?) = r != null && r.requestFocus()
    // where focus goes back to: the last item, else its row (the item may have been removed), else the banner
    fun returnFocus(): Boolean = tryFocus(lastContent) || tryFocus(lastRowKey?.let { rowRequesters[it] }) || tryFocus(heroRequester)

    // the slideshow holds still while its dots are focused (the slides are being chosen by hand), and a slide
    // changed by hand gets its full 7 s after
    var dotsFocused by remember { mutableStateOf(false) }
    LaunchedEffect(resumed, banner.size, bannerIndex, dotsFocused) {
        if (!resumed || banner.isEmpty() || dotsFocused) return@LaunchedEffect
        delay(7_000); bannerIndex = (bannerIndex + 1) % banner.size
    }
    LaunchedEffect(resumed) {
        while (resumed) { clock = now(); delay(60_000L - clock.second * 1000L) }
    }
    // First focus is the banner. Asked before the TV gives the window key focus, Android may instead hand focus to the
    // first item on screen (the top of the app bar), so it is asked again when the window gains focus — but only while
    // nothing in Home's content has had focus yet, so a press already made is never pulled back.
    var contentFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { heroRequester.requestFocus() }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) { if (windowFocused && !contentFocused) heroRequester.requestFocus() }
    LaunchedEffect(returnToken) { if (returnToken > 0) returnFocus() }

    // Vertical page: a LazyColumn whose bring-into-view rule implements the focus line. Lazy layouts take part in
    // focus search (rows beyond the screen are composed on demand), which a hand-translated column cannot do.
    val listState = rememberLazyListState()
    // read by the rows instead of focusedSection itself: Up/Down then recompose no row unless the Devices row's
    // "near the focus line" changes
    val devicesNear by remember(rows) { derivedStateOf {
        val near = rows.indexOfFirst { it.key == focusedSection }; val dev = rows.indexOfFirst { it.key == "dev" }
        dev >= 0 && kotlin.math.abs(near - dev) <= 1
    } }
    // the Devices row is coming up: read the devices' power again, so a preview only opens for one that is on
    LaunchedEffect(devicesNear, resumed) { if (devicesNear && resumed) actions.refreshDevices() }
    // a live HDMI preview puts the whole panel on the HDMI picture settings (local dimming, dynamic backlight on the
    // device's picture — seen on the TV): only while the Devices row itself has focus
    val devicesFocused by remember { derivedStateOf { focusedSection == "dev" } }
    val density = LocalDensity.current
    // every item's height as last laid out, by key: how far the page is from its top ([pageScrolled])
    val heights = remember { mutableMapOf<String, Int>() }
    fun keyAt(i: Int) = when (i) { 0 -> "top"; 1 -> "hero"; else -> rows.getOrNull(i - 2)?.key }
    val pageSpec = remember(density, motion) { PageBringIntoView(density, motion) }
    pageSpec.firstRowKey = rows.firstOrNull()?.key
    pageSpec.scrolled = { pageScrolled(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) { i -> keyAt(i)?.let(heights::get) } }
    // Back on Home: focus to the banner. Deep down the list may have disposed it (focus would go nowhere): then
    // straight to the top first
    LaunchedEffect(topToken) {
        if (topToken == 0 || heroRequester.requestFocus()) return@LaunchedEffect
        listState.scrollToItem(0); withFrameNanos { }
        heroRequester.requestFocus()
    }

    // a press that arrives before anything on Home was focused (e.g. Down held while BitTV appears) places focus on
    // the banner first, like tvOS
    Box(modifier.fillMaxSize().onPreviewKeyEvent { e ->
        if (!contentFocused && e.type == KeyEventType.KeyDown) heroRequester.requestFocus() else false
    }) {
        HomeBackground()

        AppBar(state.apps, state.installed, actions::openApp,
            Modifier.padding(start = 40.dp, top = 30.dp, bottom = 30.dp).width(97.dp).fillMaxHeightCompat(),
            returnTo = { returnFocus() }, onReorder = actions::reorderApps)

        CompositionLocalProvider(LocalBringIntoViewSpec provides pageSpec) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(start = ContentX)
                    .onFocusChanged { if (it.hasFocus) contentFocused = true }
                    // applies to the list's own focus group: Up/Down never leave the content for the app bar
                    .focusProperties { onExit = { if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) cancelFocusChange() } },
                state = listState,
                contentPadding = PaddingValues(top = 30.dp, bottom = 400.dp),
            ) {
                item(key = "top", contentType = "top") {
                    Row(Modifier.measured("top", heights).section("top") { focusedSection = it; pageSpec.section = it }.padding(start = RowStartPad, end = 40.dp).fillMaxWidth().height(36.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        ShortcutButtons(onFocused = { lastContent = it; lastRowKey = null }, items = listOf(
                            Shortcut("Search", Glyph.SEARCH, searchRequester, actions::openSearch),
                            Shortcut("Cast", Glyph.CAST) { castReturn = lastContent; castOpen = true; actions.cast() },
                            Shortcut("Browser", Glyph.GLOBE, onClick = actions::openBrowser),
                            Shortcut("Media", Glyph.MEDIA, onClick = actions::openMedia),
                            Shortcut("Settings", Glyph.SETTINGS, onClick = actions::openSettings),
                        ))
                        Spacer(Modifier.weight(1f))
                        ClockLine(clock.format(TIME), clock.format(DATE), state.weather)
                    }
                }
                item(key = "hero", contentType = "hero") {
                    Row(Modifier.measured("hero", heights).section("hero") { focusedSection = it; pageSpec.section = it }.padding(start = RowStartPad, top = 20.dp, bottom = 18.dp)) {
                        Banner(banner, bannerIndex, state.prefs.subs, state.apps, BannerW, BannerH,
                            onClick = actions::openTitle, onFocus = { _: Title -> lastContent = heroRequester; lastRowKey = null },
                            onIndexChange = { bannerIndex = it }, bannerRequester = heroRequester, dots = dotsRequester,
                            // a slide opened from the dots comes back to the dots
                            onDotsFocus = { dotsFocused = it; if (it) { lastContent = dotsRequester; lastRowKey = null } },
                            dotsDown = rows.firstOrNull()?.let { rowRequesters.getOrPut(it.key) { FocusRequester() } },
                            modifier = Modifier.testTag("hero").focusRequester(heroRequester).focusProperties {
                                right = if (assistantOverlay != null) overlayRequester else playRequester
                                up = searchRequester   // the first shortcut, not whichever is nearest the banner's middle
                            })
                        Spacer(Modifier.width(20.dp))
                        ActiveSection(state.nowPlaying, state.news.firstOrNull(), state.today, actions, playRequester,
                            ActiveW, BannerH, overlay = assistantOverlay, overlayFocus = overlayRequester)
                    }
                }
                items(rows, key = { it.key }, contentType = { if (it.key == "dev") "dev" else "row" }) { row ->
                    HomeRowSection(
                        row, state.prefs.subs, state.apps,
                        animateDevices = motion.loops && resumed && devicesNear,
                        onOpen = actions::openTitle,
                        onFocusTitle = {},
                        onDevice = { key -> rows.firstOrNull { it.key == "dev" }?.items?.filterIsInstance<RowItem.Device>()
                            ?.firstOrNull { it.key == key }?.inputId?.let(actions::openInput) },
                        modifier = Modifier.measured(row.key, heights).section(row.key) { focusedSection = it; pageSpec.section = it }.padding(top = 8.dp),
                        onTileFocused = { lastContent = it; lastRowKey = row.key },
                        rowRequester = rowRequesters.getOrPut(row.key) { FocusRequester() },
                        previewDevices = resumed && devicesFocused,
                        onPreviewBlank = actions::previewBlank,
                        ownsFocus = { focusedSection == row.key },
                    )
                }
            }
        }

        if (castOpen) {
            // a light scrim says the menu is in charge; it sits just under the Cast button
            Box(Modifier.fillMaxSize().background(Color(0x59000000)))
            CastMenu(state.tvName, find = if (state.castDevices.isNotEmpty()) { { state.castDevices } } else actions::findCastDevices,
                onClose = { castOpen = false },
                Modifier.padding(start = ContentX + RowStartPad + 46.dp, top = 80.dp))
        }

        // a small clock once the top bar has scrolled away, over a band of the backdrop: the row scrolling out of view
        // fades away under it instead of showing through around the clock
        val scrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex >= 2 } }
        TopFade({ scrolledAway }, Modifier.padding(start = ContentX).fillMaxWidth().height(TopFadeH))
        Text(clock.format(TIME), style = Type.Clock.copy(fontSize = 17.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 40.dp).alpha(if (scrolledAway) 1f else 0f)
                .shadow(10.dp, RoundedCornerShape(8.dp)).background(Color(0xF20B0E16), RoundedCornerShape(8.dp)).border(1.dp, GlassLine, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

private val TopFadeH = 100.dp
private val TopFadeSolid = 62.dp   // just below the small clock

/**
 * The Home backdrop again across the top of the content, solid to just below the small clock and then fading out.
 * One draw (the backdrop bitmap through a fading mask, no layer), only while [shown]; without the window backdrop
 * (tests, previews) the backdrop's dark base colour.
 */
@Composable
private fun TopFade(shown: () -> Boolean, modifier: Modifier) {
    val backdrop = com.bittv.home.ui.components.LocalHomeBackdrop.current
    Box(modifier.testTag("topfade").drawWithCache {
        val x0 = ContentX.toPx(); val solid = TopFadeSolid.toPx() / size.height
        val mask = android.graphics.LinearGradient(0f, 0f, 0f, size.height, intArrayOf(android.graphics.Color.BLACK, android.graphics.Color.BLACK, 0),
            floatArrayOf(0f, solid, 1f), android.graphics.Shader.TileMode.CLAMP)
        val paint = android.graphics.Paint().apply {
            shader = if (backdrop == null) android.graphics.ComposeShader(
                android.graphics.LinearGradient(0f, 0f, 1f, 0f, 0xFF05070B.toInt(), 0xFF05070B.toInt(), android.graphics.Shader.TileMode.CLAMP), mask,
                android.graphics.PorterDuff.Mode.DST_IN)
            else android.graphics.ComposeShader(
                // the bitmap is the whole window: shifted so it lines up with what is behind the band
                android.graphics.BitmapShader(backdrop.bitmap.asAndroidBitmap(), android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
                    .apply { setLocalMatrix(android.graphics.Matrix().apply { setTranslate(-x0, 0f) }) },
                mask, android.graphics.PorterDuff.Mode.DST_IN)
        }
        onDrawBehind { if (shown()) drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) } }
    })
}

private fun Modifier.section(key: String, onFocused: (String) -> Unit) = onFocusChanged { if (it.hasFocus) onFocused(key) }
private fun Modifier.measured(key: String, heights: MutableMap<String, Int>) = onSizeChanged { heights[key] = it.height }

/**
 * The focus line: the focused row's tiles sit where the first row's tiles rest, so every press moves exactly one
 * row and holding Down glides (critically damped spring). The top bar, the banner and the first row bring the page
 * back to the top; a taller row (Devices) rises just enough to fit above the bottom safe area.
 * The top is aimed at exactly ([scrolled], [topDistance]): aimed 100 000 px past it, the spring covered the whole way
 * in its first frame and stopped on the bound — Up into the first row jumped (2 frames on the TV, against 13 for Down).
 */
@OptIn(ExperimentalFoundationApi::class)
private class PageBringIntoView(density: androidx.compose.ui.unit.Density, private val motion: com.bittv.home.ui.components.Motion) : BringIntoViewSpec {
    var section: String = "hero"
    var firstRowKey: String? = null
    /** How far the page is from its top, null when unknown. */
    var scrolled: () -> Int? = { null }
    private val line = with(density) { 370.dp.toPx() }      // where the first row's tiles rest on screen
    private val safe = with(density) { 30.dp.toPx() }
    private val caption = with(density) { 27.dp.toPx() }
    private val devTile = with(density) { 158.dp.toPx() * 1.1f }   // focused PS5 tile, scaled

    @Deprecated("Animation is still honoured by the scrollable; kept for the spring feel")
    override val scrollAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> get() = motion.pageSpring()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (section == "top" || section == "hero" || section == firstRowKey) return topDistance(scrolled(), containerSize)
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
