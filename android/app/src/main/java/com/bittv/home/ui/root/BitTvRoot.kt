package com.bittv.home.ui.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bittv.home.AppGraph
import com.bittv.home.model.AppEntry
import com.bittv.home.model.NewsItem
import com.bittv.home.model.NowPlaying
import com.bittv.home.model.Prefs
import com.bittv.home.model.Title
import com.bittv.home.today.TodayInfo
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.components.Motion
import com.bittv.home.ui.home.HomeActions
import com.bittv.home.ui.home.HomeScreen
import com.bittv.home.ui.home.HomeState
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The screen stack. Task 6: Home only; Search, Details, Settings and Boot join in Tasks 7–8. */
@Composable
fun BitTvRoot(graph: AppGraph) {
    val prefs by graph.prefs.prefs.collectAsStateWithLifecycle(initialValue = Prefs())
    val scope = rememberCoroutineScope()
    val actions = remember {
        object : HomeActions {
            override fun openTitle(t: Title) {}
            override fun openApp(a: AppEntry) {}
            override fun openSearch() {}
            override fun openSettings() {}
            override fun toggleWatchLater(t: Title) { scope.launch { graph.prefs.toggleWatchLater(t.id) } }
        }
    }
    CompositionLocalProvider(LocalMotion provides Motion(prefs.animations)) {
        HomeScreen(
            HomeState(
                prefs = prefs, titles = graph.catalog.titles, progress = graph.catalog.progress, apps = graph.catalog.apps,
                installed = graph.catalog.apps.map { it.id }.toSet() - "paramount",
                today = TodayInfo.items(LocalDate.now()),
                nowPlaying = NowPlaying("Midnight Drive", "Neon Coast", 214, 83, true),
                news = listOf(NewsItem("Space mission returns safely after 200 days in orbit", "BBC News · 12 min ago")),
                weather = null,
            ),
            actions,
        )
    }
}
