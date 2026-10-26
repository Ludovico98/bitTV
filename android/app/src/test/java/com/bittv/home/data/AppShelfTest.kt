package com.bittv.home.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShelfTest {
    private val known = setOf("netflix", "prime", "tubi")

    @Test fun theFirstTimeTheBarStaysAsItWasAndTheTvsOtherAppsStartOff() {
        val s = AppShelf.reconcile(listOf("netflix", "prime", "com.tcl.settings", "com.youtube"), known, seen = null, hidden = emptySet())!!
        assertEquals(setOf("com.tcl.settings", "com.youtube"), s.hidden)
        assertEquals(setOf("netflix", "prime", "tubi", "com.tcl.settings", "com.youtube"), s.seen)
    }

    @Test fun anAppInstalledLaterGoesOnTheBar() {
        val seen = setOf("netflix", "prime", "tubi", "com.tcl.settings")
        val s = AppShelf.reconcile(listOf("netflix", "com.tcl.settings", "com.crunchyroll"), known, seen, hidden = setOf("com.tcl.settings"))!!
        assertEquals(setOf("com.tcl.settings"), s.hidden)
        assertEquals(seen + "com.crunchyroll", s.seen)
    }

    @Test fun nothingNewChangesNothingAndASwitchedOffAppStaysOff() {
        val seen = setOf("netflix", "prime", "tubi", "com.crunchyroll")
        assertNull(AppShelf.reconcile(listOf("netflix", "com.crunchyroll"), known, seen, hidden = setOf("com.crunchyroll")))
        // one of BitTV's own apps installed later was already known: it keeps the switch he gave it
        assertNull(AppShelf.reconcile(listOf("tubi"), known, seen, hidden = setOf("tubi")))
    }
}
