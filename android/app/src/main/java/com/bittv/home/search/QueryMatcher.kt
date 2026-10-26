package com.bittv.home.search

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.TitleType
import com.bittv.home.model.isAvailable

/** [included]: on his subscriptions; [purchase]: on services he doesn't have, to rent or buy, or nowhere he can watch. */
data class SearchResult(val included: List<Title>, val purchase: List<Title>, val similar: List<Title>)

/**
 * Search over every title, split into those on the apps he has and the rest. Queries are often spoken phrases
 * ("sci-fi on apple tv"), so matching is word by word: every meaningful word must match the title, a genre, a service
 * or the type; when nothing matches every word, the best partial matches are returned instead.
 */
object QueryMatcher {
    private val STOP = setOf(
        "the", "a", "an", "on", "in", "of", "and", "to", "for", "with", "me", "i", "show", "shows",
        "find", "watch", "play", "want", "some", "something", "search", "please",
    )
    private val MOVIE = Regex("^(movie|film)s?$")
    private val APPLE = Regex("^(tv|apple)$")

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun matches(t: Title, tok: String, subs: Set<Svc>): Boolean {
        val stem = squash(tok.removeSuffix("s"))
        val word = squash(tok)
        // a token that is only punctuation, or just "s", must not match every genre and service by an empty prefix
        return t.name.lowercase().contains(tok) ||
            (stem.isNotEmpty() && t.genres.any { squash(it).startsWith(stem) }) ||
            (word.isNotEmpty() && t.services.any { it in subs && squash(it.label).startsWith(word) }) ||
            (tok == "series" && t.type == TitleType.SERIES) ||
            (MOVIE.matches(tok) && t.type == TitleType.MOVIE) ||
            (APPLE.matches(tok) && Svc.APPLETV in t.services)
    }

    const val SIMILAR = 12

    fun search(query: String, pool: List<Title>, subs: Set<Svc>): SearchResult {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResult(pool.filter { it.isAvailable(subs) }.sortedByDescending { it.score }, emptyList(), emptyList())

        val toks = q.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in STOP }
        class Hit(val t: Title, val n: Int, val phrase: Boolean)
        val scored = pool.map { t -> Hit(t, toks.count { matches(t, it, subs) }, t.name.lowercase().contains(q)) }
        val all = scored.filter { it.phrase || (toks.isNotEmpty() && it.n == toks.size) }
        val hits = all.ifEmpty { scored.filter { it.n > 0 } }
        val results = hits
            .sortedWith(compareByDescending<Hit> { it.phrase }.thenByDescending { it.n }.thenByDescending { it.t.score })
            .map { it.t }
        val (included, purchase) = results.partition { it.isAvailable(subs) }
        return SearchResult(included, purchase, similar(included + purchase, pool, subs))
    }

    /**
     * Titles on his apps like the top matches: what TMDB recommends after them ([related]) first, then the pool's
     * titles sharing their genres; never one of the matches themselves.
     */
    fun similar(results: List<Title>, pool: List<Title>, subs: Set<Svc>, related: List<Title> = emptyList()): List<Title> {
        if (results.isEmpty()) return emptyList()
        val ids = results.map { it.id }.toSet()
        val genres = results.take(3).flatMap { it.genres }.toSet()
        return (related + pool.filter { it.genres.any { g -> g in genres } })
            .filter { it.id !in ids && it.isAvailable(subs) }.distinctBy { it.id }.take(SIMILAR)
    }
}
