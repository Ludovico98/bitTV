package com.bittv.home.boot

import com.bittv.home.ui.boot.BootMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BootMathTest {
    @Test
    fun cubesRestAtDestinationAtEnd() {
        BootMath.cubes.forEachIndexed { i, c ->
            val s = BootMath.cubeState(i, BootMath.END)
            assertTrue(s.visible)
            assertEquals("cube $i x", c.destX, s.x, 0.5f)
            assertEquals("cube $i y", c.destY, s.y, 0.5f)
        }
    }

    @Test
    fun cubeHiddenBeforeAppear() = assertFalse(BootMath.cubeState(0, 0.4f).visible)   // cube A pops in at 0.5 s

    @Test
    fun cubeVisibleOnceAppeared() = assertTrue(BootMath.cubeState(0, 0.6f).visible)

    @Test
    fun sheenOnlyDuringSweep() {
        assertNull(BootMath.sheenProgress(3.5f))
        assertNotNull(BootMath.sheenProgress(4.0f))
        assertNull(BootMath.sheenProgress(4.7f))
    }

    @Test
    fun runsTwiceAsFastAsTheOriginalTimeline() {
        assertEquals(2f, BootMath.RATE)
        // total on screen: 4.9 s of animation at 2x, plus the 0.35 s hold on the finished logo
        assertEquals(2.8f, BootMath.totalSeconds, 0.01f)
    }

    @Test
    fun lettersRiseInStaggered() {
        val first = BootMath.letterState(0, 0.6f); val last = BootMath.letterState(4, 0.6f)
        assertTrue(first.alpha > last.alpha)
        assertEquals(0f, BootMath.letterState(4, BootMath.END).dy, 0.01f)
    }

    @Test
    fun theWordIsLowercaseBitTV() = assertEquals("bitTV", BootMath.WORD)
}
