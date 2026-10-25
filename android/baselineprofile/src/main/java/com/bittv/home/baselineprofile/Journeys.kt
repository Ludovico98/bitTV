package com.bittv.home.baselineprofile

import android.content.ComponentName
import android.content.Intent
import android.view.KeyEvent
import androidx.benchmark.macro.MacrobenchmarkScope

const val PACKAGE = "com.bittv.home"

/** Start Home straight away (the boot animation is skipped so it doesn't hide what is measured). */
fun MacrobenchmarkScope.startHome() {
    startActivityAndWait(Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE.MainActivity")).putExtra("skipBoot", true))
}

private fun MacrobenchmarkScope.key(code: Int, times: Int = 1, gapMs: Long = 120) =
    repeat(times) { device.pressKeyCode(code); Thread.sleep(gapMs) }

/** The everyday journey: down through every row, along two rows, into Search and back. */
fun MacrobenchmarkScope.browseHome() {
    key(KeyEvent.KEYCODE_DPAD_DOWN, 10)
    key(KeyEvent.KEYCODE_DPAD_UP, 8)
    key(KeyEvent.KEYCODE_DPAD_RIGHT, 10)
    key(KeyEvent.KEYCODE_DPAD_DOWN)
    key(KeyEvent.KEYCODE_DPAD_RIGHT, 10)
    key(KeyEvent.KEYCODE_DPAD_UP, 4)
    device.waitForIdle()
}

fun MacrobenchmarkScope.openAndCloseSearch() {
    key(KeyEvent.KEYCODE_DPAD_UP, 3)
    key(KeyEvent.KEYCODE_DPAD_LEFT, 5)
    key(KeyEvent.KEYCODE_DPAD_CENTER, gapMs = 600)
    key(KeyEvent.KEYCODE_DPAD_RIGHT, 4)
    key(KeyEvent.KEYCODE_BACK, gapMs = 600)
    device.waitForIdle()
}
