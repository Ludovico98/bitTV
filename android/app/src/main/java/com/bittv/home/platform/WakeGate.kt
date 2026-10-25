package com.bittv.home.platform

import android.content.Context

/**
 * The boot animation also greets a TV woken from standby. Going to sleep is remembered on disk (BitTV's process may
 * be killed in standby) and the next time BitTV comes in front plays the logo once. Not a time window after
 * SCREEN_ON: on the TCL BitTV is resumed ~200 ms before that broadcast arrives (seen on the TV, 2026-10-25).
 */
class WakeGate(private val read: () -> Boolean, private val write: (Boolean) -> Unit) {
    /** [interactive]: the screen is on right now — then this is a late broadcast after the wake, and is ignored. */
    fun markAsleep(interactive: Boolean) { if (!interactive) write(true) }

    /** A wake is waiting to be played (looked at by the Home helper; BitTV itself consumes it). */
    val pending: Boolean get() = read()

    fun consumeWake(): Boolean = read().also { if (it) write(false) }
}

fun Context.wakeGate(): WakeGate {
    val sp = getSharedPreferences("bittv_boot", Context.MODE_PRIVATE)
    return WakeGate(read = { sp.getBoolean("asleep", false) }, write = { sp.edit().putBoolean("asleep", it).commit() })
}
