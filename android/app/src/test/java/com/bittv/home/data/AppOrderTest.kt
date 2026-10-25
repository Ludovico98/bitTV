package com.bittv.home.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderTest {
    private val apps = DemoCatalog.apps
    private fun ids(order: List<String>) = AppOrder.ordered(apps, order).map { it.id }

    @Test fun noSavedOrderKeepsTheDefault() = assertEquals(apps.map { it.id }, ids(emptyList()))

    @Test fun savedOrderComesFirstAndNewAppsFollowInDefaultOrder() {
        val got = ids(listOf("spotify", "netflix", "gone.app"))
        assertEquals(listOf("spotify", "netflix"), got.take(2))
        assertEquals(apps.map { it.id } - setOf("spotify", "netflix"), got.drop(2))   // unknown ids dropped, the rest kept
    }

    @Test fun moveSwapsWithTheNeighbourAndStopsAtTheEnds() {
        val base = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), AppOrder.move(base, "a", +1))
        assertEquals(listOf("a", "c", "b"), AppOrder.move(base, "c", -1))
        assertEquals(base, AppOrder.move(base, "a", -1))
        assertEquals(base, AppOrder.move(base, "c", +1))
    }
}
