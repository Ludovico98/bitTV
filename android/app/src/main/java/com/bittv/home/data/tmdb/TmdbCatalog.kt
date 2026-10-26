package com.bittv.home.data.tmdb

import com.bittv.home.data.CatalogSource
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.bittv.home.model.Tags

/**
 * The real catalogue: what is popular on each of his services right now, from TMDB's watch-provider data
 * (JustWatch) — two pages of series and two of films per service — plus, per service, the kinds of show TMDB marks
 * with keywords rather than genres (sitcoms, procedurals, anthologies, miniseries, biopics; horror, romance and
 * thrillers among series), for the Home rows of the same names. UK services are read in the GB region, La7 and
 * RaiPlay in IT. [fetch] returns the body or null. About 150 requests, at most 8 at a time, every 12 hours.
 */
class TmdbCatalog(private val key: String, private val fetch: suspend (url: String, auth: String?) -> String?) : CatalogSource {
    private val gate = Semaphore(8)

    private suspend fun get(path: String): String? = gate.withPermit {
        val (url, auth) = request("$API$path", key)
        fetch(url, auth)
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
                val base = "?watch_region=$region&with_watch_providers=${providers.sorted().joinToString("%7C")}" +
                    "&with_watch_monetization_types=flatrate%7Cfree%7Cads&sort_by=popularity.desc&include_adult=false&language=en-GB"
                fun genresOf(kind: String) = if (kind == "tv") tvGenres else movieGenres
                fun typeOf(kind: String) = if (kind == "tv") TitleType.SERIES else TitleType.MOVIE
                val popular = listOf("tv", "movie").flatMap { kind -> (1..PAGES).map { page ->
                    async { get("/discover/$kind$base&page=$page")?.let { TmdbParse.discover(it, typeOf(kind), svc, genresOf(kind)) }.orEmpty() }
                } }
                val tagged = KEYWORDS.map { (tag, kind, keywords) ->
                    async {
                        get("/discover/$kind$base&with_keywords=$keywords")
                            ?.let { TmdbParse.discover(it, typeOf(kind), svc, genresOf(kind)).map { t -> t.copy(tags = listOf(tag)) } }.orEmpty()
                    }
                }
                popular + tagged
            }
        }.awaitAll()
        TmdbParse.merge(lists).takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val API = "https://api.themoviedb.org/3"
        private const val PAGES = 2
        private val REGIONS = listOf(
            "GB" to (Svc.entries.toSet() - Svc.LA7 - Svc.RAIPLAY),
            "IT" to setOf(Svc.LA7, Svc.RAIPLAY),
        )
        /** Tag, kind, TMDB keyword ids ("%7C" = or), looked up on TMDB 2026-10-26. */
        private val KEYWORDS = listOf(
            Triple(Tags.SITCOM, "tv", "193171"),
            Triple(Tags.PROCEDURAL, "tv", "268067%7C207694%7C298849%7C208788"),   // police, procedural, medical procedural, medical drama
            Triple(Tags.ANTHOLOGY, "tv", "9706"), Triple(Tags.ANTHOLOGY, "movie", "9706"),
            Triple(Tags.LIMITED, "tv", "11162"),                                      // miniseries
            Triple(Tags.BIOGRAPHY, "tv", "5565%7C9672"), Triple(Tags.BIOGRAPHY, "movie", "5565%7C9672"),   // biography, based on true story
            Triple(Tags.HORROR, "tv", "315058"), Triple(Tags.ROMANCE, "tv", "9840"), Triple(Tags.THRILLER, "tv", "316362"),
        )

        /** A v4 read access token (a JWT) goes in the Authorization header; a v3 key goes in the URL. */
        fun request(url: String, key: String): Pair<String, String?> =
            if (key.startsWith("eyJ")) url to "Bearer $key"
            else (url + (if ('?' in url) "&" else "?") + "api_key=$key") to null
    }
}
