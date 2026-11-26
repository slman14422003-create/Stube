package com.nova.browser

import android.app.Activity
import kotlin.math.abs

/**
 * ضبط معدل تحديث الشاشة: أعلى معدل مدعوم بنفس الدقة لتمرير وحركة أنعم،
 * أو 60Hz عند السخونة/توفير الطاقة إن كان خيار «تحديد 60Hz عند السخونة» مفعّلاً (Prefs.cap60).
 * إيقاف خيار الأنيميشن يعيد القرار للنظام. لا يغيّر الدقة أبداً، ويتجاهل أي خطأ من الشاشة.
 */
object HighRefresh {
    fun apply(a: Activity) {
        runCatching {
            val d = a.display ?: return
            val cur = d.mode
            val same = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            if (same.isEmpty()) return
            val id = when {
                !Prefs.smoothAnim -> 0
                Adaptive.level >= 1 && Prefs.cap60 -> same.minByOrNull { abs(it.refreshRate - 60f) }?.modeId ?: 0
                else -> same.maxByOrNull { it.refreshRate }?.modeId ?: 0
            }
            val lp = a.window.attributes
            if (lp.preferredDisplayModeId != id) {
                lp.preferredDisplayModeId = id
                a.window.attributes = lp
            }
        }
    }
}
