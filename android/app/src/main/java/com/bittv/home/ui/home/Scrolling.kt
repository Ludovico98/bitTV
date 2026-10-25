package com.bittv.home.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Horizontal rows: keep [startPad] of room on the left (for the focus glow) and [peek] on the right so the next
 * tile stays cut off "for a sense of continuation". Moves only when the focused tile would leave that window.
 */
@OptIn(ExperimentalFoundationApi::class)
class RowBringIntoView(private val startPad: Float, private val peek: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            offset < startPad -> offset - startPad
            end > containerSize - peek -> end - (containerSize - peek)
            else -> 0f
        }
    }
}

/**
 * How far a list is scrolled from its top: the heights of the items above the first one shown plus that one's hidden
 * part. Null while an item above was never laid out.
 */
fun pageScrolled(firstIndex: Int, firstOffset: Int, height: (Int) -> Int?): Int? {
    var d = firstOffset
    for (i in 0 until firstIndex) d += height(i) ?: return null
    return d
}

/**
 * The page's move back to its top, [scrolled] px up: exact, so the spring eases into it like any row. From further
 * than a screen (Back from deep down) it still jumps: gliding past every row composed each one on the way (frames of
 * ~150 ms on the TV), a jump only the rows it lands on. Unknown: aimed far past the top, which clamps.
 */
fun topDistance(scrolled: Int?, containerSize: Float): Float =
    if (scrolled != null && scrolled <= containerSize) -scrolled.toFloat() else -100_000f
