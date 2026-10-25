package com.bittv.home.platform

import com.bittv.home.data.DemoCatalog
import com.bittv.home.model.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppsRepoTest {
    private class FakePackages(var present: Set<String>) : Packages {
        val launched = mutableListOf<String>(); val store = mutableListOf<String>()
        override fun isLaunchable(pkg: String) = pkg in present
        override fun launch(pkg: String): Boolean { if (pkg !in present) return false; launched += pkg; return true }
        override fun openStore(pkg: String) { store += pkg }
    }
    private val app = AppEntry("x", "X", listOf("a.first", "a.second"), null)

    @Test fun installedPicksAnyPresentCandidate() {
        val repo = AppsRepo(FakePackages(setOf("a.second")))
        assertEquals(setOf("x"), repo.installed(listOf(app)))
        assertEquals("a.second", repo.packageFor(app))
    }

    @Test fun launchOpensThePresentPackage() {
        val pk = FakePackages(setOf("a.second")); val repo = AppsRepo(pk)
        assertTrue(repo.launch(app)); assertEquals(listOf("a.second"), pk.launched)
    }

    @Test fun missingAppIsNotInstalledAndCannotLaunch() {
        val pk = FakePackages(emptySet()); val repo = AppsRepo(pk)
        assertEquals(emptySet<String>(), repo.installed(listOf(app)))
        assertFalse(repo.launch(app))
        repo.openStore(app); assertEquals(listOf("a.first"), pk.store)   // the store page of the main package
    }

    @Test fun refreshOnResumeSeesAnUninstall() {
        val pk = FakePackages(setOf("a.first")); val repo = AppsRepo(pk)
        assertEquals(setOf("x"), repo.installed(listOf(app)))
        pk.present = emptySet()
        assertEquals(emptySet<String>(), repo.installed(listOf(app)))
    }

    @Test fun yourServicesAreTheInstalledApps() {
        val ids = setOf("netflix", "spotify", "tubi", "playstore")
        assertEquals(setOf(com.bittv.home.model.Svc.NETFLIX, com.bittv.home.model.Svc.TUBI), AppsRepo.services(DemoCatalog.apps, ids))
    }

    @Test fun packagesOnLudovicosTv() {
        // read from the TV on 2026-10-25 (pm list packages)
        val tv = setOf("air.ITVMobilePlayer", "com.amazon.amazonvideo.livingroom", "com.android.vending", "com.apple.atve.androidtv.appletv",
            "com.bskyb.nowtv.beta", "com.disney.disneyplus", "com.netflix.ninja", "com.spotify.tv.android", "com.tubitv",
            "flar2.homebutton", "it.la7.it", "it.rainet.androidtv", "org.smarttube.stable")
        assertEquals(DemoCatalog.apps.map { it.id }.toSet() - "paramount", AppsRepo(FakePackages(tv)).installed(DemoCatalog.apps))
    }
}
