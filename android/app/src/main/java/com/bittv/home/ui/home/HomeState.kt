package com.bittv.home.ui.home

import com.bittv.home.model.AppEntry
import com.bittv.home.model.CastDevice
import com.bittv.home.model.NewsItem
import com.bittv.home.model.NowPlaying
import com.bittv.home.model.Prefs
import com.bittv.home.model.Progress
import com.bittv.home.model.Svc
import com.bittv.home.model.Tags
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
    /** Apps switched off the app bar in Settings (they still give titles their app icon). */
    val hiddenApps: Set<String> = emptySet(),
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
    /** The rows were rearranged: the new order of row keys. */
    fun reorderRows(keys: List<String>) {}
    /** Switch the TV to this HDMI input, full screen. */
    fun openInput(inputId: String) {}
    /** What is plugged into the TV's HDMI ports, read when the Inputs menu opens (passive: nothing is woken). */
    suspend fun findInputs(): List<com.bittv.home.platform.HdmiInput> = emptyList()
    fun openBrowser() {}
    fun openMedia() {}
    /** Phones and computers on the network that can cast to the TV (the Cast menu searches when it opens). */
    suspend fun findCastDevices(): List<com.bittv.home.model.CastDevice> = emptyList()
}

/** One cell of a row: a title, with its progress in Continue Watching and a star when it is in his Watch List. */
sealed interface RowItem {
    val key: String
    data class Show(val title: Title, val progress: Float? = null, val star: Boolean = false) : RowItem {
        override val key get() = title.id
    }
}

data class HomeRow(val key: String, val title: String, val note: String? = null, val items: List<RowItem>, val empty: String? = null)

private val FREE = Svc.entries.filter { it.free }.toSet()

/**
 * Home changes every half hour: a new [HomeState.rotation] slot reshuffles each row (seeded, so the same slot always
 * looks the same) and draws the banner and the rows from a wider top. The Watch List and Continue Watching never move.
 */
object Rotation {
    const val SLOT_MS = 30 * 60_000L
    fun slot(epochMs: Long): Long = epochMs / SLOT_MS
    fun untilNext(epochMs: Long): Long = SLOT_MS - epochMs % SLOT_MS

    /** [list] shuffled for [slot] (the row's [salt] keeps rows from moving in step); slot 0 keeps the order. */
    fun <T> mix(list: List<T>, slot: Long, salt: Int): List<T> =
        if (slot == 0L) list else list.shuffled(kotlin.random.Random(slot * 31 + salt))
}

/** A row of one kind of title: TMDB genres (series and films name some differently) or keyword [Tags], or where it is from. */
private class Kind(val key: String, val title: String, val note: String? = null, val has: (Title) -> Boolean)
private fun Title.genre(vararg names: String) = genres.any { it in names }
/**
 * A genre row for live-action titles: TMDB gives most cartoons "Adventure" or "Comedy" too, and Toy Story led
 * Action & Adventure (seen with the real catalogue, 2026-10-26). Animated titles go in Animation (or Sitcom, Anthology…).
 */
private fun Title.live(vararg names: String) = genre(*names) && !genre("Animation")

private val KINDS = listOf(
    Kind("action", "Action & Adventure") { it.live("Action & Adventure", "Action", "Adventure") },
    Kind("comedy", "Comedy") { it.live("Comedy") },
    Kind("crime", "Crime & Mystery") { it.live("Crime", "Mystery") },
    Kind("drama", "Drama") { it.live("Drama") },
    Kind("scifi", "Science Fiction") { it.live("Science Fiction", "Sci-Fi & Fantasy", "Sci-Fi") },
    Kind("fantasy", "Fantasy") { it.live("Fantasy", "Sci-Fi & Fantasy") },
    Kind("horror", "Horror") { (it.genre("Horror") || Tags.HORROR in it.tags) && !it.genre("Animation") },
    Kind("romance", "Romance") { (it.genre("Romance") || Tags.ROMANCE in it.tags) && !it.genre("Animation") },
    Kind("thriller", "Thriller") { (it.genre("Thriller") || Tags.THRILLER in it.tags) && !it.genre("Animation") },
    Kind("bio", "Biographical & Historical") { it.genre("History") || Tags.BIOGRAPHY in it.tags },
    Kind("animation", "Animation") { it.genre("Animation") },
    Kind("docs", "Documentary") { it.genre("Documentary") },
    Kind("sitcom", "Sitcom") { Tags.SITCOM in it.tags },
    Kind("procedural", "Procedural / Cop Show") { Tags.PROCEDURAL in it.tags },
    Kind("anthology", "Anthology Series") { Tags.ANTHOLOGY in it.tags },
    Kind("limited", "Limited Series / Miniseries") { Tags.LIMITED in it.tags && it.type == TitleType.SERIES },
    // made in Italy, on any of his apps (RaiPlay also streams dubbed imports: Bluey led this row by service);
    // a title whose language isn't known (the demo's) by its service
    Kind("it", "Dall’Italia") { t -> if (t.lang.isNotEmpty()) t.lang == "it" else t.services.any { it == Svc.LA7 || it == Svc.RAIPLAY } },
    Kind("free", "Free to watch", "Tubi · ITVX · La7 · RaiPlay") { t -> t.services.any { it in FREE } },
)

