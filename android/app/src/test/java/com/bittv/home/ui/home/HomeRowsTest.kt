package com.bittv.home.ui.home

import com.bittv.home.model.Prefs
import com.bittv.home.model.Progress
import com.bittv.home.model.Svc
import com.bittv.home.model.Tags
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowsTest {
    private var n = 0
    private fun t(genres: List<String>, tags: List<String> = emptyList(), svc: Svc = Svc.NETFLIX, type: TitleType = TitleType.SERIES, score: Double = 5.0) =
        Title("t${n++}", "T$n", type, genres, listOf(svc), 0, 0, 2020, score, TitleFont.INTER, "", tags = tags)

    // a banner's worth of top titles first (the banner takes them), then the rows' material
    private val top = List(5) { t(listOf("Drama"), score = 9.0) }
    private val sitcoms = List(4) { t(listOf("Comedy"), listOf(Tags.SITCOM)) }
    private val comedies = List(4) { t(listOf("Comedy")) }
    private val dramas = List(6) { t(listOf("Drama", "Comedy")) }
    private val italian = List(3) { t(listOf("Drama"), svc = Svc.RAIPLAY) }
    private val titles = top + sitcoms + comedies + dramas + italian
    private fun state(prefs: Prefs = Prefs(watchLater = emptyList())) =
        HomeState(prefs, titles, listOf(Progress(dramas[0].id, 0.5f, "")), emptyList(), emptySet(), emptyList(), null, emptyList(), null)
    private fun rows(prefs: Prefs = Prefs(watchLater = emptyList())) = homeRows(state(prefs))
    private fun ids(r: HomeRow) = r.items.map { it.key }

    @Test fun noTitleIsInTwoRowsOrInTheBannerToo() {
        val rs = rows().filter { it.key != "cw" }
        val all = rs.flatMap(::ids)
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.none { id -> bannerTitles(state()).any { it.id == id } })
        assertTrue("continue watching isn't repeated", dramas[0].id !in all)
    }

    @Test fun theNarrowKindsChooseFirst() {
        val rs = rows().associateBy { it.key }
        assertEquals(sitcoms.map { it.id }.toSet(), ids(rs.getValue("sitcom")).toSet())     // not taken by Comedy
        assertEquals(italian.map { it.id }.toSet(), ids(rs.getValue("it")).toSet())          // not taken by Drama
        assertTrue(ids(rs.getValue("comedy")).none { id -> sitcoms.any { it.id == id } })
    }

    @Test fun aKindWithTooFewTitlesIsLeftOut() {
        assertTrue(rows().none { it.key == "horror" || it.key == "anthology" })
    }

    @Test fun theRowsComeInHisOrderTheWatchListFirstByDefault() {
        assertEquals("wl", rows().first().key)
        val moved = rows(Prefs(watchLater = emptyList(), rowOrder = listOf("comedy", "it", "wl"))).map { it.key }
        assertEquals(listOf("comedy", "it", "wl"), moved.take(3))
        assertTrue(moved.indexOf("cw") > moved.indexOf("wl"))   // the rest after, in the default order
    }

    @Test fun savedOrderKeepsUnknownAndMissingKeysSensible() {
        assertEquals(listOf("b", "a", "c"), orderedKeys(listOf("a", "b", "c"), listOf("gone", "b", "b")))
    }
}
