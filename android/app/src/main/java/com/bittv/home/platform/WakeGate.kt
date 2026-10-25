package com.bittv.home.platform

import android.content.Context

/**
 * The boot animation also greets a TV woken from standby. Going to sleep is remembered on disk (BitTV's process may
 * be killed in standby) and the next time BitTV comes in front plays the logo once. Not a time window that opens on
 * SCREEN_ON: on the TCL BitTV is resumed ~200 ms before that broadcast arrives (seen on the TV, 2026-10-25). The
 * window closes instead: a wake BitTV doesn't come back to within [WINDOW_MS] of SCREEN_ON (the TV woke into a
 * cast or the PS5) is dropped, so Home hours later plays nothing. Times are elapsedRealtime.
 */
class WakeGate(
    private val read: () -> Boolean, private val write: (Boolean) -> Unit,
    private val readWoke: () -> Long, private val writeWoke: (Long) -> Unit,
) {
    /** [interactive]: the screen is on right now — then this is a late broadcast after the wake, and is ignored. */
    fun markAsleep(interactive: Boolean) { if (!interactive) { write(true); writeWoke(0) } }

    /** SCREEN_ON: the wake's window starts. */
    fun markAwake(now: Long) { if (read()) writeWoke(now) }

    /** A wake is waiting to be played. */
    val pending: Boolean get() = read()

    /** True once per standby, while the screen is on and (if SCREEN_ON was seen) within [WINDOW_MS] of it. */
    fun consumeWake(now: Long, interactive: Boolean = true): Boolean {
        if (!interactive || !read()) return false
        write(false)
        val woke = readWoke()
        // no SCREEN_ON seen yet (0, or a time from before a reboot: later than now) counts as just now
        return woke == 0L || woke > now || now - woke <= WINDOW_MS
    }

    companion object { const val WINDOW_MS = 60_000L }
}

fun Context.wakeGate(): WakeGate {
    val sp = getSharedPreferences("bittv_boot", Context.MODE_PRIVATE)
    return WakeGate(read = { sp.getBoolean("asleep", false) }, write = { sp.edit().putBoolean("asleep", it).commit() },
        readWoke = { sp.getLong("wokeAt", 0) }, writeWoke = { sp.edit().putLong("wokeAt", it).commit() })
}
