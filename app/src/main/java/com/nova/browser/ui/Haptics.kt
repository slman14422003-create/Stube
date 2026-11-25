package com.nova.browser

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * مصدر واحد للاهتزاز الخفيف في الواجهة (بدل استدعاءات متفرقة).
 * يحترم خيار «اهتزاز خفيف» الموجود في الإعدادات (Prefs.pwaHaptics).
 * الثوابت متاحة من API 34 وminSdk للتطبيق 35.
 */
class Haptics(private val view: View) {
    private fun go(c: Int) { if (Prefs.pwaHaptics) runCatching { view.performHapticFeedback(c) } }
    fun tick() = go(HapticFeedbackConstants.CLOCK_TICK)
    fun segment() = go(HapticFeedbackConstants.SEGMENT_TICK)
    fun toggle(on: Boolean) = go(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
    fun confirm() = go(HapticFeedbackConstants.CONFIRM)
    fun reject() = go(HapticFeedbackConstants.REJECT)
}

@Composable
fun rememberHaptics(): Haptics {
    val v = LocalView.current
    return remember(v) { Haptics(v) }
}
