package com.bittv.home.data.tmdb

import android.graphics.BitmapFactory
import com.bittv.home.model.Title
import com.bittv.home.model.tmdbImage
import com.bittv.home.net.httpBytes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Ambient colours from each title's real artwork: the smallest backdrop (w300, ~15 KB), decoded at 1/16 size
 * (about 19×11 px) — a few kilobytes of memory, done once per catalogue and then cached with it.
 */
object ArtColorLoader {
    suspend fun apply(titles: List<Title>): List<Title> = coroutineScope {
        val gate = Semaphore(6)
        titles.map { t ->
            async {
                val art = t.art ?: return@async t
                val bytes = gate.withPermit { httpBytes(tmdbImage(art, 300)) } ?: return@async t
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 16 })
                    ?: return@async t
                val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                bmp.recycle()
                val (c1, c2) = ArtColors.from(px, bmp.width, bmp.height)
                t.copy(c1 = c1, c2 = c2)
            }
        }.awaitAll()
    }
}
