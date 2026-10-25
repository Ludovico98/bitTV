package com.bittv.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.bittv.home.ui.components.FocusTile
import com.bittv.home.ui.components.LocalMotion
import com.bittv.home.ui.components.Motion
import com.bittv.home.ui.components.TileScale
import com.bittv.home.ui.components.TileSwaying
import androidx.compose.foundation.focusable
import androidx.compose.ui.test.assertIsFocused
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FocusTileTest {
    @get:Rule val rule = createComposeRule()

    private fun scaleIs(v: Float) = SemanticsMatcher.expectValue(TileScale, v)

    private fun setTile(motion: Motion) {
        rule.setContent {
            CompositionLocalProvider(LocalMotion provides motion) {
                val fr = remember { FocusRequester() }
                FocusTile(onClick = {}, width = 160.dp, height = 90.dp, modifier = Modifier.testTag("tile").focusRequester(fr)) { Box(Modifier) }
                LaunchedEffect(Unit) { fr.requestFocus() }
            }
        }
    }

    @Test
    fun focusedTileScalesTo110() {
        setTile(Motion(enabled = true))
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag("tile").assert(scaleIs(1.10f))
    }

    @Test
    fun animationsOffMakesScaleInstant() {
        rule.mainClock.autoAdvance = false
        setTile(Motion(enabled = false))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag("tile").assert(scaleIs(1.10f))
    }

    @Test
    fun animatedScaleIsMidwayAfterOneFrame() {
        rule.mainClock.autoAdvance = false
        setTile(Motion(enabled = true))
        rule.mainClock.advanceTimeBy(64)   // focus lands a frame after composition; the 160 ms tween is under way
        rule.onNodeWithTag("tile").assert(SemanticsMatcher("scale between 1 and 1.1") { n ->
            val s = n.config.getOrElse(TileScale) { 0f }; s > 1f && s < 1.10f
        })
    }

    @Test
    fun swaySettlesSoAnIdleScreenStopsRedrawing() {
        rule.mainClock.autoAdvance = false
        setTile(Motion(enabled = true))
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("tile").assert(SemanticsMatcher.expectValue(TileSwaying, true))
        rule.mainClock.advanceTimeBy(9_500)                    // two 4.5 s cycles, then it settles
        rule.onNodeWithTag("tile").assert(SemanticsMatcher.expectValue(TileSwaying, false))
    }

    // the banner's dots are a focus stop inside its tile: the sway starting or ending must not rebuild what is inside
    // (that dropped focus from the dots, and the next press went into the app bar)
    @Test
    fun whatIsInsideKeepsItsFocusAndStateThroughTheSway() {
        rule.mainClock.autoAdvance = false
        var built = 0
        rule.setContent {
            CompositionLocalProvider(LocalMotion provides Motion(enabled = true)) {
                val inner = remember { FocusRequester() }
                FocusTile(onClick = {}, width = 160.dp, height = 90.dp, lit = true) {
                    LaunchedEffect(Unit) { built++ }
                    Box(Modifier.testTag("inner").focusRequester(inner).focusable())
                }
                LaunchedEffect(Unit) { inner.requestFocus() }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("inner", useUnmergedTree = true).assertIsFocused()
        rule.mainClock.advanceTimeBy(10_000)   // the sway has ended
        rule.onNodeWithTag("inner", useUnmergedTree = true).assertIsFocused()
        assertEquals(1, built)
    }
}
