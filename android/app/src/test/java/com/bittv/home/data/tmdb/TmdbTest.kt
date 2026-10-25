package com.bittv.home.data.tmdb

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbParseTest {
    private val providers = """{"results":[
        {"provider_id":8,"provider_name":"Netflix"},
        {"provider_id":1796,"provider_name":"Netflix basic with Ads"},
        {"provider_id":9,"provider_name":"Amazon Prime Video"},
        {"provider_id":39,"provider_name":"Now TV"},
        {"provider_id":350,"provider_name":"Apple TV Plus"},
        {"provider_id":2,"provider_name":"Apple TV"},
        {"provider_id":337,"provider_name":"Disney Plus"},
        {"provider_id":531,"provider_name":"Paramount Plus"},
        {"provider_id":73,"provider_name":"Tubi TV"},
        {"provider_id":41,"provider_name":"ITVX"},
        {"provider_id":3,"provider_name":"Google Play Movies"}]}"""

    @Test fun mapsProvidersToServices() {
        val ids = TmdbParse.providerIds(providers)
        assertEquals(setOf(8, 1796), ids[Svc.NETFLIX])
        assertEquals(setOf(9), ids[Svc.PRIME])
        assertEquals(setOf(39), ids[Svc.NOW])
        assertEquals(setOf(350, 2), ids[Svc.APPLETV])
        assertEquals(setOf(337), ids[Svc.DISNEY])
        assertEquals(setOf(531), ids[Svc.PARAMOUNT])
        assertEquals(setOf(73), ids[Svc.TUBI])
        assertEquals(setOf(41), ids[Svc.ITVX])
        assertNull(ids[Svc.LA7])
    }

    @Test fun italianProviderNames() {
        assertEquals(Svc.RAIPLAY, TmdbParse.svcFor("Rai Play"))
        assertEquals(Svc.LA7, TmdbParse.svcFor("La7"))
        assertEquals(Svc.NOW, TmdbParse.svcFor("NOW"))
        assertNull(TmdbParse.svcFor("Google Play Movies"))
    }

    private val genres = """{"genres":[{"id":18,"name":"Drama"},{"id":35,"name":"Comedy"},{"id":99,"name":"Documentary"}]}"""
    private val discoverTv = """{"page":1,"results":[
        {"id":1399,"name":"Stormlands","original_name":"Stormlands","overview":"Kings and storms.","first_air_date":"2021-04-17",
         "genre_ids":[18,35],"vote_average":8.4,"vote_count":2400,"backdrop_path":"/storm.jpg","poster_path":"/p.jpg"},
        {"id":77,"name":"","original_name":"Il Commissario","overview":"","first_air_date":"",
         "genre_ids":[99,12345],"vote_average":9.5,"vote_count":3,"backdrop_path":null}]}"""

    @Test fun parsesDiscoverIntoTitles() {
        val g = TmdbParse.genres(genres)
        val list = TmdbParse.discover(discoverTv, TitleType.SERIES, Svc.NETFLIX, g)
        assertEquals(2, list.size)
        val a = list[0]
        assertEquals("tv1399", a.id); assertEquals("Stormlands", a.name); assertEquals(TitleType.SERIES, a.type)
        assertEquals(listOf("Drama", "Comedy"), a.genres); assertEquals(listOf(Svc.NETFLIX), a.services)
        assertEquals(2021, a.year); assertEquals("/storm.jpg", a.art); assertEquals("Kings and storms.", a.blurb)
        val b = list[1]
        assertEquals("Il Commissario", b.name)          // empty localised name → original name
        assertEquals(listOf("Documentary"), b.genres)   // unknown genre ids dropped
        assertNull(b.art); assertEquals(0, b.year)
        assertTrue("3 votes must not outrank 2400", b.score < a.score)
    }

    @Test fun titleOnTwoServicesIsMergedOnce() {
        val g = TmdbParse.genres(genres)
        val n = TmdbParse.discover(discoverTv, TitleType.SERIES, Svc.NETFLIX, g)
        val p = TmdbParse.discover(discoverTv, TitleType.SERIES, Svc.PRIME, g)
        val merged = TmdbParse.merge(listOf(n, p))
        assertEquals(2, merged.size)
        assertEquals(listOf(Svc.NETFLIX, Svc.PRIME), merged.first { it.id == "tv1399" }.services)
    }

    @Test fun cacheRoundTrip() {
        val t = Title("mv5", "A \"quoted\" film", TitleType.MOVIE, listOf("Drama"), listOf(Svc.DISNEY, Svc.TUBI),
            0xFF102030, 0xFF405060, 2020, 7.5, TitleFont.PLAYFAIR, "Line one\nline two", art = "/x.jpg")
        assertEquals(listOf(t), TmdbParse.fromCache(TmdbParse.toCache(listOf(t))))
        assertNull(TmdbParse.fromCache("not json"))
    }

    @Test fun demoProgressUsesRealTitles() {
        val g = TmdbParse.genres(genres)
        val titles = TmdbParse.discover(discoverTv, TitleType.SERIES, Svc.NETFLIX, g)
        val p = TmdbParse.demoProgress(titles)
        assertTrue(p.isNotEmpty()); assertTrue(p.all { pr -> titles.any { it.id == pr.titleId } })
    }
}

class TmdbCatalogTest {
    private val genres = """{"genres":[{"id":18,"name":"Drama"}]}"""
    private fun page(id: Int, name: String) = """{"results":[{"id":$id,"name":"$name","title":"$name","overview":"x","first_air_date":"2020-01-01",
        "release_date":"2020-01-01","genre_ids":[18],"vote_average":7,"vote_count":500,"backdrop_path":"/$id.jpg"}]}"""

    @Test fun loadsEachServiceInItsRegion() = runTest {
        val urls = mutableListOf<String>()
        val cat = TmdbCatalog("k123") { url, _ ->
            urls += url
            when {
                "/watch/providers/" in url && "watch_region=IT" in url -> """{"results":[{"provider_id":222,"provider_name":"Rai Play"},{"provider_id":7,"provider_name":"La7"}]}"""
                "/watch/providers/" in url -> """{"results":[{"provider_id":8,"provider_name":"Netflix"}]}"""
                "/genre/" in url -> genres
                "with_watch_providers=8" in url -> page(1, "Net")
                "with_watch_providers=222" in url -> page(2, "Rai")
                else -> """{"results":[]}"""
            }
        }
        val titles = cat.load()!!
        assertEquals(setOf("Net", "Rai"), titles.map { it.name }.toSet())
        assertTrue(urls.any { "with_watch_providers=222" in it && "watch_region=IT" in it })
        assertTrue(urls.any { "with_watch_providers=8" in it && "watch_region=GB" in it })
        assertTrue("key sent as api_key", urls.all { "api_key=k123" in it })
    }

    @Test fun readAccessTokenGoesInTheHeaderNotTheUrl() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.abc.def"
        val r = TmdbCatalog.request("https://api.themoviedb.org/3/genre/tv/list?language=en-GB", jwt)
        assertTrue("api_key" !in r.first); assertEquals("Bearer $jwt", r.second)
    }

    @Test fun networkFailureGivesNull() = runTest {
        assertNull(TmdbCatalog("k") { _, _ -> null }.load())
    }
}
