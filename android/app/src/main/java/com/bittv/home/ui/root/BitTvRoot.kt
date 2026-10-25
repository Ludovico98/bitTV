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
import kotlinx.coroutines.async
import com.bittv.home.platform.TtsSpeaker
import androidx.compose.runtime.rememberUpdatedState
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bittv.home.data.AppOrder
import com.bittv.home.platform.AppsRepo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bittv.home.ui.components.LocalAppArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
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
fun BitTvRoot(graph: AppGraph, skipBoot: Boolean = false, homeSignal: Int = 0, demoCatalog: Boolean = false, askSignal: Int = 0,
              wakeSignal: Int = 0) {
    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(initialValue = Prefs())
    val scope = rememberCoroutineScope()
    // the app bar in his order (hold OK on an app to move it)
    val apps = remember(prefs.appOrder) { AppOrder.ordered(graph.apps, prefs.appOrder) }
    val context = LocalContext.current
    // the TV's real apps; tests (demo catalogue) keep the stand-in set and screens
    var installed by remember { mutableStateOf(if (demoCatalog) apps.map { it.id }.toSet() - "paramount" else graph.appsRepo.installed(apps)) }
    val live by graph.catalog.state.collectAsStateWithLifecycle()
    // tests pin the demo catalogue so their titles don't depend on what is popular today
    val catalog = if (demoCatalog) CatalogRepo.State(DemoCatalog.titles, DemoCatalog.progress, CatalogRepo.Source.DEMO) else live
    // the catalogue refreshes while Home is in front (CatalogRepo skips it when its copy is under 12 h old)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // his services are the installed apps' (the demo keeps its own list)
    val effective = remember(prefs, installed) { if (demoCatalog) prefs else prefs.copy(subs = AppsRepo.services(apps, installed)) }
    val nowPlaying by graph.nowPlaying.state.collectAsStateWithLifecycle()
    // Speak in Search: the real speech service; the first time, Android asks to allow the microphone
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val listen: suspend () -> String? = remember {
        suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO); null
            } else graph.speech.listen {}
        }
    }
    var appArt by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var devices by remember { mutableStateOf<List<com.bittv.home.platform.ExternalDevice>>(emptyList()) }
    // Home's picks change every half hour (Watch Later and Continue Watching stay put)
    var rotation by remember { mutableStateOf(com.bittv.home.ui.home.Rotation.slot(System.currentTimeMillis())) }
    LaunchedEffect(demoCatalog) {
        if (!demoCatalog) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                rotation = com.bittv.home.ui.home.Rotation.slot(System.currentTimeMillis())
                kotlinx.coroutines.delay(com.bittv.home.ui.home.Rotation.untilNext(System.currentTimeMillis()) + 500)
            }
        }
    }
    // the clock line's weather (Redhill), every 30 minutes while Home is in front
    var weatherNow by remember { mutableStateOf<com.bittv.home.model.WeatherNow?>(null) }
    LaunchedEffect(demoCatalog) {
        if (!demoCatalog) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { graph.answers.current()?.let { weatherNow = it }; kotlinx.coroutines.delay(30 * 60_000L) }
        }
    }
    LaunchedEffect(demoCatalog) {
        if (!demoCatalog) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            installed = graph.appsRepo.installed(apps)   // an app installed or removed while away shows at once
            // the apps' own TV banners for the app bar (SmartTube keeps the YouTube mark)
            appArt = withContext(Dispatchers.IO) {
                apps.filter { it.id != "smarttube" && it.id in installed }
                    .mapNotNull { a -> graph.appsRepo.banner(a)?.let { a.id to it.asImageBitmap() } }.toMap()
            }
            graph.nowPlaying.start()   // what's playing, live, while Home is in front
            devices = graph.tvInputs.devices()   // and what is plugged in (the PS5), kept current while Home shows
            graph.tvInputs.watch { devices = graph.tvInputs.devices() }
            try { graph.catalog.refresh(); awaitCancellation() } finally { graph.nowPlaying.stop(); graph.tvInputs.stop() }
        }
    }
    // first real catalogue: the demo's Watch Later picks become real titles (read from storage, never the default)
    LaunchedEffect(catalog.source) {
        if (catalog.source != CatalogRepo.Source.TMDB) return@LaunchedEffect
        val saved = graph.prefs.prefs.first()
        WatchLaterSeed.replaceDemo(saved.watchLater, catalog.titles, AppsRepo.services(apps, installed))?.let { graph.prefs.setWatchLater(it) }
    }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var returnToken by remember { mutableIntStateOf(0) }
    var topToken by remember { mutableIntStateOf(0) }
    var booting by rememberSaveable { mutableStateOf(!skipBoot) }
    // the Assistant: demo answers and an estimated reading time until plan 2 wires speech and live answers
    val demo = remember { DemoAnswers() }
    val ctx = LocalContext.current
    val voice by rememberUpdatedState(prefs.voice)
    // real answers on the TV: live weather/time/Wikipedia read aloud; the rest goes to Google Assistant
    val tts = remember { if (demoCatalog) null else TtsSpeaker(ctx) { voice } }
    val assistant = remember {
        AssistantController(scope, tts ?: EstimatedSpeaker, if (demoCatalog) demo else graph.answers, onHandoff = { q ->
            runCatching {
                ctx.startActivity(Intent("android.search.action.GLOBAL_SEARCH").setPackage("com.google.android.katniss")
                    .putExtra(android.app.SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
    }
    val assistantState by assistant.state.collectAsStateWithLifecycle()
    // kept here, not in Search, so the query is still there when Back returns from a result
    var searchQuery by rememberSaveable { mutableStateOf("") }
    fun askDemo() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        if (demoCatalog) { assistant.ask(demo.nextQuestion()); return }
        // the remote's mic on BitTV: listen for real; the answer appears in the active section
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        assistant.listen { onPartial -> graph.speech.listen(onPartial) }
    }
    LaunchedEffect(askSignal) { if (askSignal > 0) askDemo() }
    // the TV woke from standby onto BitTV: greet it with the boot animation, from the top of Home
    LaunchedEffect(wakeSignal) { if (wakeSignal > 0) { while (stack.size > 1) stack.removeAt(stack.lastIndex); assistant.dismiss(); booting = true } }
    // the TV's English voices for the Settings picker
    var voices by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(tts) { tts?.let { voices = it.voices() } }

    // opening a page puts any answer away: it belongs to Home and Back must close the page first
    fun push(s: Screen) { assistant.dismiss(); stack.add(s) }
    fun pop() { if (stack.size > 1) { stack.removeAt(stack.lastIndex); if (stack.size == 1) returnToken++ } }
    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = stack.size == 1) { topToken++ }
    // on Home an answer closes before Back scrolls to the top (registered later, so it wins)
    BackHandler(enabled = stack.size == 1 && assistantState != AssistantState.Idle) { assistant.dismiss() }
    BackHandler(enabled = booting) { booting = false }

    fun openApp(a: AppEntry) {
        if (demoCatalog) { if (a.id in installed) push(Screen.Opening(a, a.label, "In the real BitTV this hands straight over to ${a.label}.")) else push(Screen.NotInstalled(a)); return }
        if (!graph.appsRepo.launch(a)) { installed = graph.appsRepo.installed(apps); push(Screen.NotInstalled(a)) }
    }
    // each service's own page for a title (netflix.com/title/…), fetched when its page opens so Play is instant
    val watchLinks = remember { mutableMapOf<String, kotlinx.coroutines.Deferred<String?>>() }
    fun linkFor(t: Title, svc: Svc): kotlinx.coroutines.Deferred<String?> = watchLinks.getOrPut("${t.id}:$svc") {
        scope.async {
            com.bittv.home.platform.WatchLinks.pageUrl(t.id, svc)?.let { page ->
                com.bittv.home.net.httpGet(page)?.let { com.bittv.home.platform.WatchLinks.parse(it)[svc] }
            }
        }
    }
    fun play(t: Title, svc: Svc) {
        val app = apps.firstOrNull { it.svc == svc }
        val p = catalog.progress.firstOrNull { it.titleId == t.id }
        if (!demoCatalog && app != null) {
            if (app.id !in installed) { push(Screen.NotInstalled(app)); return }
            // the title's page in the app; else the app's search for it; else the app
            scope.launch {
                val url = kotlinx.coroutines.withTimeoutOrNull(3_000) { linkFor(t, svc).await() }
                if (!graph.appsRepo.openTitle(app, url, t.name)) push(Screen.NotInstalled(app))
            }
            return
        }
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
            // a title found by online search is kept so Watch Later can still show it after restarts
            override fun toggleWatchLater(t: Title) { scope.launch { graph.catalog.keep(t); graph.prefs.toggleWatchLater(t.id) } }
            override fun reorderApps(ids: List<String>) { scope.launch { graph.prefs.setAppOrder(ids) } }
            override fun openInput(inputId: String) {
                // straight into TCL's TV app on that input: no "open with TV / Live Channels" chooser
                val i = Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val handlers = context.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }
                com.bittv.home.platform.TvApps.inputApp(handlers)?.let { i.setPackage(it) }
                runCatching { context.startActivity(i) }
            }
            override fun openBrowser() { if (!demoCatalog) graph.appsRepo.launchFirst(com.bittv.home.platform.TvApps.BROWSER) }
            override fun openMedia() { if (!demoCatalog) graph.appsRepo.launchFirst(com.bittv.home.platform.TvApps.MEDIA) }
            override suspend fun findCastDevices() = if (demoCatalog) DemoCatalog.castDevices else graph.network.scan()
            // the card's buttons drive the session shown; with nothing to control, play opens Spotify
            override fun spotify(command: String) {
                if (demoCatalog) return
                if (!graph.nowPlaying.command(command) && command == "toggle") apps.firstOrNull { it.id == "spotify" }?.let(::openApp)
            }
        }
    }

    val motion = remember(prefs.animations) { Motion(prefs.animations) }
    val dismiss = remember { { assistant.dismiss() } }
    val overlay: (@Composable () -> Unit)? = remember(assistantState) {
        if (assistantState == AssistantState.Idle) null else { { AssistantArea(assistantState, onDismiss = dismiss) } }
    }
    CompositionLocalProvider(LocalMotion provides motion, LocalAppArt provides appArt) {
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
                        prefs = effective, titles = catalog.titles, progress = catalog.progress, apps = apps,
                        installed = installed, today = TodayInfo.items(LocalDate.now()),
                        nowPlaying = if (demoCatalog) NowPlaying("Midnight Drive", "Neon Coast", 214, 83, true) else nowPlaying,
                        news = listOf(NewsItem("Space mission returns safely after 200 days in orbit", "BBC News · 12 min ago")),
                        weather = weatherNow, devices = if (demoCatalog) null else devices, rotation = if (demoCatalog) 0 else rotation,
                    ),
                    actions, active = top == Screen.Home, returnToken = returnToken, topToken = topToken,
                    assistantOverlay = overlay,
                )
            }
            when (top) {
                Screen.Home -> {}
                Screen.Search -> if (demoCatalog) SearchScreen(catalog.titles, effective.subs, apps, onOpen = { push(Screen.Details(it)) }, onBack = ::pop,
                    query = searchQuery, onQueryChange = { searchQuery = it })
                else SearchScreen(catalog.titles, effective.subs, apps, onOpen = { push(Screen.Details(it)) }, onBack = ::pop, listen = listen,
                    online = graph.search?.let { s -> { q: String -> s.search(q) } },
                    query = searchQuery, onQueryChange = { searchQuery = it })
                is Screen.Details -> { if (!demoCatalog) LaunchedEffect(top.title.id) { top.title.services.forEach { linkFor(top.title, it) } }
                DetailsScreen(top.title, effective, catalog.progress.firstOrNull { it.titleId == top.title.id }, apps,
                    onPlay = { play(top.title, it) }, onToggleWatchLater = { actions.toggleWatchLater(top.title) }, onBack = ::pop) }
                is Screen.NotInstalled -> NotInstalledScreen(top.app, onInstall = {
                    if (demoCatalog) installed = installed + top.app.id else graph.appsRepo.openStore(top.app)
                    pop()
                }, onBack = ::pop)
                Screen.Settings -> SettingsScreen(prefs, graph.prefs, apps, voices = voices, onSample = { scope.launch { tts?.speak("Hello, this is how I will sound when I answer you.") } },
                    onTvSettings = { runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                    onReplayBoot = { pop(); booting = true }, onBack = ::pop)
                is Screen.Opening -> OpeningScreen(top.app, top.label, top.line, onBack = ::pop)
            }
            if (prefs.overscanGuides) Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp).border(2.dp, Color(0xD9FF4646)))
            // Home is composed underneath while the logo plays, so it is ready the moment the boot fades out
            AnimatedVisibility(booting, enter = EnterTransition.None, exit = fadeOut(tween(200))) { BootAnimation(onDone = { booting = false }) }
        }
    }
}
