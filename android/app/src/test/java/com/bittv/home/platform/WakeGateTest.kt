package com.bittv.home.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeGateTest {
    private var stored = false
    private fun gate() = WakeGate(read = { stored }, write = { stored = it })

    @Test fun consumeWithoutMarkIsFalse() = assertFalse(gate().consumeWake())

    @Test fun markThenConsumeIsTrueOnce() {
        val g = gate()
        g.markAsleep(interactive = false)
        assertTrue(g.consumeWake())
        assertFalse(g.consumeWake())
    }

    @Test fun markTwiceIsOneWake() {
        val g = gate()
        g.markAsleep(false); g.markAsleep(false)
        assertTrue(g.consumeWake())
        assertFalse(g.consumeWake())
    }

    // BitTV's process may be killed during standby: the wake is still there for the new one
    @Test fun survivesANewInstanceOverTheSameStore() {
        gate().markAsleep(false)
        assertTrue(gate().consumeWake())
    }

    // a SCREEN_OFF broadcast delivered late, after the TV is awake again, must not arm the logo for the next return
    @Test fun markWhileInteractiveIsIgnored() {
        val g = gate()
        g.markAsleep(interactive = true)
        assertFalse(g.consumeWake())
    }

    // the Home helper only looks (to bring BitTV in front); BitTV itself consumes the wake
    @Test fun pendingSeesAMarkWithoutConsumingIt() {
        val g = gate()
        assertFalse(g.pending)
        g.markAsleep(false)
        assertTrue(g.pending)
        assertTrue(g.consumeWake())
        assertFalse(g.pending)
    }
}
