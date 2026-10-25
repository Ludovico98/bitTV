package com.bittv.home.search

import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryMatcherTest {
    private val subs = Prefs().subs
    private fun ids(q: String) = QueryMatcher.search(q, DemoCatalog.titles, subs).results.map { it.id }

    @Test
    fun crimeSeries() = assertEquals(listOf("tenements", "commissario", "northernline"), ids("crime series"))

    @Test
    fun theLongTide() = assertEquals("longtide", ids("the long tide").first())

    @Test
    fun sciFiOnAppleTv() = assertEquals(listOf("static", "eclipse"), ids("sci-fi on apple tv"))

    @Test
    fun comedyMovies() = assertEquals(listOf("lisbon", "laughtrack", "diner"), ids("comedy movies"))

    @Test
    fun netflix() {
        val r = QueryMatcher.search("netflix", DemoCatalog.titles, subs).results
        assertEquals(6, r.size)
        assertTrue(r.all { Svc.NETFLIX in it.services })
    }

    @Test
    fun stopWordsOnlyFallsBackToPhrase() {
        val r = QueryMatcher.search("the", DemoCatalog.titles, subs).results
        assertTrue(r.isNotEmpty())
        assertTrue(r.all { "the" in it.name.lowercase() })
    }

    @Test
    fun noMatchReturnsEmpty() {
        val r = QueryMatcher.search("xyzzy", DemoCatalog.titles, subs)
        assertTrue(r.results.isEmpty())
        assertTrue(r.similar.isEmpty())
    }

    @Test
    fun unsubscribedServiceIsHidden() {
        val r = ids("red harbour")
        assertTrue("redharbour" !in r)            // Paramount+ is not one of his apps
        assertEquals(listOf("harbourlights"), r)  // best partial match on his apps instead
    }

    @Test
    fun emptyQueryListsEverythingAvailableByScoreWithoutSimilar() {
        val r = QueryMatcher.search("  ", DemoCatalog.titles, subs)
        assertEquals("wildcoast", r.results.first().id)   // 8.7, the highest score on his apps
        assertTrue(r.similar.isEmpty())
    }

    @Test
    fun similarSharesAGenreAndExcludesResults() {
        val r = QueryMatcher.search("crime series", DemoCatalog.titles, subs)
        val genres = r.results.take(3).flatMap { it.genres }.toSet()
        assertTrue(r.similar.isNotEmpty() && r.similar.size <= 12)
        assertTrue(r.similar.none { it in r.results })
        assertTrue(r.similar.all { t -> t.genres.any { it in genres } })
    }
}
