package com.bittv.home.ui.root

import androidx.activity.compose.BackHandler
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
fun BitTvRoot(graph: AppGraph) {
    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(initialValue = Prefs())
    val scope = rememberCoroutineScope()
    val apps = graph.catalog.apps
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var returnToken by remember { mutableIntStateOf(0) }
    var topToken by remember { mutableIntStateOf(0) }
    var installed by remember { mutableStateOf(apps.map { it.id }.toSet() - "paramount") }   // plan 2: PackageManager

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) { stack.removeAt(stack.lastIndex); if (stack.size == 1) returnToken++ } }
    BackHandler(enabled = stack.size > 1) { pop() }
    BackHandler(enabled = stack.size == 1) { topToken++ }

    fun openApp(a: AppEntry) =
        if (a.id in installed) push(Screen.Opening(a, a.label, "In the real BitTV this hands straight over to ${a.label}."))
        else push(Screen.NotInstalled(a))
    fun play(t: Title, svc: Svc) {
        val app = apps.firstOrNull { it.svc == svc }
        val p = graph.catalog.progress.firstOrNull { it.titleId == t.id }
        if (app != null && app.id !in installed) push(Screen.NotInstalled(app))
        else push(Screen.Opening(app, svc.label, "Playing “${t.name}”" + (p?.let { " · ${it.label}" } ?: "")))
    }

    val actions = remember {
        object : HomeActions {
            override fun openTitle(t: Title) = push(Screen.Details(t))
            override fun openApp(a: AppEntry) = openApp(a)
            override fun openSearch() = push(Screen.Search)
            override fun openSettings() = push(Screen.Settings)
            override fun toggleWatchLater(t: Title) { scope.launch { graph.prefs.toggleWatchLater(t.id) } }
        }
    }

    CompositionLocalProvider(LocalMotion provides Motion(prefs.animations)) {
        Box(Modifier.fillMaxSize()) {
            val top = stack.last()
            // while another screen is on top, focus can't wander into Home behind it
            Box(if (top != Screen.Home) Modifier.focusProperties { onEnter = { cancelFocusChange() } }.focusGroup() else Modifier) {
                HomeScreen(
                    HomeState(
                        prefs = prefs, titles = graph.catalog.titles, progress = graph.catalog.progress, apps = apps,
                        installed = installed, today = TodayInfo.items(LocalDate.now()),
                        nowPlaying = NowPlaying("Midnight Drive", "Neon Coast", 214, 83, true),
                        news = listOf(NewsItem("Space mission returns safely after 200 days in orbit", "BBC News · 12 min ago")),
                        weather = null,
                    ),
                    actions, active = top == Screen.Home, returnToken = returnToken, topToken = topToken,
                )
            }
            when (top) {
                Screen.Home -> {}
                Screen.Search -> SearchScreen(graph.catalog.titles, prefs.subs, apps, onOpen = { push(Screen.Details(it)) }, onBack = ::pop)
                is Screen.Details -> DetailsScreen(top.title, prefs, graph.catalog.progress.firstOrNull { it.titleId == top.title.id }, apps,
                    onPlay = { play(top.title, it) }, onToggleWatchLater = { actions.toggleWatchLater(top.title) }, onBack = ::pop)
                is Screen.NotInstalled -> NotInstalledScreen(top.app, onInstall = { installed = installed + top.app.id; pop() }, onBack = ::pop)
                Screen.Settings -> SettingsScreen(prefs, graph.prefs, apps, voices = emptyList(), onSample = {}, onReplayBoot = {}, onBack = ::pop)
                is Screen.Opening -> OpeningScreen(top.app, top.label, top.line, onBack = ::pop)
            }
            if (prefs.overscanGuides) Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp).border(2.dp, Color(0xD9FF4646)))
        }
    }
}
