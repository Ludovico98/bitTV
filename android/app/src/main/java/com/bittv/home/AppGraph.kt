package com.bittv.home

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.bittv.home.data.DemoCatalog
import com.bittv.home.data.PrefsRepo

private val Context.prefsStore by preferencesDataStore(name = "bittv")

/** Everything long-lived, built once in [BitTvApp]. Plan 2 swaps the catalogue for TMDB when a key is set. */
class AppGraph(context: Context) {
    val prefs = PrefsRepo(context.applicationContext.prefsStore)
    val catalog = DemoCatalog
}
