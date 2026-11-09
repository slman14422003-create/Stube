package com.nova.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object Perf {
    private lateinit var app: Context
    fun init(c: Context) { app = c.applicationContext }
    fun context(): Context = app
    fun asset(name: String): String = app.assets.open(name).bufferedReader().use { it.readText() }
}

/**
 * تكيّف ذكي مع حرارة الجهاز والبطارية.
 * level: 0 = أداء كامل، 1 = مخفَّف (حرارة متوسطة أو توفير الطاقة)، 2 = أدنى استهلاك (حرارة عالية).
 * يُستخدم لتقصير الأنيميشن، تخفيض معدل التحديث إلى 60Hz، وتحرير التبويبات الخلفية.
 */
object Adaptive {
    var level by mutableIntStateOf(0); private set
    private var thermal = PowerManager.THERMAL_STATUS_NONE
    private var saver = false
    private var lowBat = false      // بطارية ≤15% وغير موصولة بالشاحن
    private var started = false

    private fun recompute() {
        level = if (!Prefs.adaptive) 0 else when {
            thermal >= PowerManager.THERMAL_STATUS_SEVERE -> 2
            thermal >= PowerManager.THERMAL_STATUS_MODERATE || saver || lowBat -> 1
            else -> 0
        }
    }

    fun refresh() = recompute()

    private fun readBattery(app: Context) {
        val i = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return   // بث لاصق: لا مستمع دائم
        val lv = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
        lowBat = lv >= 0 && sc > 0 && lv * 100 / sc <= 15 && !plugged
    }

    fun init(c: Context) {
        if (started) return
        started = true
        val app = c.applicationContext
        val pm = app.getSystemService(PowerManager::class.java) ?: return
        saver = pm.isPowerSaveMode
        readBattery(app)
        thermal = pm.currentThermalStatus
        runCatching { pm.addThermalStatusListener(ContextCompat.getMainExecutor(app)) { s -> thermal = s; recompute() } }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, i: Intent?) { saver = pm.isPowerSaveMode; readBattery(app); recompute() }
        }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
            addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        recompute()
    }

    /** مدة أنيميشن مناسبة للحالة الحالية (صفر عند الحرارة العالية أو إيقاف الحركة). */
    fun ms(base: Int): Int = when {
        !Prefs.smoothAnim -> 0
        level == 0 -> base
        level == 1 -> base * 6 / 10
        else -> 0
    }

    /** عدد التبويبات الحيّة المسموح به: يقلّ كلما سخن الجهاز. */
    fun liveCap(base: Int): Int = when (level) { 0 -> base; 1 -> (base - 1).coerceAtLeast(2); else -> 2 }
}

/**
 * مصدر واحد لكل حركات التطبيق: منحنيات Material "Emphasized" (دخول سريع وهبوط ناعم)،
 * وكلها تمر عبر Adaptive.ms فتقصر أو تتوقف تلقائياً عند السخونة أو إيقاف الحركة.
 * الخصائص getters عمداً لأن Adaptive.level يتغير أثناء التشغيل.
 */
object NovaMotion {
    val emphasized = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    val emphasizedAccel = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    val panelEnter: EnterTransition
        get() = slideInVertically(tween(Adaptive.ms(420), easing = emphasized)) { it / 4 } +
            scaleIn(tween(Adaptive.ms(420), easing = emphasized), initialScale = 0.985f) +
            fadeIn(tween(Adaptive.ms(240), easing = LinearOutSlowInEasing))
    val panelExit: ExitTransition
        get() = slideOutVertically(tween(Adaptive.ms(300), easing = emphasized)) { it / 5 } +
            scaleOut(tween(Adaptive.ms(300), easing = emphasized), targetScale = 0.99f) +
            fadeOut(tween(Adaptive.ms(200)))

    fun axisEnter(forward: Boolean): EnterTransition {
        val s = (if (forward) 1 else -1) * (if (I18n.isEnglish()) 1 else -1)
        return slideInHorizontally(tween(Adaptive.ms(380), easing = emphasized)) { s * it / 3 } +
            fadeIn(tween(Adaptive.ms(260), delayMillis = Adaptive.ms(40), easing = LinearOutSlowInEasing))
    }
    fun axisExit(forward: Boolean): ExitTransition {
        val s = (if (forward) -1 else 1) * (if (I18n.isEnglish()) 1 else -1)
        return slideOutHorizontally(tween(Adaptive.ms(380), easing = emphasized)) { s * it / 4 } +
            fadeOut(tween(Adaptive.ms(200)))
    }

    val barEnter: EnterTransition
        get() = slideInVertically(tween(Adaptive.ms(320), easing = emphasized)) { -it } +
            fadeIn(tween(Adaptive.ms(200)))
    val barExit: ExitTransition
        get() = slideOutVertically(tween(Adaptive.ms(260), easing = emphasized)) { -it } +
            fadeOut(tween(Adaptive.ms(140)))
}

/**
 * تأثير ضغط ناعم (ينكمش قليلاً ثم يرتد بنابض). يعمل على طبقة الرسم فقط فلا يعيد التخطيط.
 * عند إيقاف الحركة لا يتحرك شيء.
 */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.94f): Modifier {
    val down by source.collectIsPressedAsState()
    val s: State<Float> = animateFloatAsState(
        if (down && Adaptive.ms(100) > 0) pressed else 1f,
        if (down) spring(dampingRatio = 1f, stiffness = 900f) else spring(dampingRatio = 0.7f, stiffness = 420f), label = "press"
    )
    return this.graphicsLayer { scaleX = s.value; scaleY = s.value }
}
