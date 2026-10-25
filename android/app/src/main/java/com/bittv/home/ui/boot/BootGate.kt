package com.bittv.home.ui.boot

/**
 * The boot animation belongs to switching the TV on, not to every start of BitTV's process: on a 2 GB TV Android
 * often kills the launcher while a streaming app plays, and coming back must not replay the logo.
 * [bootCount] is Settings.Global.BOOT_COUNT (null if the firmware hides it: then only the very first start plays).
 */
class BootGate(private val read: () -> Int?, private val write: (Int) -> Unit) {
    fun shouldPlay(bootCount: Int?): Boolean {
        val current = bootCount ?: 0
        if (read() == current) return false
        write(current)
        return true
    }
}
