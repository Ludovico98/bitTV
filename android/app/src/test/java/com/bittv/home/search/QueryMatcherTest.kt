package com.bittv.home.search

import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryMatcherTest {
    private val subs = Prefs().subs
    private fun ids(q: String) = QueryMatcher.search(q, DemoCatalog.titles, subs).included.map { it.id }

    @Test
    fun crimeSeries() = assertEquals(listOf("tenements", "commissario", "northernline", "coldcase", "dockside"), ids("crime series"))

    @Test
    fun theLongTide() = assertEquals("longtide", ids("the long tide").first())

    @Test
    fun sciFiOnAppleTv() = assertEquals(listOf("static", "eclipse"), ids("sci-fi on apple tv"))

    @Test
    fun comedyMovies() = assertEquals(listOf("lisbon", "laughtrack", "diner"), ids("comedy movies"))

    @Test
    fun netflix() {
        val r = QueryMatcher.search("netflix", DemoCatalog.titles, subs).included
        assertEquals(11, r.size)
        assertTrue(r.all { Svc.NETFLIX in it.services })
    }

    @Test
    fun stopWordsOnlyFallsBackToPhrase() {
        val r = QueryMatcher.search("the", DemoCatalog.titles, subs).included
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { "the" in it.name.lowercase() })
    }

    @Test
    fun noMatchReturnsEmpty() {
        val r = QueryMatcher.search("xyzzy", DemoCatalog.titles, subs)
        assertTrue(r.included.isEmpty() && r.purchase.isEmpty())
        assertTrue(r.similar.isEmpty())
    }

    @Test
    fun unsubscribedServiceGoesToPurchase() {
        val r = QueryMatcher.search("red harbour", DemoCatalog.titles, subs)
        assertEquals(listOf("redharbour"), r.purchase.map { it.id })   // Paramount+ is not one of his apps
        assertTrue(r.included.isEmpty())                                // no weaker partial match on his apps instead
        assertTrue(r.similar.isNotEmpty() && r.similar.all { t -> t.services.any { it in subs } })
    }

    @Test
    fun emptyQueryListsEverythingAvailableByScoreWithoutSimilar() {
        val r = QueryMatcher.search("  ", DemoCatalog.titles, subs)
        assertEquals("wildcoast", r.included.first().id)   // 8.7, the highest score on his apps
        assertTrue(r.purchase.isEmpty() && r.similar.isEmpty())
    }

    @Test
    fun similarSharesAGenreAndExcludesResults() {
        val r = QueryMatcher.search("crime series", DemoCatalog.titles, subs)
        val genres = (r.included + r.purchase).take(3).flatMap { it.genres }.toSet()
        assertTrue(r.similar.isNotEmpty() && r.similar.size <= 12)
        assertTrue(r.similar.none { it in r.included })
        assertTrue(r.similar.all { t -> t.genres.any { it in genres } })
    }

    @Test
    fun singleLetterDoesNotMatchEveryGenre() {
        // "s" stems to "" — an empty prefix must not match every genre and service
        val owned = DemoCatalog.titles.count { t -> t.services.any { it in subs } }
        val r = QueryMatcher.search("s", DemoCatalog.titles, subs).included
        assertTrue("${r.size} of $owned", r.size < owned)
        assertTrue(QueryMatcher.search("-", DemoCatalog.titles, subs).included.isEmpty())
    }

    @Test
    fun recommendationsLeadSimilarAndOnlyTitlesOnHisAppsShow() {
        val match = DemoCatalog.titles.first { it.id == "longtide" }
        val onApps = DemoCatalog.titles.filter { t -> t.id != match.id && t.services.any { it in subs } }
        val paramount = DemoCatalog.titles.first { it.id == "redharbour" }
        val s = QueryMatcher.similar(listOf(match), DemoCatalog.titles, subs, related = listOf(onApps.last(), paramount, match))
        assertEquals(onApps.last().id, s.first().id)
        assertTrue(s.none { it.id == paramount.id || it.id == match.id })
    }
}
