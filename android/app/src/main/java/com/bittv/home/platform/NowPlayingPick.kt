package com.bittv.home.platform

import com.bittv.home.model.NowPlaying

/** One media session as the system reports it (times in ms; [updatedAt] on the elapsed-realtime clock). */
data class SessionSnapshot(
    val pkg: String, val playing: Boolean, val title: String?, val artist: String?,
    val durationMs: Long, val positionMs: Long, val updatedAt: Long, val speed: Float, val art: Any?,
)

/** Which session the active section shows, and where it is now. Pure, so it is tested on the JVM. */
object NowPlayingPick {
    const val SPOTIFY = "com.spotify.tv.android"

    /** Playing beats paused; among equals Spotify comes first; sessions without a title (video apps idle) are skipped. */
    fun pick(sessions: List<SessionSnapshot>, now: Long): NowPlaying? {
        val s = sessions.filter { !it.title.isNullOrBlank() }
            .sortedWith(compareByDescending<SessionSnapshot> { it.playing }.thenByDescending { it.pkg == SPOTIFY })
            .firstOrNull() ?: return null
        val pos = if (s.playing) s.positionMs + ((now - s.updatedAt) * s.speed).toLong() else s.positionMs
        val durSec = (s.durationMs / 1000).toInt().coerceAtLeast(0)
        val posSec = (pos / 1000).toInt().coerceAtLeast(0).let { if (durSec > 0) it.coerceAtMost(durSec) else it }
        return NowPlaying(s.title!!, s.artist.orEmpty(), durSec, posSec, s.playing, s.art, s.pkg)
    }
}
