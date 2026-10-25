package com.bittv.home.data.tmdb

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleType
import com.bittv.home.model.isAvailable
import com.bittv.home.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Search beyond the downloaded catalogue: TMDB's own search, then where each result streams — UK services in GB,
 * La7 and RaiPlay in IT, subscription/free/with-ads only (not rentals). Up to 10 titles per query.
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

    suspend fun search(query: String): List<Title> = coroutineScope {
        val q = query.trim().ifEmpty { return@coroutineScope emptyList() }
        val body = get("/search/multi?query=${URLEncoder.encode(q, "UTF-8").replace("+", "%20")}&language=en-GB&include_adult=false")
            ?: return@coroutineScope emptyList()
        val (tvG, mvG) = genres() ?: return@coroutineScope emptyList()
        val arr = runCatching { JSONObject(body).getJSONArray("results") }.getOrNull() ?: return@coroutineScope emptyList()
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("media_type") == "tv" || it.optString("media_type") == "movie" }.take(LIMIT)
        items.map { o ->
            async {
                val tv = o.getString("media_type") == "tv"
                val services = get("/${if (tv) "tv" else "movie"}/${o.getInt("id")}/watch/providers")?.let(::services).orEmpty()
                TmdbParse.item(o, if (tv) TitleType.SERIES else TitleType.MOVIE, services, if (tv) tvG else mvG)
            }
        }.awaitAll()
    }

    companion object {
        private const val LIMIT = 10
        private val WATCH = listOf("flatrate", "free", "ads")
        private val ITALIAN = setOf(Svc.LA7, Svc.RAIPLAY)

        /** /watch/providers → his services that stream it (GB, or IT for La7/RaiPlay), in the order Svc lists them. */
        fun services(json: String): List<Svc> = runCatching {
            val r = JSONObject(json).getJSONObject("results")
            fun inRegion(region: String) = r.optJSONObject(region)?.let { reg ->
                WATCH.flatMap { k -> reg.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { TmdbParse.svcFor(a.getJSONObject(it).optString("provider_name")) } }.orEmpty() }
            }.orEmpty()
            val found = inRegion("GB").filter { it !in ITALIAN } + inRegion("IT").filter { it in ITALIAN }
            Svc.entries.filter { it in found }
        }.getOrDefault(emptyList())

        /** The instant local matches first, then online results on his apps that weren't already there. */
        fun merge(local: SearchResult, online: List<Title>, subs: Set<Svc>): SearchResult {
            val seen = local.results.map { it.id }.toMutableSet()
            val extra = online.filter { it.isAvailable(subs) && seen.add(it.id) }
            val all = local.results + extra
            return SearchResult(all, local.similar.filter { s -> all.none { it.id == s.id } })
        }
    }
}
