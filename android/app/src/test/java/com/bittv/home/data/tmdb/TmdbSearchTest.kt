package com.bittv.home.data.tmdb

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import com.bittv.home.search.SearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbSearchTest {
    private val multi = """{"results":[
        {"id":66732,"media_type":"tv","name":"Stranger Things","overview":"Kids in Hawkins.","first_air_date":"2016-07-15","genre_ids":[18,10765],"vote_average":8.6,"vote_count":19000,"backdrop_path":"/st.jpg"},
        {"id":99,"media_type":"person","name":"Someone"},
        {"id":1234,"media_type":"movie","title":"Strange World","overview":"A film.","release_date":"2022-11-23","genre_ids":[16],"vote_average":6.2,"vote_count":900,"backdrop_path":"/sw.jpg"}]}"""
    private val tvGenres = """{"genres":[{"id":18,"name":"Drama"},{"id":10765,"name":"Sci-Fi & Fantasy"}]}"""
    private val movieGenres = """{"genres":[{"id":16,"name":"Animation"}]}"""
    private val stProviders = """{"results":{"GB":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]},"IT":{"flatrate":[{"provider_id":8,"provider_name":"Netflix"}]}}}"""
    private val swProviders = """{"results":{"GB":{"flatrate":[{"provider_id":337,"provider_name":"Disney Plus"}],"rent":[{"provider_id":2,"provider_name":"Apple TV Store"}]},
        "IT":{"free":[{"provider_id":222,"provider_name":"Rai Play"}]}}}"""

    private fun fetcher(urls: MutableList<String> = mutableListOf()): suspend (String, String?) -> String? = { url, _ ->
        urls += url
        when {
            "/search/multi" in url -> multi
            "/genre/tv/" in url -> tvGenres
            "/genre/movie/" in url -> movieGenres
            "/tv/66732/watch/providers" in url -> stProviders
            "/movie/1234/watch/providers" in url -> swProviders
            else -> null
        }
    }

    @Test fun findsAnyShowAndTheServicesItIsOn() = runTest {
        val urls = mutableListOf<String>()
        val found = TmdbSearch("k", fetcher(urls)).search("stranger things")
        assertEquals(listOf("tv66732", "mv1234"), found.map { it.id })   // people are left out
        val st = found.first()
        assertEquals("Stranger Things", st.name); assertEquals(listOf(Svc.NETFLIX), st.services)
        assertEquals(listOf("Drama", "Sci-Fi & Fantasy"), st.genres); assertEquals("/st.jpg", st.art)
        assertTrue(urls.first { "/search/multi" in it }.contains("query=stranger%20things"))
    }

    @Test fun rentalsAreKeptApartAndItalianServicesComeFromItaly() = runTest {
        val sw = TmdbSearch("k", fetcher()).search("strange").first { it.id == "mv1234" }
        assertEquals(listOf(Svc.DISNEY, Svc.RAIPLAY), sw.services)   // streaming only
        assertEquals(listOf(Svc.APPLETV), sw.buy)                     // the Apple TV store rents it, in the Apple TV app
    }

    @Test fun aShowOnlyOnAnotherServiceIsStillFound() {
        // Attack on Titan, the anime: Crunchyroll (also as an Amazon channel), and stores
        val w = TmdbSearch.where("""{"results":{"GB":{"flatrate":[{"provider_name":"Crunchyroll"},{"provider_name":"Crunchyroll Amazon Channel"}],
            "buy":[{"provider_name":"Apple TV Store"},{"provider_name":"Amazon Video"},{"provider_name":"Sky Store"}]}}}""")
        assertEquals(emptyList<Svc>(), w.services)
        assertEquals(listOf(Svc.PRIME, Svc.APPLETV), w.buy)
        assertEquals(listOf("Crunchyroll", "Sky Store"), w.elsewhere)
    }

    @Test fun relatedAreTheRecommendationsElseTheSimilarTitles() = runTest {
        val urls = mutableListOf<String>()
        val base = fetcher(urls)
        val related = TmdbSearch("k") { url, auth ->
            when {
                "/tv/95557/recommendations" in url -> """{"results":[]}"""
                "/tv/95557/similar" in url -> multi.replace("\"media_type\":\"movie\",", "")
                else -> base(url, auth)
            }
        }.related(t("tv95557", Svc.PRIME))
        assertEquals(listOf("tv66732", "tv99", "tv1234"), related.map { it.id })
        assertEquals(emptyList<Title>(), TmdbSearch("k", base).related(t("longtide", Svc.NETFLIX)))   // a demo title
    }

    @Test fun onlineOnlyMatchStillGetsSimilarContent() {
        // Invincible came from the online search alone: the pool's titles that share its genres fill Similar Content
        val inv = t("tv95557", Svc.PRIME, genres = listOf("Animation", "Sci-Fi & Fantasy"))
        val pool = listOf(t("tv1", Svc.NETFLIX, listOf("Animation")), t("tv2", Svc.NETFLIX, listOf("Comedy")), t("tv3", Svc.PARAMOUNT, listOf("Animation")))
        val merged = TmdbSearch.merge(SearchResult(emptyList(), emptyList(), emptyList()), listOf(inv), setOf(Svc.NETFLIX, Svc.PRIME), pool)
        assertEquals(listOf("tv95557"), merged.included.map { it.id })
        assertEquals(listOf("tv1"), merged.similar.map { it.id })
    }

    @Test fun offlineFindsNothing() = runTest {
        assertEquals(emptyList<Title>(), TmdbSearch("k") { _, _ -> null }.search("anything"))
    }

    private fun t(id: String, svc: Svc, genres: List<String> = emptyList()) =
        Title(id, id, TitleType.SERIES, genres, listOf(svc), 0, 0, 2020, 5.0, TitleFont.INTER, "")

    @Test fun onlineResultsJoinTheLocalOnesWithoutRepeatsInTheirColumn() {
        val local = SearchResult(listOf(t("tv1", Svc.NETFLIX)), emptyList(), listOf(t("tv9", Svc.NETFLIX)))
        val nowhere = t("tv4", Svc.NETFLIX).copy(services = emptyList())
        val merged = TmdbSearch.merge(local, listOf(t("tv1", Svc.NETFLIX), nowhere, t("tv2", Svc.NETFLIX), t("tv3", Svc.PARAMOUNT), t("tv9", Svc.NETFLIX)),
            subs = setOf(Svc.NETFLIX), pool = emptyList())
        assertEquals(listOf("tv1", "tv2", "tv9"), merged.included.map { it.id })
        assertEquals(listOf("tv3", "tv4"), merged.purchase.map { it.id })   // somewhere to get it before nowhere
        assertEquals(emptyList<String>(), merged.similar.map { it.id })
    }

    @Test fun titlesNamedLikeTheQueryLeadOverLocalNearMisses() {
        fun named(id: String, name: String, svc: Svc) = t(id, svc).copy(name = name)
        val local = SearchResult(emptyList(), listOf(named("mv1", "Titanic", Svc.DISNEY)), emptyList())
        val merged = TmdbSearch.merge(local, listOf(named("tv1429", "Attack on Titan", Svc.PARAMOUNT)), setOf(Svc.NETFLIX), emptyList(), "attack on titan")
        assertEquals(listOf("tv1429", "mv1"), merged.purchase.map { it.id })
    }
}
