package com.bittv.home.data

import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import com.bittv.home.model.isAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCatalogTest {
    private val byId = DemoCatalog.titles.associateBy { it.id }

    @Test
    fun catalogueHas53Titles() {
        assertEquals(53, DemoCatalog.titles.size)
        assertEquals(53, byId.size) // ids are unique
    }

    @Test
    fun everyProgressRefersToATitle() {
        assertEquals(7, DemoCatalog.progress.size)
        DemoCatalog.progress.forEach { assertTrue(it.titleId, it.titleId in byId) }
    }

    @Test
    fun railHasTheFourteenAppsInOrder() {
        assertEquals(
            listOf("Netflix", "Prime Video", "NOW", "Paramount+", "Apple TV", "Spotify", "SmartTube", "Disney+",
                "Tubi", "ITVX", "La7", "RaiPlay", "Play Store", "Button Mapper"),
            DemoCatalog.apps.map { it.label },
        )
    }

    @Test
    fun availabilityFollowsSubscriptions() {
        val red = byId.getValue("redharbour")
        val defaults = Prefs().subs
        assertFalse(red.isAvailable(defaults))
        assertTrue(red.isAvailable(defaults + Svc.PARAMOUNT))
    }
}
