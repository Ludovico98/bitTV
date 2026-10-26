package com.bittv.home.search

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedTest {
    private fun t(id: String, genres: List<String>, type: TitleType = TitleType.SERIES, score: Double = 7.0, svc: Svc = Svc.NETFLIX) =
        Title(id, id, type, genres, listOf(svc), 0, 0, 2024, score, TitleFont.INTER, "")
    private val subs = setOf(Svc.NETFLIX)

    @Test
    fun moreSharedGenresComeFirstThenTheSameTypeThenTheScore() {
        val me = t("me", listOf("Crime", "Drama"))
        val pool = listOf(me,
            t("one", listOf("Crime"), score = 9.0),
            t("both", listOf("Drama", "Crime"), score = 6.0),
            t("film", listOf("Crime"), TitleType.MOVIE, score = 9.5),
            t("other", listOf("Comedy")))
        assertEquals(listOf("both", "one", "film"), Related.to(me, pool, subs).map { it.id })
    }

    @Test
    fun onlyTitlesOnHisAppsAndNeverTheTitleItself() {
        val me = t("me", listOf("Crime"))
        val pool = listOf(me, t("elsewhere", listOf("Crime"), svc = Svc.DISNEY), t("here", listOf("Crime")))
        assertEquals(listOf("here"), Related.to(me, pool, subs).map { it.id })
    }

    @Test
    fun atMostTheLimit() {
        val me = t("me", listOf("Crime"))
        val pool = (1..30).map { t("c$it", listOf("Crime")) }
        assertEquals(Related.LIMIT, Related.to(me, pool, subs).size)
    }
}
