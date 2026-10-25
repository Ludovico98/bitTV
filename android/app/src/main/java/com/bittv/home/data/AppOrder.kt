package com.bittv.home.data

import com.bittv.home.model.AppEntry

/** His own order for the app bar: saved ids first (unknown ones dropped), then any other app in the default order. */
object AppOrder {
    fun ordered(apps: List<AppEntry>, order: List<String>): List<AppEntry> {
        val byId = apps.associateBy { it.id }
        val first = order.mapNotNull { byId[it] }
        return first + apps.filter { it !in first }
    }

    /** [id] one place up (delta −1) or down (+1); unchanged at either end. */
    fun move(ids: List<String>, id: String, delta: Int): List<String> {
        val i = ids.indexOf(id); val j = i + delta
        if (i < 0 || j !in ids.indices) return ids
        return ids.toMutableList().also { it[i] = ids[j]; it[j] = id }
    }
}
