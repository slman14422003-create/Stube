package com.stube.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun libraryScroll() = scroll("library", "Bench item")

    @Test
    fun downloadsScroll() = scroll("downloads", "bench-file")

    private fun scroll(kind: String, marker: String) = rule.measureRepeated(
        packageName = targetAppId,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 10,
        setupBlock = {
            resetData()
            startActivityAndWait()
            openPanel(kind, marker)
        }
    ) {
        scrollList()
    }
}
