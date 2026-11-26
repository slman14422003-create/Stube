package com.stube.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start only: also feeds the startup profile (DEX layout). */
    @Test
    fun startup() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        resetData()
        pressHome()
        startActivityAndWait()
    }

    /** Library, Downloads, Passwords and Settings: open, scroll, navigate back. */
    @Test
    fun panels() = rule.collect(packageName = targetAppId) {
        resetData()
        startActivityAndWait()
        for ((kind, marker) in listOf("library" to "Bench item", "downloads" to "bench-file", "passwords" to "bench")) {
            openPanel(kind, marker)
            scrollList()
            device.pressBack()
            device.waitForIdle()
        }
        openPanel("settings", null)
        scrollList()
        device.pressBack()
        device.waitForIdle()
    }
}
