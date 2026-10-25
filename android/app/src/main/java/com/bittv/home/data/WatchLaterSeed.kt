package com.bittv.home.data

import com.bittv.home.model.Svc
import com.bittv.home.model.Title
import com.bittv.home.model.isAvailable

/** When the real catalogue arrives, the demo's Watch Later picks (which no longer exist) become real ones, once. */
object WatchLaterSeed {
    private val demoIds = DemoCatalog.titles.map { it.id }.toSet()

    /** The new list, or null to leave his list alone (it has anything of his own in it, or is empty, or is demo data). */
    fun replaceDemo(saved: List<String>, titles: List<Title>, subs: Set<Svc>): List<String>? {
        if (saved.isEmpty() || !saved.all { it in demoIds }) return null
        if (titles.any { it.id in demoIds }) return null
        return titles.filter { it.isAvailable(subs) }.sortedByDescending { it.score }.take(5).map { it.id }.takeIf { it.isNotEmpty() }
    }
}
