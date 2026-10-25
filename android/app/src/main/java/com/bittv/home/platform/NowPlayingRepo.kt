package com.bittv.home.platform

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.bittv.home.model.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Exists only so BitTV may read the media sessions (Android grants that to notification listeners). It reads no
 * notifications. Granted once with: adb shell cmd notification allow_listener com.bittv.home/com.bittv.home.platform.MediaListener
 */
class MediaListener : NotificationListenerService()

/**
 * What is playing on the TV (Spotify first), from the system's media sessions, kept current by their callbacks while
 * Home is in front ([start]/[stop]). Without the listener permission it stays null and the card says nothing plays.
 */
class NowPlayingRepo(private val context: Context) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = publish()
    }
    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list.orEmpty()) }

    fun start() {
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
            watch(manager.getActiveSessions(component))
        } catch (e: SecurityException) { _state.value = null }   // listener permission not granted
    }

    fun stop() {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controllers.forEach { it.unregisterCallback(callback) }; controllers = emptyList()
    }

    private fun watch(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        list.forEach { it.registerCallback(callback, main) }
        publish()
    }

    private fun publish() {
        _state.value = NowPlayingPick.pick(controllers.map { c ->
            val m = c.metadata; val p = c.playbackState
            SessionSnapshot(
                pkg = c.packageName, playing = p?.state == PlaybackState.STATE_PLAYING,
                title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.description?.title?.toString(),
                artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.description?.subtitle?.toString(),
                durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
                positionMs = p?.position ?: 0, updatedAt = p?.lastPositionUpdateTime ?: 0, speed = p?.playbackSpeed ?: 1f,
                art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: m?.getString(MediaMetadata.METADATA_KEY_ART_URI),
            )
        }, now = SystemClock.elapsedRealtime())
    }

    /** prev / toggle / next on the session shown; false when nothing is playing anywhere (the caller opens Spotify). */
    fun command(cmd: String): Boolean {
        val shown = _state.value?.source
        val c = controllers.firstOrNull { it.packageName == shown } ?: controllers.firstOrNull { it.packageName == NowPlayingPick.SPOTIFY }
            ?: return false
        val t = c.transportControls
        when (cmd) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            else -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
        return true
    }
}
