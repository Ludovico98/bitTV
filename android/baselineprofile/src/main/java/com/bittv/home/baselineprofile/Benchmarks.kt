package com.bittv.home.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start to Home's first frame (spec §7: ≤ 900 ms on the emulator, ≤ 1.2 s on the TV). */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun startupNoCompilation() = startup(CompilationMode.None())
    @Test fun startupBaselineProfile() = startup(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(StartupTimingMetric()), compilationMode = mode,
        startupMode = StartupMode.COLD, iterations = 10,
    ) { startHome() }
}

/** Frame times while browsing Home with the remote (spec §7: P90 ≤ 16 ms, P99 ≤ 33 ms). */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun browseNoCompilation() = browse(CompilationMode.None())
    @Test fun browseBaselineProfile() = browse(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun browse(mode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()), compilationMode = mode,
        startupMode = StartupMode.WARM, iterations = 5, setupBlock = { startHome() },
    ) { browseHome() }
}
