package com.bittv.home.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import com.bittv.home.ui.assistant.AssistantArea
import com.bittv.home.assistant.EstimatedSpeaker
import com.bittv.home.assistant.DemoAnswers
import com.bittv.home.assistant.AssistantState
import com.bittv.home.assistant.AssistantController
import com.bittv.home.ui.boot.BootAnimation
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bittv.home.AppGraph
import com.bittv.home.data.CatalogRepo
import com.bittv.home.data.DemoCatalog
import com.bittv.home.data.WatchLaterSeed
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bittv.home.model.AppEntry
import com.bittv.home.model.NewsItem
import com.bittv.home.model.NowPlaying
import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.today.TodayInfo
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.components.Motion
import com.bittv.home.ui.details.DetailsScreen
import com.bittv.home.ui.details.NotInstalledScreen
import com.bittv.home.ui.details.OpeningScreen
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import com.bittv.home.ui.search.SearchScreen
import com.bittv.home.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Details(val title: Title) : Screen
    data class NotInstalled(val app: AppEntry) : Screen
    data object Settings : Screen
    data class Opening(val app: AppEntry?, val label: String, val line: String) : Screen
}

/** The screen stack. Home stays composed underneath so it keeps its place; Back pops, Back on Home goes to the top. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BitTvRoot(graph: AppGraph, skipBoot: Boolean = false, homeSignal: Int = 0, demoCatalog: Boolean = false) {
    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(initialValue = Prefs())
    val scope = rememberCoroutineScope()
    val apps = graph.apps
    val live by graph.catalog.state.collectAsStateWithLifecycle()
    // tests pin the demo catalogue so their titles don't depend on what is popular today
    val catalog = if (demoCatalog) CatalogRepo.State(DemoCatalog.titles, DemoCatalog.progress, CatalogRepo.Source.DEMO) else live
    // the catalogue refreshes while Home is in front (CatalogRepo skips it when its copy is under 12 h old)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(demoCatalog) { if (!demoCatalog) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { graph.catalog.refresh() } }
    // first real catalogue: the demo's Watch Later picks become real titles (read from storage, never the default)
    LaunchedEffect(catalog.source) {
        if (catalog.source != CatalogRepo.Source.TMDB) return@LaunchedEffect
        val saved = graph.prefs.prefs.first()
        WatchLaterSeed.replaceDemo(saved.watchLater, catalog.titles, saved.subs)?.let { graph.prefs.setWatchLater(it) }
    }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var returnToken by remember { mutableIntStateOf(0) }
    var topToken by remember { mutableIntStateOf(0) }
    var installed by remember { mutableStateOf(apps.map { it.id }.toSet() - "paramount") }   // plan 2: PackageManager
    var booting by rememberSaveable { mutableStateOf(!skipBoot) }
    // the Assistant: demo answers and an estimated reading time until plan 2 wires speech and live answers
    val demo = remember { DemoAnswers() }
    val assistant = remember { AssistantController(scope, EstimatedSpeaker, demo) }
    val assistantState by assistant.state.collectAsStateWithLifecycle()
    // kept here, not in Search, so the query is still there when Back returns from a result
    var searchQuery by rememberSaveable { mutableStateOf("") }
    fun askDemo() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        assistant.ask(demo.nextQuestion())
    }

    // opening a page puts any answer away: it belongs to Home and Back must close the page first
    fun push(s: Screen) { assistant.dismiss(); stack.add(s) }
    fun pop() { if (stack.size > 1) { stack.removeAt(stack.lastIndex); if (stack.size == 1) returnToken++ } }
    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = stack.size == 1) { topToken++ }
    // on Home an answer closes before Back scrolls to the top (registered later, so it wins)
    BackHandler(enabled = stack.size == 1 && assistantState != AssistantState.Idle) { assistant.dismiss() }
    BackHandler(enabled = booting) { booting = false }

    fun openApp(a: AppEntry) =
        if (a.id in installed) push(Screen.Opening(a, a.label, "In the real BitTV this hands straight over to ${a.label}."))
        else push(Screen.NotInstalled(a))
    fun play(t: Title, svc: Svc) {
        val app = apps.firstOrNull { it.svc == svc }
        val p = catalog.progress.firstOrNull { it.titleId == t.id }
        if (app != null && app.id !in installed) push(Screen.NotInstalled(app))
        else push(Screen.Opening(app, svc.label, "Playing “${t.name}”" + (p?.let { " · ${it.label}" } ?: "")))
    }

    // the Home button while BitTV is already in front: back to the top of Home, like a fresh start
    LaunchedEffect(homeSignal) {
        if (homeSignal > 0) { stack.removeRange(1, stack.size); assistant.dismiss(); booting = false; topToken++ }
    }

    val actions = remember {
        object : HomeActions {
            override fun openTitle(t: Title) = push(Screen.Details(t))
            override fun openApp(a: AppEntry) = openApp(a)
            override fun openSearch() { searchQuery = ""; push(Screen.Search) }
            override fun openSettings() = push(Screen.Settings)
            override fun toggleWatchLater(t: Title) { scope.launch { graph.prefs.toggleWatchLater(t.id) } }
        }
    }

    val motion = remember(prefs.animations) { Motion(prefs.animations) }
    val dismiss = remember { { assistant.dismiss() } }
    val overlay: (@Composable () -> Unit)? = remember(assistantState) {
        if (assistantState == AssistantState.Idle) null else { { AssistantArea(assistantState, onDismiss = dismiss) } }
    }
    CompositionLocalProvider(LocalMotion provides motion) {
        // while the boot animation plays, any key skips it
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            when {
                booting && e.type == KeyEventType.KeyDown -> { booting = false; true }
                // the remote's mic/search key (M on a keyboard) asks the Assistant; plan 2 listens for real
                // a held key repeats: only the first press asks, the repeats are swallowed
                e.type == KeyEventType.KeyDown && (e.key == Key.M || e.key == Key.Search) && stack.last() != Screen.Search -> {
                    if (e.nativeKeyEvent.repeatCount == 0) askDemo(); true
                }
                else -> false
            }
        }) {
            val top = stack.last()
            // while another screen is on top, focus can't wander into Home behind it
            Box(if (top != Screen.Home) Modifier.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup() else Modifier) {
                HomeScreen(
                    HomeState(
                        prefs = prefs, titles = catalog.titles, progress = catalog.progress, apps = apps,
                        installed = installed, today = TodayInfo.items(LocalDate.now()),
                        nowPlaying = NowPlaying("Midnight Drive", "Neon Coast", 214, 83, true),
                        news = listOf(NewsItem("Space mission returns safely after 200 days in orbit", "BBC News · 12 min ago")),
                        weather = null, castDevices = DemoCatalog.castDevices,
                    ),
                    actions, active = top == Screen.Home, returnToken = returnToken, topToken = topToken,
                    assistantOverlay = overlay,
                )
            }
            when (top) {
                Screen.Home -> {}
                Screen.Search -> SearchScreen(catalog.titles, prefs.subs, apps, onOpen = { push(Screen.Details(it)) }, onBack = ::pop,
                    query = searchQuery, onQueryChange = { searchQuery = it })
                is Screen.Details -> DetailsScreen(top.title, prefs, catalog.progress.firstOrNull { it.titleId == top.title.id }, apps,
                    onPlay = { play(top.title, it) }, onToggleWatchLater = { actions.toggleWatchLater(top.title) }, onBack = ::pop)
                is Screen.NotInstalled -> NotInstalledScreen(top.app, onInstall = { installed = installed + top.app.id; pop() }, onBack = ::pop)
                Screen.Settings -> SettingsScreen(prefs, graph.prefs, apps, voices = emptyList(), onSample = {},
                    onReplayBoot = { pop(); booting = true }, onBack = ::pop)
                is Screen.Opening -> OpeningScreen(top.app, top.label, top.line, onBack = ::pop)
            }
            if (prefs.overscanGuides) Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp).border(2.dp, Color(0xD9FF4646)))
            // Home is composed underneath while the logo plays, so it is ready the moment the boot fades out
            AnimatedVisibility(booting, enter = EnterTransition.None, exit = fadeOut(tween(200))) { BootAnimation(onDone = { booting = false }) }
        }
    }
}
