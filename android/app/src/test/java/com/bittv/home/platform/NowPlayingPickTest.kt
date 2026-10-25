package com.bittv.home.platform

import com.bittv.home.model.NowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingPickTest {
    private fun s(pkg: String, playing: Boolean, title: String? = "Song", artist: String? = "Artist", pos: Long = 10_000, updated: Long = 1_000,
                  dur: Long = 200_000, speed: Float = 1f) = SessionSnapshot(pkg, playing, title, artist, dur, pos, updated, speed, art = null)

    @Test fun spotifyPlayingIsShownAsItIs() {
        // what the TV reported on 2026-10-25: Spotify PLAYING "S.A.L.M.O." by Salmo, Netflix and NOW stopped
        val np = NowPlayingPick.pick(listOf(
            s("com.netflix.ninja", false, title = null, artist = null),
            s("com.spotify.tv.android", true, "S.A.L.M.O.", "Salmo", pos = 124_774, updated = 95_478_056, dur = 210_000),
            s("com.bskyb.nowtv.beta", false, title = null, artist = null),
        ), now = 95_478_056)
        assertEquals(NowPlaying("S.A.L.M.O.", "Salmo", 210, 124, true, art = null, source = "com.spotify.tv.android"), np)
    }

    @Test fun positionAdvancesWhilePlaying() {
        val np = NowPlayingPick.pick(listOf(s("com.spotify.tv.android", true, pos = 10_000, updated = 1_000)), now = 31_000)!!
        assertEquals(40, np.positionSec)
    }

    @Test fun pausedPositionStaysPut() {
        val np = NowPlayingPick.pick(listOf(s("com.spotify.tv.android", false, pos = 10_000, updated = 1_000)), now = 31_000)!!
        assertEquals(10, np.positionSec); assertEquals(false, np.playing)
    }

    @Test fun pausedSpotifyBeatsNothingButPlayingMusicElsewhereWins() {
        val np = NowPlayingPick.pick(listOf(s("com.spotify.tv.android", false, "A"), s("com.other.music", true, "B")), now = 1_000)!!
        assertEquals("B", np.track)
        val np2 = NowPlayingPick.pick(listOf(s("com.spotify.tv.android", true, "A"), s("com.other.music", true, "B")), now = 1_000)!!
        assertEquals("A", np2.track)
    }

    @Test fun sessionsWithoutATitleAreIgnored() {
        assertNull(NowPlayingPick.pick(listOf(s("com.netflix.ninja", false, title = null)), now = 0))
        assertNull(NowPlayingPick.pick(emptyList(), now = 0))
    }

    @Test fun positionNeverPassesTheEnd() {
        val np = NowPlayingPick.pick(listOf(s("com.spotify.tv.android", true, pos = 190_000, updated = 0, dur = 200_000)), now = 60_000)!!
        assertEquals(200, np.positionSec)
    }
}
