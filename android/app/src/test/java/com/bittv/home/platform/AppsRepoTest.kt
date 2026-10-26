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
        var launcher = listOf<Pair<String, String>>()
        override fun launchableApps() = launcher
    }
    private val app = AppEntry("x", "X", listOf("a.first", "a.second"), null)

    @Test fun everyAppOnTheTvIsListedOursFirstThenTheRestByName() {
        val pk = FakePackages(setOf("a.second", "com.zz", "com.crunchyroll"))
        pk.launcher = listOf("a.second" to "X (the TV app)", "com.zz" to "Zoom", "com.crunchyroll" to "Crunchyroll", "com.zz" to "Zoom")
        val missing = AppEntry("y", "Y", listOf("b.only"), null)
        val apps = AppsRepo(pk).installedApps(listOf(app, missing))
        assertEquals(listOf("x", "com.crunchyroll", "com.zz"), apps.map { it.id })   // our entry, not its package again
        assertEquals(AppEntry("com.crunchyroll", "Crunchyroll", listOf("com.crunchyroll"), null), apps[1])
    }

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

class OpenTitleTest {
    private class Pk(val links: Boolean, val search: Boolean) : Packages {
        val did = mutableListOf<String>()
        override fun isLaunchable(pkg: String) = true
        override fun launch(pkg: String): Boolean { did += "launch"; return true }
        override fun openStore(pkg: String) {}
        override fun openLink(pkg: String, url: String): Boolean { if (links) did += "link $url"; return links }
        override fun searchIn(pkg: String, query: String): Boolean { if (search) did += "search $query"; return search }
    }
    private val netflix = com.bittv.home.data.DemoCatalog.apps.first { it.id == "netflix" }

    @Test fun titlePageFirstThenSearchThenTheApp() {
        Pk(true, true).also { AppsRepo(it).openTitle(netflix, "https://www.netflix.com/title/80057281", "Stranger Things"); assertEquals(listOf("link https://www.netflix.com/title/80057281"), it.did) }
        Pk(false, true).also { AppsRepo(it).openTitle(netflix, "https://www.nowtv.com/watch/x", "Stranger Things"); assertEquals(listOf("search Stranger Things"), it.did) }
        Pk(false, false).also { AppsRepo(it).openTitle(netflix, null, "Stranger Things"); assertEquals(listOf("launch"), it.did) }
    }
}
