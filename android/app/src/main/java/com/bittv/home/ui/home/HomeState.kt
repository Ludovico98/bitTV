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
    /** The half-hour slot (see [Rotation]): the banner and the rows change with it; 0 keeps the plain order. */
    val rotation: Long = 0,
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
    /** Open this story (the one on show in the news carousel). */
    fun openNews(item: NewsItem) {}
    /** The app bar was rearranged: the new order of app ids. */
    fun reorderApps(ids: List<String>) {}
    /** Switch the TV to this HDMI input, full screen. */
    fun openInput(inputId: String) {}
    /** Read the HDMI devices again (their power) — the Devices row is coming up. */
    fun refreshDevices() {}
    /** This device's preview showed no picture: show it without one until the TV reports a change. */
    fun previewBlank(inputId: String) {}
    fun openBrowser() {}
    fun openMedia() {}
    /** Phones and computers on the network that can cast to the TV (the Cast menu searches when it opens). */
    suspend fun findCastDevices(): List<com.bittv.home.model.CastDevice> = emptyList()
}

/** One cell of a row: a title (with optional progress) or the PS5 / HDMI tiles in Devices. */
sealed interface RowItem {
    val key: String
    data class Show(val title: Title, val progress: Float? = null, val star: Boolean = false) : RowItem {
        override val key get() = title.id
    }
    /** An HDMI device tile; [inputId] is the real input (null: the demo's stand-in). */
    data class Device(override val key: String, val label: String, val live: Boolean, val inputId: String? = null, val on: Boolean = true,
                      val noPicture: Boolean = false) : RowItem
}

data class HomeRow(val key: String, val title: String, val note: String? = null, val items: List<RowItem>, val empty: String? = null)

private val FREE = Svc.entries.filter { it.free }.toSet()

/**
 * Home changes every half hour: a new [HomeState.rotation] slot reshuffles each row (seeded, so the same slot always
 * looks the same) and draws the banner and Suggested from a wider top. Watch Later and Continue Watching never move.
 */
object Rotation {
    const val SLOT_MS = 30 * 60_000L
    fun slot(epochMs: Long): Long = epochMs / SLOT_MS
    fun untilNext(epochMs: Long): Long = SLOT_MS - epochMs % SLOT_MS

    /** [list] shuffled for [slot] (the row's [salt] keeps rows from moving in step); slot 0 keeps the order. */
    fun <T> mix(list: List<T>, slot: Long, salt: Int): List<T> =
        if (slot == 0L) list else list.shuffled(kotlin.random.Random(slot * 31 + salt))
}

/** The Home rows, in order, as in the prototype's buildRows(). Rows with nothing to show are dropped. */
fun homeRows(s: HomeState): List<HomeRow> {
    val subs = s.prefs.subs
    val byId = s.titles.associateBy { it.id }
    val pool = s.titles.filter { it.isAvailable(subs) }
    val cwIds = s.progress.map { it.titleId }.toSet()
    val r = s.rotation
    fun shows(list: List<Title>, salt: Int) = Rotation.mix(list, r, salt).map { RowItem.Show(it, star = it.id in s.prefs.watchLater) }
    return listOf(
        HomeRow("wl", "Watch Later", items = s.prefs.watchLater.mapNotNull { byId[it] }.map { RowItem.Show(it) },
            empty = "Nothing saved yet — add titles from their page to keep them here"),
        HomeRow("cw", "Continue Watching", items = s.progress.mapNotNull { p ->
            byId[p.titleId]?.takeIf { it.isAvailable(subs) }?.let { RowItem.Show(it, p.fraction, star = it.id in s.prefs.watchLater) }
        }),
        // real devices when known (none → no row); the demo's stand-ins otherwise
        HomeRow("dev", "Devices", items = s.devices?.map { RowItem.Device(it.inputId, listOfNotNull(it.label, it.port).joinToString(" · "), live = true, inputId = it.inputId, on = it.on, noPicture = it.noPicture) }
            ?: listOf(RowItem.Device("ps5", "PlayStation 5 · HDMI 1", live = true), RowItem.Device("hdmi2", "HDMI 2", live = false))),
        HomeRow("sug", "Suggested for you", note = "only from your apps",
            // the best-rated 12, or (rotating) 12 drawn from the best-rated 40
            items = shows(pool.filter { it.id !in cwIds }.sortedByDescending { it.score }.let { if (r == 0L) it.take(12) else Rotation.mix(it.take(40), r, 1).take(12) }
                .sortedByDescending { it.score }, 0)),
        HomeRow("series", "Series", items = shows(pool.filter { it.type == TitleType.SERIES }, 2)),
        HomeRow("movies", "Movies", items = shows(pool.filter { it.type == TitleType.MOVIE }, 3)),
        HomeRow("comedy", "Comedy — Series & Movies", items = shows(pool.filter { "Comedy" in it.genres }, 4)),
        HomeRow("it", "Dall’Italia", note = "La7 · RaiPlay", items = shows(pool.filter { t -> t.services.any { it == Svc.LA7 || it == Svc.RAIPLAY } }, 5)),
        HomeRow("free", "Free to watch", note = "Tubi · ITVX · La7 · RaiPlay", items = shows(pool.filter { t -> t.services.any { it in FREE } }, 6)),
    ).filter { it.items.isNotEmpty() || it.empty != null }
}

/** Banner: the five best-rated titles on his apps — or, rotating, five of the best-rated 20, new each half hour. */
fun bannerTitles(s: HomeState): List<Title> = s.titles.filter { it.isAvailable(s.prefs.subs) }.sortedByDescending { it.score }
    .let { if (s.rotation == 0L) it.take(5) else Rotation.mix(it.take(20), s.rotation, 7).take(5) }
