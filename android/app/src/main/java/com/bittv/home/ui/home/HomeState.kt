package com.bittv.home.ui.home

import com.bittv.home.model.AppEntry
import com.bittv.home.model.CastDevice
import com.bittv.home.model.NewsItem
import com.bittv.home.model.NowPlaying
import com.bittv.home.model.Prefs
import com.bittv.home.model.Progress
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleType
import com.bittv.home.model.WeatherNow
import com.bittv.home.model.isAvailable
import com.bittv.home.today.TodayItem

data class HomeState(
    val prefs: Prefs,
    val titles: List<Title>,
    val progress: List<Progress>,
    val apps: List<AppEntry>,
    /** Ids of rail apps that are installed; the others show "Get on Play Store". */
    val installed: Set<String>,
    val today: List<TodayItem>,
    val nowPlaying: NowPlaying?,
    val news: List<NewsItem>,
    val weather: WeatherNow?,
    /** Devices that can cast to this TV, shown by the Cast button. */
    val castDevices: List<CastDevice> = emptyList(),
    /** The name phones see for this TV. */
    val tvName: String = "Living Room TV",
    /** Devices plugged into the TV; null on the demo (stand-in tiles), empty when nothing is connected. */
    val devices: List<com.bittv.home.platform.ExternalDevice>? = null,
)

interface HomeActions {
    fun openTitle(t: Title)
    fun openApp(a: AppEntry)
    fun openSearch()
    fun openSettings()
    fun toggleWatchLater(t: Title)
    fun cast() {}
    fun spotify(command: String) {}
    fun openNews() {}
    /** The app bar was rearranged: the new order of app ids. */
    fun reorderApps(ids: List<String>) {}
    /** Switch the TV to this HDMI input, full screen. */
    fun openInput(inputId: String) {}
}

/** One cell of a row: a title (with optional progress) or the PS5 / HDMI tiles in Devices. */
sealed interface RowItem {
    val key: String
    data class Show(val title: Title, val progress: Float? = null, val caption: String? = null, val star: Boolean = false) : RowItem {
        override val key get() = title.id
    }
    /** An HDMI device tile; [inputId] is the real input (null: the demo's stand-in). */
    data class Device(override val key: String, val label: String, val live: Boolean, val inputId: String? = null) : RowItem
}

data class HomeRow(val key: String, val title: String, val note: String? = null, val items: List<RowItem>, val empty: String? = null)

private val FREE = Svc.entries.filter { it.free }.toSet()

/** The Home rows, in order, as in the prototype's buildRows(). Rows with nothing to show are dropped. */
fun homeRows(s: HomeState): List<HomeRow> {
    val subs = s.prefs.subs
    val byId = s.titles.associateBy { it.id }
    val pool = s.titles.filter { it.isAvailable(subs) }
    val cwIds = s.progress.map { it.titleId }.toSet()
    fun shows(list: List<Title>) = list.map { RowItem.Show(it, star = it.id in s.prefs.watchLater) }
    return listOf(
        HomeRow("wl", "Watch Later", items = s.prefs.watchLater.mapNotNull { byId[it] }.map { RowItem.Show(it) },
            empty = "Nothing saved yet — add titles from their page to keep them here"),
        HomeRow("cw", "Continue Watching", items = s.progress.mapNotNull { p ->
            byId[p.titleId]?.takeIf { it.isAvailable(subs) }?.let { RowItem.Show(it, p.fraction, p.label, it.id in s.prefs.watchLater) }
        }),
        // real devices when known (none → no row); the demo's stand-ins otherwise
        HomeRow("dev", "Devices", items = s.devices?.map { RowItem.Device(it.inputId, listOfNotNull(it.label, it.port).joinToString(" · "), live = true, inputId = it.inputId) }
            ?: listOf(RowItem.Device("ps5", "PlayStation 5 · HDMI 1", live = true), RowItem.Device("hdmi2", "HDMI 2", live = false))),
        HomeRow("sug", "Suggested for you", note = "only from your apps",
            items = shows(pool.filter { it.id !in cwIds }.sortedByDescending { it.score }.take(12))),
        HomeRow("series", "Series", items = shows(pool.filter { it.type == TitleType.SERIES })),
        HomeRow("movies", "Movies", items = shows(pool.filter { it.type == TitleType.MOVIE })),
        HomeRow("comedy", "Comedy — Series & Movies", items = shows(pool.filter { "Comedy" in it.genres })),
        HomeRow("it", "Dall’Italia", note = "La7 · RaiPlay", items = shows(pool.filter { t -> t.services.any { it == Svc.LA7 || it == Svc.RAIPLAY } })),
        HomeRow("free", "Free to watch", note = "Tubi · ITVX · La7 · RaiPlay", items = shows(pool.filter { t -> t.services.any { it in FREE } })),
    ).filter { it.items.isNotEmpty() || it.empty != null }
}

/** Banner: the five best-rated titles on his apps. */
fun bannerTitles(s: HomeState): List<Title> = s.titles.filter { it.isAvailable(s.prefs.subs) }.sortedByDescending { it.score }.take(5)
