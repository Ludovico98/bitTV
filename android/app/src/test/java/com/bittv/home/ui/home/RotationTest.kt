package com.bittv.home.ui.home

import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.Prefs
import com.bittv.home.model.isAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {
    private fun state(slot: Long) = HomeState(Prefs(), DemoCatalog.titles, DemoCatalog.progress, DemoCatalog.apps, emptySet(),
        emptyList(), null, emptyList(), null, rotation = slot)
    private fun row(slot: Long, key: String) = homeRows(state(slot)).first { it.key == key }.items.map { it.key }
    /** Every pick on Home outside his two lists, row by row. */
    private fun picks(slot: Long) = homeRows(state(slot)).filter { it.key != "wl" && it.key != "cw" }.map { it.key to it.items.map { i -> i.key } }

    @Test fun theSameHalfHourAlwaysLooksTheSame() {
        assertEquals(bannerTitles(state(57_000)), bannerTitles(state(57_000)))
        assertEquals(picks(57_000), picks(57_000))
    }

    @Test fun eachHalfHourBringsDifferentPicks() {
        val slots = (57_000L..57_005L)
        assertTrue("banner changes", slots.map { bannerTitles(state(it)).map { t -> t.id } }.toSet().size > 1)
        assertTrue("the rows change", slots.map { picks(it) }.toSet().size > 1)
    }

    @Test fun theBannerStaysAmongTheBestRated() {
        val top = DemoCatalog.titles.filter { it.isAvailable(Prefs().subs) }.sortedByDescending { it.score }.take(20).map { it.id }.toSet()
        for (s in 57_000L..57_010L) assertTrue(bannerTitles(state(s)).all { it.id in top })
        assertEquals(5, bannerTitles(state(57_003)).size)
    }

    @Test fun watchLaterAndContinueWatchingNeverMove() {
        assertEquals(row(0, "wl"), row(57_001, "wl")); assertEquals(row(57_000, "wl"), row(57_002, "wl"))
        assertEquals(row(57_000, "cw"), row(57_001, "cw"))
    }

    @Test fun halfHourSlots() {
        assertEquals(0L, Rotation.slot(0)); assertEquals(0L, Rotation.slot(29 * 60_000L)); assertEquals(1L, Rotation.slot(30 * 60_000L))
        assertEquals(30 * 60_000L - 1, Rotation.untilNext(1))
    }
}
