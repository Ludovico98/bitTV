package com.bittv.home.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PosterArtTest {
    @Test fun noArtAlwaysPlaceholder() = assertTrue(posterNeedsPlaceholder(hasArt = false, loaded = true))

    @Test fun loadingShowsPlaceholder() = assertTrue(posterNeedsPlaceholder(hasArt = true, loaded = false))

    @Test fun loadedHidesPlaceholder() = assertFalse(posterNeedsPlaceholder(hasArt = true, loaded = true))

    @Test fun cacheKeyCarriesTheRadius() {
        assertEquals("shade:0.4:0.6:18.0", ShadeTransformation(18f).cacheKey)
        assertNotEquals(ShadeTransformation(18f).cacheKey, ShadeTransformation(34f).cacheKey)
    }
}
