package com.bittv.home

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.bittv.home.data.CatalogRepo
import com.bittv.home.data.DemoCatalog
import com.bittv.home.data.PrefsRepo
import com.bittv.home.data.tmdb.ArtColorLoader
import com.bittv.home.data.tmdb.TmdbCatalog
import com.bittv.home.net.httpGet
import com.bittv.home.platform.AndroidPackages
import com.bittv.home.platform.AppsRepo
import java.io.File

private val Context.prefsStore by preferencesDataStore(name = "bittv")

/** Everything long-lived, built once in [BitTvApp]. The catalogue is TMDB's when a key is set, else the demo. */
class AppGraph(context: Context) {
    val prefs = PrefsRepo(context.applicationContext.prefsStore)
    val apps = DemoCatalog.apps
    val appsRepo = AppsRepo(AndroidPackages(context.applicationContext))
    val catalog = CatalogRepo(
        source = BuildConfig.TMDB_KEY.takeIf { it.isNotBlank() }?.let { key -> TmdbCatalog(key) { url, auth -> httpGet(url, auth) } },
        cacheFile = File(context.filesDir, "tmdb-catalog.json"),
        colours = ArtColorLoader::apply,
    )
}
