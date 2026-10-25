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
