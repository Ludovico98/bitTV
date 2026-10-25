package com.bittv.home.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageScrollTest {
    private val heights = mapOf(0 to 66, 1 to 503, 2 to 270)

    @Test fun atTheTopNothingIsScrolled() = assertEquals(0, pageScrolled(0, 0) { heights[it] })

    @Test fun scrolledIsTheItemsAboveThePlusTheFirstOnesHiddenPart() {
        assertEquals(40, pageScrolled(0, 40) { heights[it] })
        assertEquals(66 + 503 + 12, pageScrolled(2, 12) { heights[it] })
    }

    // an item above that was never laid out (focus jumped past it): unknown, the page falls back to the clamp
    @Test fun unknownWhenAnItemAboveWasNeverMeasured() = assertNull(pageScrolled(4, 0) { heights[it] })

    @Test fun theTopIsAimedAtExactlyFromUpToAScreenAway() {
        assertEquals(-640f, topDistance(640, 1080f))
        assertEquals(-1080f, topDistance(1080, 1080f))
        assertEquals(-100_000f, topDistance(3000, 1080f))   // Back from deep down: one jump
        assertEquals(-100_000f, topDistance(null, 1080f))
    }
}
