package com.bittv.home.data

import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLaterSeedTest {
    private fun t(id: String, score: Double, svc: Svc = Svc.NETFLIX) =
        Title(id, id, TitleType.SERIES, emptyList(), listOf(svc), 0, 0, 2024, score, TitleFont.INTER, "")
    private val real = listOf(t("tv1", 5.0), t("tv2", 9.0), t("tv3", 7.0), t("tv4", 8.0, Svc.PARAMOUNT), t("tv5", 6.0), t("tv6", 4.0), t("tv7", 3.0))
    private val subs = Prefs().subs

    @Test fun demoPicksAreReplacedByRealOnesOnce() {
        assertEquals(listOf("tv2", "tv3", "tv5", "tv1", "tv6"), WatchLaterSeed.replaceDemo(Prefs().watchLater, real, subs))
    }

    @Test fun hisOwnListIsNeverTouched() {
        assertNull(WatchLaterSeed.replaceDemo(listOf("tv1", "longtide"), real, subs))
        assertNull(WatchLaterSeed.replaceDemo(emptyList(), real, subs))
    }

    @Test fun demoCatalogueKeepsDemoPicks() {
        assertNull(WatchLaterSeed.replaceDemo(Prefs().watchLater, DemoCatalog.titles, subs))
    }
}
