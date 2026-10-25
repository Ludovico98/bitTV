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
        val ambient = booleanPreferencesKey("ambient")
        val voice = stringPreferencesKey("voice")
        val guides = booleanPreferencesKey("overscan_guides")
    }

    private val defaults = Prefs()

    val prefs: Flow<Prefs> = store.data.map { p ->
        Prefs(
            subs = p[K.subs]?.mapNotNull { name -> Svc.entries.firstOrNull { it.name == name } }?.toSet() ?: defaults.subs,
            watchLater = p[K.watchLater]?.let { if (it.isEmpty()) emptyList() else it.split('|') } ?: defaults.watchLater,
            animations = p[K.animations] ?: defaults.animations,
            ambient = p[K.ambient] ?: defaults.ambient,
            voice = p[K.voice],
            overscanGuides = p[K.guides] ?: defaults.overscanGuides,
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
    suspend fun setAmbient(on: Boolean) { store.edit { it[K.ambient] = on } }
    suspend fun setVoice(name: String?) { store.edit { if (name == null) it.remove(K.voice) else it[K.voice] = name } }
    suspend fun setOverscanGuides(on: Boolean) { store.edit { it[K.guides] = on } }
}
