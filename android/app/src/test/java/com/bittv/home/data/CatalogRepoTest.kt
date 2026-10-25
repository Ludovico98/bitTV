package com.bittv.home.data

import com.bittv.home.data.tmdb.TmdbCatalog
import com.bittv.home.data.tmdb.TmdbParse
import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleFont
import com.bittv.home.model.TitleType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogRepoTest {
    @get:Rule val tmp = TemporaryFolder()
    private val real = Title("tv1", "Real Show", TitleType.SERIES, listOf("Drama"), listOf(Svc.NETFLIX), 0xFF111111, 0xFF222222,
        2024, 8.0, TitleFont.INTER, "x", art = "/a.jpg")
    private fun tmdb(result: List<Title>?, calls: IntArray = IntArray(1)) = object : CatalogSource {
        override suspend fun load(): List<Title>? { calls[0]++; return result }
    }

    @Test fun noKeyUsesDemo() = runTest {
        val repo = CatalogRepo(null, tmp.newFile())
        repo.refresh()
        assertEquals(DemoCatalog.titles, repo.state.value.titles)
        assertEquals(CatalogRepo.Source.DEMO, repo.state.value.source)
    }

    @Test fun loadedCatalogReplacesDemoAndIsCached() = runTest {
        val file = tmp.newFile()
        val repo = CatalogRepo(tmdb(listOf(real)), file)
        repo.refresh()
        assertEquals(listOf(real), repo.state.value.titles)
        assertEquals(CatalogRepo.Source.TMDB, repo.state.value.source)
        assertTrue(repo.state.value.progress.all { it.titleId == "tv1" })
        assertEquals(listOf(real), TmdbParse.fromCache(file.readText()))
    }

    @Test fun cachedCatalogIsShownAtStartWithoutNetwork() = runTest {
        val file = tmp.newFile().apply { writeText(TmdbParse.toCache(listOf(real))) }
        val repo = CatalogRepo(tmdb(null), file)
        assertEquals(listOf(real), repo.state.value.titles)   // before any refresh: instant start
        repo.refresh()                                         // offline: keeps the cache
        assertEquals(listOf(real), repo.state.value.titles)
    }

    @Test fun freshCacheSkipsTheNetwork() = runTest {
        val file = tmp.newFile().apply { writeText(TmdbParse.toCache(listOf(real))) }
        val calls = IntArray(1)
        CatalogRepo(tmdb(emptyList(), calls), file, now = { file.lastModified() + 60_000 }).refresh()
        assertEquals(0, calls[0])
        CatalogRepo(tmdb(listOf(real), calls), file, now = { file.lastModified() + 13 * 3_600_000L }).refresh()
        assertEquals(1, calls[0])
    }

    @Test fun artworkColoursAreAppliedAndKept() = runTest {
        val file = tmp.newFile()
        val repo = CatalogRepo(tmdb(listOf(real)), file, colours = { l -> l.map { it.copy(c1 = 0xFF00FF00) } })
        repo.refresh()
        assertEquals(0xFF00FF00, repo.state.value.titles.single().c1)
        assertEquals(0xFF00FF00, TmdbParse.fromCache(file.readText())!!.single().c1)
    }

    @Test fun continueWatchingDoesNotRepeatTheWatchLaterPicks() {
        val many = (1..12).map { real.copy(id = "tv$it", score = it.toDouble()) }
        val wl = WatchLaterSeed.replaceDemo(com.bittv.home.model.Prefs().watchLater, many, com.bittv.home.model.Prefs().subs)!!
        val cw = TmdbParse.demoProgress(many).map { it.titleId }
        assertTrue("overlap ${cw.intersect(wl.toSet())}", cw.none { it in wl })
        assertTrue(cw.isNotEmpty())
    }

    @Test fun tmdbCatalogIsACatalogSource() {
        val s: CatalogSource = TmdbCatalog("k") { _, _ -> null }
        assertTrue(s is TmdbCatalog)
    }
}
