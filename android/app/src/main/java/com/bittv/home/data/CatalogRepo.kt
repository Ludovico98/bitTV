package com.bittv.home.data

import com.bittv.home.data.tmdb.TmdbParse
import com.bittv.home.model.Progress
import com.bittv.home.model.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Where real titles come from (TMDB); null from [load] means "couldn't reach it". */
interface CatalogSource { suspend fun load(): List<Title>? }

/**
 * The titles on Home and in Search. Without a source (no TMDB key) it is the demo catalogue. With one, the last
 * catalogue saved in [cacheFile] shows at once (also offline) and [refresh] replaces it at most every 12 hours.
 */
class CatalogRepo(
    private val source: CatalogSource?,
    private val cacheFile: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Source { DEMO, TMDB }
    data class State(val titles: List<Title>, val progress: List<Progress>, val source: Source)

    private fun tmdbState(titles: List<Title>) = State(titles, TmdbParse.demoProgress(titles), Source.TMDB)
    private val demo = State(DemoCatalog.titles, DemoCatalog.progress, Source.DEMO)

    private val _state = MutableStateFlow(
        if (source == null) demo
        else cacheFile.takeIf { it.length() > 0 }?.let { runCatching { TmdbParse.fromCache(it.readText()) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }?.let(::tmdbState) ?: demo
    )
    val state: StateFlow<State> = _state.asStateFlow()

    suspend fun refresh() {
        val src = source ?: return
        if (_state.value.source == Source.TMDB && now() - cacheFile.lastModified() < MAX_AGE_MS) return
        val titles = src.load() ?: return
        _state.value = tmdbState(titles)
        withContext(Dispatchers.IO) { cacheFile.writeText(TmdbParse.toCache(titles)) }
    }

    companion object { const val MAX_AGE_MS = 12 * 3_600_000L }
}
