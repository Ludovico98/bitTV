package com.bittv.home.platform

import com.bittv.home.model.Svc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class WatchLinksTest {
    private fun a(provider: String, type: String, target: String): String {
        val cx = """{"schema":"x","data":[{"schema":"y","data":{"provider":"$provider","monetizationType":"$type"}}]}"""
        val enc = Base64.getUrlEncoder().withoutPadding().encodeToString(cx.toByteArray())
        return """<a class="relative" href="https://click.justwatch.com/a?cx=$enc&amp;r=${java.net.URLEncoder.encode(target, "UTF-8")}&amp;uct_country=gb">"""
    }
    // shapes seen on TMDB watch pages, 2026-10-25
    private val page = listOf(
        a("Netflix", "flatrate", "https://www.netflix.com/title/80057281"),
        a("Netflix Standard with Ads", "flatrate", "https://www.netflix.com/title/82939566"),
        a("Amazon Prime Video", "flatrate", "https://watch.amazon.co.uk/detail?gti=amzn1.dv.gti.9ab600aa"),
        a("Amazon Video", "buy", "https://watch.amazon.co.uk/detail?gti=amzn1.dv.gti.d9a17eaa"),
        a("Disney Plus", "flatrate", "https://disneyplus.bn5x.net/c/1206980/705874/9358?u=https%3A%2F%2Fwww.disneyplus.com%2Fen-gb%2Fbrowse%2Fentity-abc"),
        a("Paramount Plus Apple TV channel", "flatrate", "https://tv.apple.com"),
        a("Paramount Plus", "flatrate", "https://www.paramountplus.com/shows/halo/video/q0V6/halo-contact?searchReferral=JustWatch"),
        a("Apple TV", "flatrate", "https://tv.apple.com/gb/show/severance/umc.cmc.1srk?at=1000l3V2&ct=app_tv"),
        a("Rai Play", "ads", "https://www.raiplay.it/video/2026/09/NCIS.html"),
    ).joinToString("\n")

    @Test fun eachServiceGetsItsOwnPageForTheTitle() {
        val links = WatchLinks.parse(page)
        assertEquals("https://www.netflix.com/title/80057281", links[Svc.NETFLIX])
        assertEquals("https://app.primevideo.com/detail?gti=amzn1.dv.gti.9ab600aa", links[Svc.PRIME])   // the form the TV app opens
        assertEquals("https://www.disneyplus.com/en-gb/browse/entity-abc", links[Svc.DISNEY])          // affiliate wrapper removed
        assertEquals("https://www.paramountplus.com/shows/halo/video/q0V6/halo-contact", links[Svc.PARAMOUNT])  // not the Apple channel
        assertEquals("https://tv.apple.com/gb/show/severance/umc.cmc.1srk", links[Svc.APPLETV])
        assertEquals("https://www.raiplay.it/video/2026/09/NCIS.html", links[Svc.RAIPLAY])
    }

    @Test fun rentalsAndPurchasesAreNeverUsed() {
        val links = WatchLinks.parse(a("Amazon Video", "buy", "https://watch.amazon.co.uk/detail?gti=x") + a("Apple TV Store", "rent", "https://tv.apple.com/gb/movie/x"))
        assertTrue(links.isEmpty())
    }

    @Test fun titleIdsMapToTmdbPages() {
        assertEquals("https://www.themoviedb.org/tv/66732/watch?locale=GB", WatchLinks.pageUrl("tv66732", Svc.NETFLIX))
        assertEquals("https://www.themoviedb.org/movie/603/watch?locale=GB", WatchLinks.pageUrl("mv603", Svc.DISNEY))
        assertEquals("https://www.themoviedb.org/tv/4614/watch?locale=IT", WatchLinks.pageUrl("tv4614", Svc.RAIPLAY))
        assertNull(WatchLinks.pageUrl("longtide", Svc.NETFLIX))   // demo titles have no real page
    }

    @Test fun wakingTheTvPlaysTheAnimationOnlyWhenBitTvIsWhatComesBack() {
        assertTrue(WakeGate.playOnResume(lastScreenOn = 10_000, now = 12_000))
        assertEquals(false, WakeGate.playOnResume(lastScreenOn = 10_000, now = 30_000))   // an ordinary return to Home
        assertEquals(false, WakeGate.playOnResume(lastScreenOn = 0, now = 12_000))        // no wake seen
    }
}
