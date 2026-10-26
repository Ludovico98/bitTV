package com.bittv.home.search

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.isAvailable

/**
 * What a title's page offers below its buttons: titles on his apps that share its genres, the most shared first,
 * then the same kind (series or film), then the best rated.
 */
object Related {
    const val LIMIT = 12

    fun to(title: Title, pool: List<Title>, subs: Set<Svc>): List<Title> {
        val genres = title.genres.toSet()
        return pool.asSequence()
            .filter { it.id != title.id && it.isAvailable(subs) }
            .map { it to it.genres.count { g -> g in genres } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Title, Int>> { it.second }
                .thenByDescending { it.first.type == title.type }.thenByDescending { it.first.score })
            .map { it.first }.distinctBy { it.id }.take(LIMIT).toList()
    }
}
