package com.bittv.home.boot

import com.bittv.home.ui.boot.BootGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootGateTest {
    @Test
    fun playsOncePerDeviceBootNotOnEveryProcessStart() {
        val store = mutableMapOf<String, Int>()
        val gate = BootGate(read = { store["boot"] }, write = { store["boot"] = it })
        assertTrue(gate.shouldPlay(bootCount = 5))
        assertFalse(gate.shouldPlay(bootCount = 5))   // launcher killed by Android while Netflix played, then restarted
        assertTrue(gate.shouldPlay(bootCount = 6))    // the TV was switched off and on
    }

    @Test
    fun unknownBootCountPlaysOnlyOnFirstStart() {
        val store = mutableMapOf<String, Int>()
        val gate = BootGate(read = { store["boot"] }, write = { store["boot"] = it })
        assertTrue(gate.shouldPlay(bootCount = null))
        assertFalse(gate.shouldPlay(bootCount = null))
    }
}
