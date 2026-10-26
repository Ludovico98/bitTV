package com.bittv.home.data.tmdb

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleType
import com.bittv.home.model.isAvailable
import com.bittv.home.model.offer
import com.bittv.home.search.QueryMatcher
import com.bittv.home.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Search beyond the downloaded catalogue: TMDB's own search, then where each result can be watched — UK services in
 * GB, La7 and RaiPlay in IT. Every match comes back, wherever it is: on his subscriptions, on a service he doesn't
 * have, to rent or buy, or nowhere at all. Up to 16 titles per query.
 */
class TmdbSearch(private val key: String, private val fetch: suspend (url: String, auth: String?) -> String?) {
    private val genresLock = Mutex()
    private var genres: Pair<Map<Int, String>, Map<Int, String>>? = null

    private suspend fun get(path: String): String? {
        val (url, auth) = TmdbCatalog.request("https://api.themoviedb.org/3$path", key)
        return fetch(url, auth)
    }

    private suspend fun genres(): Pair<Map<Int, String>, Map<Int, String>>? = genresLock.withLock {
        genres ?: run {
            val tv = get("/genre/tv/list?language=en-GB")?.let(TmdbParse::genres) ?: return@withLock null
            val mv = get("/genre/movie/list?language=en-GB")?.let(TmdbParse::genres) ?: return@withLock null
            (tv to mv).also { genres = it }
        }
    }

    suspend fun search(query: String): List<Title> {
        val q = query.trim().ifEmpty { return emptyList() }
        val body = get("/search/multi?query=${URLEncoder.encode(q, "UTF-8").replace("+", "%20")}&language=en-GB&include_adult=false")
            ?: return emptyList()
        return titles(body, typeOf = { it.optString("media_type") })
    }

    /**
     * What TMDB recommends after [t] (viewers of it watched these), else its similar titles; empty for titles that
     * aren't from TMDB (the demo's).
     */
    suspend fun related(t: Title): List<Title> {
        val kind = when { t.id.startsWith("tv") -> "tv"; t.id.startsWith("mv") -> "movie"; else -> return emptyList() }
        val id = t.id.drop(2).toIntOrNull() ?: return emptyList()
        for (list in listOf("recommendations", "similar")) {
            val found = get("/$kind/$id/$list?language=en-GB")?.let { titles(it, typeOf = { kind }) }.orEmpty()
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /** A results page → titles with where each can be watched (one providers request per title, in parallel). */
    private suspend fun titles(body: String, typeOf: (JSONObject) -> String): List<Title> = coroutineScope {
        val (tvG, mvG) = genres() ?: return@coroutineScope emptyList()
        val arr = runCatching { JSONObject(body).getJSONArray("results") }.getOrNull() ?: return@coroutineScope emptyList()
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { typeOf(it) == "tv" || typeOf(it) == "movie" }.take(LIMIT)
        items.map { o ->
            async {
                val tv = typeOf(o) == "tv"
                val w = get("/${if (tv) "tv" else "movie"}/${o.getInt("id")}/watch/providers")?.let(::where) ?: Where()
                TmdbParse.item(o, if (tv) TitleType.SERIES else TitleType.MOVIE, w.services, if (tv) tvG else mvG)
                    .copy(buy = w.buy, elsewhere = w.elsewhere)
            }
        }.awaitAll()
    }

    /** Where a title can be watched: [services] stream it, [buy] are his apps that rent or sell it, [elsewhere] the rest by name. */
    data class Where(val services: List<Svc> = emptyList(), val buy: List<Svc> = emptyList(), val elsewhere: List<String> = emptyList())

    companion object {
        private const val LIMIT = 16
        private val STREAM = listOf("flatrate", "free", "ads")
        private val SHOP = listOf("rent", "buy")
        private val ITALIAN = setOf(Svc.LA7, Svc.RAIPLAY)
        /** The stores inside his apps: Amazon's sells through Prime Video, Apple's through the Apple TV app. */
        private fun storeSvc(name: String): Svc? = name.lowercase().filter { it.isLetterOrDigit() }.let { n ->
            when { n.startsWith("amazon") -> Svc.PRIME; n.startsWith("appletv") -> Svc.APPLETV; else -> null }
        }
        /** "Crunchyroll Amazon Channel" is Crunchyroll bought through Amazon: one name for the tile. */
        private fun brand(name: String) = name.replace(Regex("\\s+(Amazon|Apple TV|Roku Premium) Channel$"), "").trim()

        /** /watch/providers → his services that stream it (GB, or IT for La7/RaiPlay), in the order Svc lists them. */
        fun services(json: String): List<Svc> = where(json).services

        /** /watch/providers → where it streams, which of his apps rent or sell it, and everywhere else (GB only). */
        fun where(json: String): Where = runCatching {
            val r = JSONObject(json).getJSONObject("results")
            fun names(region: String, kinds: List<String>) = r.optJSONObject(region)?.let { reg ->
                kinds.flatMap { k -> reg.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("provider_name") } }.orEmpty() }
            }.orEmpty()
            val gbStream = names("GB", STREAM)
            val found = gbStream.mapNotNull(TmdbParse::svcFor).filter { it !in ITALIAN } +
                names("IT", STREAM).mapNotNull(TmdbParse::svcFor).filter { it in ITALIAN }
            val gbShop = names("GB", SHOP)
            val buy = gbShop.mapNotNull(::storeSvc)
            val other = (gbStream.filter { TmdbParse.svcFor(it) == null } + gbShop.filter { storeSvc(it) == null }).map(::brand)
            Where(Svc.entries.filter { it in found }, Svc.entries.filter { it in buy }, other.distinct())
        }.getOrDefault(Where())

        /**
         * The instant local matches first, then online ones that weren't already there, each in its column: on his
         * apps, or not (those with somewhere to get them before those with nowhere). Titles named like the whole
         * [query] lead either way, so a local near miss ("Titanic" for "attack on titan") can't push the real match
         * down. Similar Content is worked out again from the merged matches, so a title only the online search found
         * still gets some.
         */
        fun merge(local: SearchResult, online: List<Title>, subs: Set<Svc>, pool: List<Title>, query: String = ""): SearchResult {
            val q = query.trim().lowercase()
            fun named(t: Title) = q.isNotEmpty() && q in t.name.lowercase()
            val seen = (local.included + local.purchase).map { it.id }.toMutableSet()
            val extra = online.filter { seen.add(it.id) }
            val included = (local.included + extra.filter { it.isAvailable(subs) }).sortedBy { !named(it) }
            val purchase = (local.purchase + extra.filter { !it.isAvailable(subs) })
                .sortedWith(compareBy<Title> { it.offer(subs) == null }.thenBy { !named(it) })
            return SearchResult(included, purchase, QueryMatcher.similar(included + purchase, pool, subs))
        }
    }
}
