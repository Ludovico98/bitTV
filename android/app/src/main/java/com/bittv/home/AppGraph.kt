package com.bittv.home

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.bittv.home.data.CatalogRepo
import com.bittv.home.data.DemoCatalog
import com.bittv.home.data.PrefsRepo
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
    val nowPlaying = com.bittv.home.platform.NowPlayingRepo(context.applicationContext)
    val tvInputs = com.bittv.home.platform.AndroidTvInputs(context.applicationContext)
    val network = com.bittv.home.platform.NetworkScanner(context.applicationContext)
    // Ludovico lives in Redhill, Surrey
    val answers = com.bittv.home.assistant.LiveAnswers({ url -> httpGet(url) }, place = "Redhill", lat = 51.24, lon = -0.17,
        general = BuildConfig.ANTHROPIC_KEY.takeIf { it.isNotBlank() }?.let { com.bittv.home.platform.ClaudeAnswers(it) })
    val speech = com.bittv.home.platform.SpeechListener(com.bittv.home.platform.AndroidSpeechEngine(context.applicationContext))
    val catalog = CatalogRepo(
        source = BuildConfig.TMDB_KEY.takeIf { it.isNotBlank() }?.let { key -> TmdbCatalog(key) { url, auth -> httpGet(url, auth) } },
        cacheFile = File(context.filesDir, "tmdb-catalog.json"),
    )
}
