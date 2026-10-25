package com.bittv.home.data.tmdb

/** The two ambient colours of a piece of artwork: the average of its left and right halves, darkened for white text. */
object ArtColors {
    fun from(pixels: IntArray, width: Int, height: Int): Pair<Long, Long> {
        fun avg(x0: Int, x1: Int): Long {
            var r = 0L; var g = 0L; var b = 0L; var n = 0
            for (y in 0 until height) for (x in x0 until x1) {
                val p = pixels[y * width + x]; r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF; n++
            }
            if (n == 0) return 0xFF202530
            fun ch(v: Long) = (v / n * 6 / 10).coerceIn(0, 255)   // 60 %: keeps the background behind white text
            return (0xFFL shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
        val mid = width / 2
        return if (mid == 0) avg(0, width).let { it to it } else avg(0, mid) to avg(mid, width)
    }
}
