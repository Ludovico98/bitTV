package com.bittv.home.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.bittv.home.model.Prefs
import com.bittv.home.model.Svc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Settings and Watch Later, stored on the TV. Absent keys fall back to [Prefs] defaults. */
class PrefsRepo(private val store: DataStore<Preferences>) {
    private object K {
        val subs = stringSetPreferencesKey("subs")
        val watchLater = stringPreferencesKey("watch_later")   // ordered, newest first, '|'-separated
        val animations = booleanPreferencesKey("animations")
        val guides = booleanPreferencesKey("overscan_guides")
        val appOrder = stringPreferencesKey("app_order")
        val hiddenApps = stringSetPreferencesKey("hidden_apps")
        val rowOrder = stringPreferencesKey("row_order")
        val seenApps = stringSetPreferencesKey("seen_apps")
    }

    private val defaults = Prefs()

    val prefs: Flow<Prefs> = store.data.map { p ->
        Prefs(
            subs = p[K.subs]?.mapNotNull { name -> Svc.entries.firstOrNull { it.name == name } }?.toSet() ?: defaults.subs,
            watchLater = p[K.watchLater]?.let { if (it.isEmpty()) emptyList() else it.split('|') } ?: defaults.watchLater,
            animations = p[K.animations] ?: defaults.animations,
            overscanGuides = p[K.guides] ?: defaults.overscanGuides,
            appOrder = p[K.appOrder]?.split('|')?.filter { it.isNotEmpty() } ?: defaults.appOrder,
            hiddenApps = p[K.hiddenApps] ?: defaults.hiddenApps,
            rowOrder = p[K.rowOrder]?.split('|')?.filter { it.isNotEmpty() } ?: defaults.rowOrder,
            seenApps = p[K.seenApps],
        )
    }

    // toggles read and write inside one edit, so two quick presses both count
    suspend fun toggleSub(s: Svc) {
        store.edit { p ->
            val subs = p[K.subs] ?: defaults.subs.map { it.name }.toSet()
            p[K.subs] = if (s.name in subs) subs - s.name else subs + s.name
        }
    }

    /** Returns true when the title is now saved. */
    suspend fun toggleWatchLater(id: String): Boolean {
        var saved = false
        store.edit { p ->
            val list = p[K.watchLater]?.let { if (it.isEmpty()) emptyList() else it.split('|') } ?: defaults.watchLater
            saved = id !in list
            p[K.watchLater] = (if (saved) listOf(id) + list else list - id).joinToString("|")
        }
        return saved
    }

    suspend fun setWatchLater(ids: List<String>) { store.edit { it[K.watchLater] = ids.joinToString("|") } }

    suspend fun clearWatchLater() { store.edit { it[K.watchLater] = "" } }
    suspend fun setAnimations(on: Boolean) { store.edit { it[K.animations] = on } }
    suspend fun setOverscanGuides(on: Boolean) { store.edit { it[K.guides] = on } }
    suspend fun setAppOrder(ids: List<String>) { store.edit { it[K.appOrder] = ids.joinToString("|") } }

    suspend fun setRowOrder(keys: List<String>) { store.edit { it[K.rowOrder] = keys.joinToString("|") } }

    /** The app's switch in Settings: on the app bar or off it. */
    suspend fun toggleAppShown(id: String) {
        store.edit { p -> val hidden = p[K.hiddenApps] ?: emptySet(); p[K.hiddenApps] = if (id in hidden) hidden - id else hidden + id }
    }

    /** Records the apps on the TV now: one installed since last time goes on the app bar (read and written in one edit). */
    suspend fun reconcileApps(installed: Collection<String>, known: Set<String>) {
        store.edit { p ->
            AppShelf.reconcile(installed, known, p[K.seenApps], p[K.hiddenApps] ?: emptySet())?.let { p[K.seenApps] = it.seen; p[K.hiddenApps] = it.hidden }
        }
    }
}
