package com.bittv.home.data.tmdb

import com.bittv.home.data.CatalogSource
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The real catalogue: what is popular on each of his services right now, from TMDB's watch-provider data
 * (JustWatch). UK services are read in the GB region, La7 and RaiPlay in IT. [fetch] returns the body or null.
 */
class TmdbCatalog(private val key: String, private val fetch: suspend (url: String, auth: String?) -> String?) : CatalogSource {

    private suspend fun get(path: String): String? {
        val (url, auth) = request("$API$path", key)
        return fetch(url, auth)
    }

    /** All titles, or null when TMDB can't be reached (the caller keeps what it had). */
    override suspend fun load(): List<Title>? = coroutineScope {
        val tvGenres = get("/genre/tv/list?language=en-GB")?.let(TmdbParse::genres) ?: return@coroutineScope null
        val movieGenres = get("/genre/movie/list?language=en-GB")?.let(TmdbParse::genres) ?: return@coroutineScope null
        val lists = REGIONS.flatMap { (region, services) ->
            val ids = mutableMapOf<Svc, Set<Int>>()
            for (kind in listOf("tv", "movie")) {
                val p = get("/watch/providers/$kind?watch_region=$region") ?: return@coroutineScope null
                TmdbParse.providerIds(p).forEach { (s, v) -> if (s in services) ids[s] = (ids[s] ?: emptySet()) + v }
            }
            ids.flatMap { (svc, providers) ->
                listOf(TitleType.SERIES to "tv", TitleType.MOVIE to "movie").map { (type, kind) ->
                    async {
                        get("/discover/$kind?watch_region=$region&with_watch_providers=${providers.sorted().joinToString("%7C")}" +
                            "&with_watch_monetization_types=flatrate%7Cfree%7Cads&sort_by=popularity.desc&include_adult=false&language=en-GB")
                            ?.let { TmdbParse.discover(it, type, svc, if (type == TitleType.SERIES) tvGenres else movieGenres) }
                            .orEmpty()
                    }
                }
            }
        }.awaitAll()
        TmdbParse.merge(lists).takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private val REGIONS = listOf(
            "GB" to (Svc.entries.toSet() - Svc.LA7 - Svc.RAIPLAY),
            "IT" to setOf(Svc.LA7, Svc.RAIPLAY),
        )

        /** A v4 read access token (a JWT) goes in the Authorization header; a v3 key goes in the URL. */
        fun request(url: String, key: String): Pair<String, String?> =
            if (key.startsWith("eyJ")) url to "Bearer $key"
            else (url + (if ('?' in url) "&" else "?") + "api_key=$key") to null
    }
}
