package com.bittv.home.platform

import com.bittv.home.data.tmdb.TmdbParse
import com.bittv.home.model.Svc
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Base64

/**
 * Each service's own page for a title, so "Play on …" opens the title in the app rather than the app's home.
 * Read from TMDB's public "where to watch" page (JustWatch data): every offer link carries the provider and the
 * offer type, and points at the service's page (netflix.com/title/…, app.primevideo.com/detail?gti=…).
 * Subscription, free and with-ads offers only; rentals, purchases and "channels" inside other apps are ignored.
 */
object WatchLinks {
    private val ANCHOR = Regex("""href="(https?://click\.justwatch\.com/a\?[^"]+)"""")
    private val WATCH = setOf("flatrate", "free", "ads")

    /** TMDB's watch page for a catalogue title id ("tv66732", "mv603"); null for demo titles. */
    fun pageUrl(titleId: String, svc: Svc): String? {
        val kind = when { titleId.startsWith("tv") -> "tv"; titleId.startsWith("mv") -> "movie"; else -> return null }
        val id = titleId.drop(2).toIntOrNull() ?: return null
        val locale = if (svc == Svc.LA7 || svc == Svc.RAIPLAY) "IT" else "GB"
        return "https://www.themoviedb.org/$kind/$id/watch?locale=$locale"
    }

    fun parse(html: String): Map<Svc, String> {
        val out = LinkedHashMap<Svc, String>()
        for (m in ANCHOR.findAll(html)) {
            val params = m.groupValues[1].replace("&amp;", "&").substringAfter('?').split('&')
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
            val cx = params["cx"]?.let { c ->
                runCatching { JSONObject(String(Base64.getUrlDecoder().decode(c.trimEnd('=')))).getJSONArray("data").getJSONObject(0).getJSONObject("data") }
                    .getOrNull()
            } ?: continue
            val provider = cx.optString("provider")
            if (cx.optString("monetizationType") !in WATCH || provider.contains("channel", ignoreCase = true)) continue
            val svc = TmdbParse.svcFor(provider) ?: continue
            val target = params["r"]?.let(::clean) ?: continue
            out.putIfAbsent(svc, target)
        }
        return out
    }

    /** Unwraps affiliate redirects, turns Amazon links into the form the TV app opens, drops tracking parameters. */
    private fun clean(url: String): String? {
        var u = url
        Regex("""[?&]u=(https?%3A[^&]+)""").find(u)?.let { u = URLDecoder.decode(it.groupValues[1], "UTF-8") }
        Regex("""[?&]gti=([^&]+)""").find(u)?.takeIf { "amazon" in u || "primevideo" in u }?.let {
            return "https://app.primevideo.com/detail?gti=${it.groupValues[1]}"
        }
        u = u.substringBefore('?')
        return u.takeIf { it.count { c -> c == '/' } > 3 }   // a bare home page (https://tv.apple.com) is no use
    }
}

/** The boot animation also greets a TV woken from standby — when BitTV is what comes back on screen. */
object WakeGate {
    const val WINDOW_MS = 5_000L
    fun playOnResume(lastScreenOn: Long, now: Long): Boolean = lastScreenOn > 0 && now - lastScreenOn in 0..WINDOW_MS
}