/**
 * Who chooses first: the narrowest kinds, so a broad one (Drama, Comedy) can't take every title they could show.
 * Each title goes in one row only.
 */
private val PICK_ORDER = listOf("it", "sitcom", "procedural", "anthology", "limited", "docs", "animation", "horror", "bio",
    "romance", "thriller", "fantasy", "scifi", "free", "crime", "action", "comedy", "drama")

/** Every row's key in the default order: his two lists, then the kinds. */
val DEFAULT_ROW_ORDER: List<String> = listOf("wl", "cw") + KINDS.map { it.key }
const val ROW_MAX = 15
/** A kind with fewer titles than this (after the others chose) is left out rather than shown nearly empty. */
const val ROW_MIN = 3

/** [keys] in his saved [order] (unknown keys dropped), the rest after it in their default order. */
fun orderedKeys(keys: List<String>, order: List<String>): List<String> = order.filter { it in keys }.distinct().let { it + (keys - it.toSet()) }

/**
 * The Home rows in his order. The Watch List and Continue Watching show everything in them; every other title shows
 * at most once on Home — never in a second row, and not in a row when it is already in the banner, the Watch List or
 * Continue Watching. Rows with nothing to show are dropped (the Watch List says how to fill it instead).
 */
fun homeRows(s: HomeState): List<HomeRow> {
    val subs = s.prefs.subs
    val byId = s.titles.associateBy { it.id }
    val pool = s.titles.filter { it.isAvailable(subs) }.sortedByDescending { it.score }
    val r = s.rotation
    val wl = HomeRow("wl", "Watch List", items = s.prefs.watchLater.mapNotNull { byId[it] }.map { RowItem.Show(it) },
        empty = "Nothing saved yet — add titles from their page to keep them here")
    val cw = HomeRow("cw", "Continue Watching", items = s.progress.mapNotNull { p ->
        byId[p.titleId]?.takeIf { it.isAvailable(subs) }?.let { RowItem.Show(it, p.fraction, star = it.id in s.prefs.watchLater) }
    })
    val used = (s.prefs.watchLater + s.progress.map { it.titleId } + bannerTitles(s).map { it.id }).toMutableSet()
    val kinds = KINDS.associateBy { it.key }
    val chosen = PICK_ORDER.mapIndexed { salt, key ->
        val k = kinds.getValue(key)
        // the best-rated it may show, or (rotating) a draw from its best-rated 40, best first
        val free = pool.filter { it.id !in used && k.has(it) }
        val pick = (if (r == 0L) free.take(ROW_MAX) else Rotation.mix(free.take(40), r, salt).take(ROW_MAX)).sortedByDescending { it.score }
        if (pick.size >= ROW_MIN) used += pick.map { it.id }
        key to HomeRow(key, k.title, k.note, items = if (pick.size >= ROW_MIN) pick.map { RowItem.Show(it) } else emptyList())
    }.toMap()
    val all = mapOf("wl" to wl, "cw" to cw) + chosen
    return orderedKeys(DEFAULT_ROW_ORDER, s.prefs.rowOrder).mapNotNull { all[it] }.filter { it.items.isNotEmpty() || it.empty != null }
}

/** Banner: the five best-rated titles on his apps — or, rotating, five of the best-rated 20, new each half hour. */
fun bannerTitles(s: HomeState): List<Title> = s.titles.filter { it.isAvailable(s.prefs.subs) }.sortedByDescending { it.score }
    .let { if (s.rotation == 0L) it.take(5) else Rotation.mix(it.take(20), s.rotation, 7).take(5) }
