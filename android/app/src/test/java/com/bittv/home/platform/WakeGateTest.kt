package com.bittv.home.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeGateTest {
    private var stored = false
    private var wokeAt = 0L
    private fun gate() = WakeGate(read = { stored }, write = { stored = it }, readWoke = { wokeAt }, writeWoke = { wokeAt = it })

    @Test fun consumeWithoutMarkIsFalse() = assertFalse(gate().consumeWake(now = 1))

    @Test fun markThenConsumeIsTrueOnce() {
        val g = gate()
        g.markAsleep(interactive = false)
        assertTrue(g.consumeWake(now = 1))
        assertFalse(g.consumeWake(now = 1))
    }

    @Test fun markTwiceIsOneWake() {
        val g = gate()
        g.markAsleep(false); g.markAsleep(false)
        assertTrue(g.consumeWake(now = 1))
        assertFalse(g.consumeWake(now = 1))
    }

    // BitTV's process may be killed during standby: the wake is still there for the new one
    @Test fun survivesANewInstanceOverTheSameStore() {
        gate().markAsleep(false)
        assertTrue(gate().consumeWake(now = 1))
    }

    // a SCREEN_OFF broadcast delivered late, after the TV is awake again, must not arm the logo for the next return
    @Test fun markWhileInteractiveIsIgnored() {
        val g = gate()
        g.markAsleep(interactive = true)
        assertFalse(g.consumeWake(now = 1))
    }

    // the Home helper only looks (to bring BitTV in front); BitTV itself consumes the wake
    @Test fun pendingSeesAMarkWithoutConsumingIt() {
        val g = gate()
        assertFalse(g.pending)
        g.markAsleep(false)
        assertTrue(g.pending)
        assertTrue(g.consumeWake(now = 1))
        assertFalse(g.pending)
    }

    // the TV woke into something else (cast, the PS5) and BitTV came back hours later: no logo then
    @Test fun aWakeNeverConsumedExpires() {
        val g = gate()
        g.markAsleep(false); g.markAwake(now = 1_000)
        assertFalse(g.consumeWake(now = 1_000 + WakeGate.WINDOW_MS + 1))
        assertFalse(g.pending)
    }

    @Test fun consumedSoonAfterTheWakePlays() {
        val g = gate()
        g.markAsleep(false); g.markAwake(now = 1_000)
        assertTrue(g.consumeWake(now = 1_300))
    }

    // on the TCL BitTV resumes before SCREEN_ON arrives: no wake time yet still plays
    @Test fun resumedBeforeScreenOnStillPlays() {
        val g = gate()
        g.markAsleep(false)
        assertTrue(g.consumeWake(now = 5_000))
    }

    // a wake time from before a reboot (elapsed time restarts at 0) counts as none
    @Test fun aWakeTimeFromBeforeARebootIsIgnored() {
        val g = gate()
        g.markAsleep(false); wokeAt = 9_000_000
        assertTrue(g.consumeWake(now = 2_000))
    }

    // BitTV recreated while the TV is still in standby must leave the wake for the real one
    @Test fun consumeWhileTheScreenIsOffKeepsIt() {
        val g = gate()
        g.markAsleep(false)
        assertFalse(g.consumeWake(now = 1_000, interactive = false))
        assertTrue(g.consumeWake(now = 2_000))
    }

    // the next standby starts over: an old wake time must not expire the new one
    @Test fun goingToSleepAgainClearsTheOldWakeTime() {
        val g = gate()
        g.markAwake(now = 1_000)
        g.markAsleep(false)
        assertTrue(g.consumeWake(now = 1_000_000))
    }
}
