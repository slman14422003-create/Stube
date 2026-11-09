package com.nova.browser

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** إعدادات كل تطبيق مثبّت على حدة (مفتاحها النطاق) — تُحفظ في ملف الإعدادات الموحّد. */
object AppPrefs {
    const val ORIENT_AUTO = 0
    const val ORIENT_PORTRAIT = 1
    const val ORIENT_LANDSCAPE = 2

    private fun c(ctx: Context) = ConfStore.open(ctx)

    fun orient(ctx: Context, host: String): Int = c(ctx).getInt("pwa_o_$host", ORIENT_AUTO)
    fun setOrient(ctx: Context, host: String, v: Int) = c(ctx).putInt("pwa_o_$host", v)

    fun keepAwake(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_k_$host", false)
    fun setKeepAwake(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_k_$host", v)

    fun immersive(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_i_$host", false)
    fun setImmersive(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_i_$host", v)

    fun pull(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_r_$host", true)
    fun setPull(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_r_$host", v)

    /** الوضع المختار (AppModes) أو -1 عند تعديل خيار يدوياً بعد اختيار وضع. */
    fun mode(ctx: Context, host: String): Int = c(ctx).getInt("pwa_m_$host", AppModes.NORMAL)
    fun setMode(ctx: Context, host: String, v: Int) = c(ctx).putInt("pwa_m_$host", v)

    /** إخفاء Shorts لهذا التطبيق؛ إن لم يُضبط صراحةً يتبع إعداد يوتيوب العام. */
    fun shorts(ctx: Context, host: String): Boolean {
        val s = c(ctx)
        return if (s.contains("pwa_s_$host")) s.getBoolean("pwa_s_$host", false) else Prefs.ytNoShorts
    }
    fun setShorts(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_s_$host", v)

    fun focus(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_f_$host", false)
    fun setFocus(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_f_$host", v)

    /** سقف الجودة: 0 تلقائي، 1=360p، 2=480p، 3=720p، 4=1080p — على بيانات الجوال وعلى Wi-Fi. */
    fun qMobile(ctx: Context, host: String): Int = c(ctx).getInt("pwa_qm_$host", 0).coerceIn(0, 4)
    fun qWifi(ctx: Context, host: String): Int = c(ctx).getInt("pwa_qw_$host", 0).coerceIn(0, 4)
    fun setQuality(ctx: Context, host: String, mobile: Int, wifi: Int) {
        c(ctx).putInt("pwa_qm_$host", mobile)
        c(ctx).putInt("pwa_qw_$host", wifi)
    }

    fun dim(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_d_$host", false)
    fun setDim(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_d_$host", v)

    fun gestures(ctx: Context, host: String): Boolean = c(ctx).getBoolean("pwa_g_$host", true)
    fun setGestures(ctx: Context, host: String, v: Boolean) = c(ctx).putBoolean("pwa_g_$host", v)

    /** ثابت اتجاه النظام المقابل للاختيار. */
    fun orientFlag(v: Int): Int = when (v) {
        ORIENT_PORTRAIT -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ORIENT_LANDSCAPE -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

/** حزمة إعدادات يطبّقها الوضع دفعة واحدة. */
class ModeBundle(
    val orient: Int = AppPrefs.ORIENT_AUTO, val awake: Boolean = false, val immersive: Boolean = false, val pull: Boolean = true,
    val shorts: Boolean = false, val focus: Boolean = false, val qMobile: Int = 0, val qWifi: Int = 0,
    val dim: Boolean = false, val gestures: Boolean = true
)

object AppModes {
    const val CUSTOM = -1
    const val NORMAL = 0
    const val SAVER = 1
    const val FOCUS = 2
    const val CINEMA = 3
    const val NIGHT = 4

    /** عادي = القيم الافتراضية (وإخفاء Shorts يتبع إعداد يوتيوب العام). */
    fun bundle(mode: Int, globalShorts: Boolean): ModeBundle = when (mode) {
        SAVER -> ModeBundle(shorts = globalShorts, qMobile = 1, qWifi = 2)
        FOCUS -> ModeBundle(shorts = true, focus = true)
        CINEMA -> ModeBundle(awake = true, immersive = true, shorts = globalShorts, qMobile = 3, qWifi = 4)
        NIGHT -> ModeBundle(dim = true, shorts = true, focus = true)
        else -> ModeBundle(shorts = globalShorts)
    }
}

/** اسم عرض مناسب للتطبيق: «YouTube» بدل m.youtube.com، وإلا الاسم المحفوظ، وإلا اسم الموقع بحرف كبير. */
fun prettyAppName(host: String, stored: String?): String {
    val h = host.removePrefix("www.")
    if (PageRules.isYtHost(h)) return "YouTube"
    val s = stored?.trim().orEmpty()
    if (s.isNotEmpty() && !s.equals(h, true) && !s.equals(host, true)) return s
    val parts = h.split('.').filter { it.isNotEmpty() && it != "m" && it != "mobile" }
    val base = if (parts.size >= 2) parts[parts.size - 2] else parts.firstOrNull() ?: h
    return base.replaceFirstChar { it.uppercase() }
}

fun fmtTime(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
}

fun qualityLabel(i: Int): String = when (i) {
    1 -> "360p"
    2 -> "480p"
    3 -> "720p"
    4 -> "1080p"
    else -> L("تلقائي")
}

/** ما تعرضه الشاشة (لقطة غير قابلة للتغيير يبنيها AppActivity في كل إعادة تركيب). */
@Immutable
class AppOpts(
    val name: String, val host: String, val icon: Bitmap?,
    val isYt: Boolean, val canAudio: Boolean, val canShareAt: Boolean, val shareAtLabel: String, val canYtApp: Boolean,
    val orient: Int, val awake: Boolean, val immersive: Boolean, val pull: Boolean,
    val mode: Int, val shorts: Boolean, val focus: Boolean, val qMobile: Int, val qWifi: Int,
    val dim: Boolean, val gestures: Boolean, val sleepMin: Int, val sleepLeftSec: Int,
    val canDownload: Boolean = false
)

class AppOptActions(
    val onOrient: () -> Unit, val onAwake: (Boolean) -> Unit, val onImmersive: (Boolean) -> Unit, val onPull: (Boolean) -> Unit,
    val onMode: (Int) -> Unit, val onShorts: (Boolean) -> Unit, val onFocus: (Boolean) -> Unit,
    val onQMobile: () -> Unit, val onQWifi: () -> Unit, val onDim: (Boolean) -> Unit, val onGestures: (Boolean) -> Unit,
    val onSleep: () -> Unit,
    val onReload: () -> Unit, val onShare: () -> Unit, val onCopy: () -> Unit, val onOpenNova: () -> Unit,
    val onShareAt: () -> Unit, val onOpenYtApp: () -> Unit, val onAudioOnly: () -> Unit, val onRemove: () -> Unit,
    val onDownload: () -> Unit = {}
)

// ───────────────────────── أيقونات مرسومة (لكل خيار أيقونته الخاصة) ─────────────────────────

enum class OptGlyph { Rotate, Bulb, Expand, Timer, NoShorts, Signal, Wifi, Focus, Moon, Film, Tube, Sun, Swipe, Clock, Compass, Down, Volume }

@Composable
fun OptIcon(kind: OptGlyph, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val sw = 2f * s
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun P(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, P(x1, y1), P(x2, y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun poly(vararg p: Float) {
            val path = Path()
            path.moveTo(p[0] * s, p[1] * s)
            var i = 2
            while (i < p.size) { path.lineTo(p[i] * s, p[i + 1] * s); i += 2 }
            drawPath(path, tint, style = st)
        }
        fun ring(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * s, P(cx, cy), style = st)
        fun dot(cx: Float, cy: Float, r: Float) = drawCircle(tint, r * s, P(cx, cy))
        fun box(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(tint, P(x, y), Size(w * s, h * s), CornerRadius(r * s), style = st)
        fun arc(x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float) = drawArc(tint, start, sweep, false, P(x, y), Size(w * s, h * s), style = st)
        when (kind) {
            OptGlyph.Rotate -> { box(4f, 9f, 16f, 10f, 2.5f); arc(6f, 1.5f, 12f, 10f, 200f, 140f); poly(18f, 1.9f, 17.6f, 4.8f, 14.9f, 4.3f) }
            OptGlyph.Bulb -> { ring(12f, 9.5f, 5.5f); line(9.5f, 18f, 14.5f, 18f); line(10.5f, 21f, 13.5f, 21f) }
            OptGlyph.Expand -> {
                poly(4f, 9f, 4f, 4f, 9f, 4f); poly(15f, 4f, 20f, 4f, 20f, 9f)
                poly(20f, 15f, 20f, 20f, 15f, 20f); poly(9f, 20f, 4f, 20f, 4f, 15f)
            }
            OptGlyph.Timer -> { ring(12f, 13.5f, 7.5f); line(12f, 13.5f, 12f, 9.5f); line(12f, 13.5f, 14.8f, 15f); line(9.5f, 3f, 14.5f, 3f) }
            OptGlyph.NoShorts -> { box(7f, 3f, 10f, 18f, 3.5f); line(4f, 4f, 20f, 20f) }
            OptGlyph.Signal -> { line(5f, 19f, 5f, 15f); line(10f, 19f, 10f, 11f); line(15f, 19f, 15f, 7f); line(20f, 19f, 20f, 3f) }
            OptGlyph.Wifi -> { arc(8f, 15f, 8f, 8f, 225f, 90f); arc(4f, 11f, 16f, 16f, 225f, 90f); arc(0f, 7f, 24f, 24f, 225f, 90f); dot(12f, 19f, 1.3f) }
            OptGlyph.Focus -> { ring(12f, 12f, 8.5f); ring(12f, 12f, 4.2f); dot(12f, 12f, 1.2f) }
            OptGlyph.Moon -> {
                val p = Path()
                p.arcTo(Rect(3f * s, 3f * s, 21f * s, 21f * s), 5f, 260f, true)
                p.arcTo(Rect(9.1f * s, 0.9f * s, 23.1f * s, 14.9f * s), 225f, -180f, false)
                p.close()
                drawPath(p, tint, style = st)
            }
            OptGlyph.Film -> {
                box(3f, 4f, 18f, 16f, 2.5f); line(8f, 4f, 8f, 20f); line(16f, 4f, 16f, 20f)
                line(3f, 9f, 8f, 9f); line(3f, 15f, 8f, 15f); line(16f, 9f, 21f, 9f); line(16f, 15f, 21f, 15f)
            }
            OptGlyph.Tube -> { box(2.5f, 5.5f, 19f, 13f, 4f); poly(10f, 9.2f, 14.8f, 12f, 10f, 14.8f, 10f, 9.2f) }
            OptGlyph.Sun -> {
                ring(12f, 12f, 4f)
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    line((12 + 7 * cos(a)).toFloat(), (12 + 7 * sin(a)).toFloat(), (12 + 9.5 * cos(a)).toFloat(), (12 + 9.5 * sin(a)).toFloat())
                }
            }
            OptGlyph.Swipe -> { line(7f, 5f, 7f, 19f); poly(4.5f, 8f, 7f, 5f, 9.5f, 8f); line(17f, 5f, 17f, 19f); poly(14.5f, 16f, 17f, 19f, 19.5f, 16f) }
            OptGlyph.Clock -> { ring(12f, 12f, 8.5f); line(12f, 7.5f, 12f, 12f); line(12f, 12f, 15.5f, 14f) }
            OptGlyph.Compass -> { ring(12f, 12f, 9f); poly(15.8f, 8.2f, 13.6f, 13.6f, 8.2f, 15.8f, 10.4f, 10.4f, 15.8f, 8.2f) }
            OptGlyph.Down -> { line(12f, 4f, 12f, 15f); poly(7.5f, 10.5f, 12f, 15f, 16.5f, 10.5f); line(5f, 20f, 19f, 20f) }
            OptGlyph.Volume -> {
                poly(4f, 9.5f, 8f, 9.5f, 12.5f, 6f, 12.5f, 18f, 8f, 14.5f, 4f, 14.5f, 4f, 9.5f)
                arc(9f, 8f, 8f, 8f, -55f, 110f); arc(5.5f, 4.5f, 15f, 15f, -55f, 110f)
            }
        }
    }
}

// ───────────────────────── مكوّنات الشاشة ─────────────────────────

@Composable
private fun QuickAction(icon: @Composable (Color) -> Unit, label: String, modifier: Modifier, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(src, 0.94f).clip(RoundedCornerShape(16.dp)).background(ios.cell)
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(ios.blue)
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = ios.label, maxLines = 2, minLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
        )
    }
}

/** مفتاح بلاطة: يمتلئ بلون التطبيق عند التفعيل (بديل قائمة المفاتيح الطويلة). */
@Composable
private fun ToggleTile(icon: @Composable (Color) -> Unit, label: String, sub: String?, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (on) ios.blue else ios.cell, tween(Adaptive.ms(200)), label = "tilebg")
    val fg by animateColorAsState(if (on) Color.White else ios.label, tween(Adaptive.ms(200)), label = "tilefg")
    Column(
        modifier.pressScale(src, 0.95f).clip(RoundedCornerShape(18.dp)).background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(fg)
        Spacer(Modifier.height(6.dp))
        Text(
            label, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 2, minLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
        )
        Text(sub ?: " ", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun ModeChip(kind: OptGlyph, label: String, selected: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (selected) ios.blue else ios.cell, tween(Adaptive.ms(200)), label = "chipbg")
    Row(
        Modifier.pressScale(src, 0.95f).clip(RoundedCornerShape(50)).background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptIcon(kind, if (selected) Color.White else ios.blue, 18.dp)
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else ios.label, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = LocalIos.current.label2,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 18.dp, bottom = 8.dp)
    )
}

private fun modeGlyph(m: Int) = when (m) {
    AppModes.SAVER -> OptGlyph.Down
    AppModes.FOCUS -> OptGlyph.Focus
    AppModes.CINEMA -> OptGlyph.Film
    AppModes.NIGHT -> OptGlyph.Moon
    else -> OptGlyph.Sun
}

private fun modeName(m: Int) = when (m) {
    AppModes.SAVER -> L("توفير البيانات")
    AppModes.FOCUS -> L("تركيز")
    AppModes.CINEMA -> L("سينما")
    AppModes.NIGHT -> L("ليلي")
    else -> L("عادي")
}

/**
 * ورقة خيارات التطبيق المثبّت: تُفتح بلمس مقبض الحافة العلوية، وتُغلق بسحب الرأس للأسفل.
 * ترتيبها: إجراءات سريعة ← مفاتيح بلاطات (اتجاه/شاشة مضاءة/ملء الشاشة/سحب للتحديث) ← الأوضاع ← خيارات يوتيوب ← عام ← إزالة.
 */
@Composable
fun AppOptionsSheet(visible: Boolean, o: AppOpts, a: AppOptActions, onDismiss: () -> Unit) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }
    val tick: () -> Unit = { if (Prefs.pwaHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(Adaptive.ms(180))), exit = fadeOut(tween(Adaptive.ms(140)))) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = visible, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(Adaptive.ms(340), easing = NovaMotion.emphasized)) { it } + fadeIn(tween(Adaptive.ms(160))),
            exit = slideOutVertically(tween(Adaptive.ms(240), easing = NovaMotion.emphasized)) { it } + fadeOut(tween(Adaptive.ms(120)))
        ) {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().graphicsLayer { translationY = drag.value }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(ios.groupedBg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            ) {
                // الرأس: مقبض + هوية التطبيق. سحبه للأسفل يغلق الورقة.
                Column(
                    Modifier.fillMaxWidth().pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (drag.value > dismissPx) { onDismiss(); delay(260); drag.snapTo(0f) }
                                    else drag.animateTo(0f, spring(stiffness = 500f))
                                }
                            },
                            onDragCancel = { scope.launch { drag.animateTo(0f, spring(stiffness = 500f)) } },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                            }
                        )
                    }
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(5.dp).clip(RoundedCornerShape(50)).background(ios.fill2))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ic = o.icon
                        if (ic != null) {
                            Image(
                                bitmap = remember(ic) { ic.asImageBitmap() }, contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(11.dp))
                            )
                        } else {
                            IosTile(ios.blue, 44.dp) { Text(o.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(L("خيارات التطبيق"), style = MaterialTheme.typography.labelSmall, color = ios.label2, maxLines = 1)
                            Text(
                                o.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ios.label,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(o.host, style = MaterialTheme.typography.bodySmall, color = ios.label2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
                    // إجراءات سريعة
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAction({ c -> IosIcon(IosGlyph.Reload, c, 22.dp) }, L("تحديث"), Modifier.weight(1f)) { onDismiss(); a.onReload() }
                        QuickAction({ c -> IosIcon(IosGlyph.Share, c, 22.dp) }, L("مشاركة"), Modifier.weight(1f)) { onDismiss(); a.onShare() }
                        QuickAction({ c -> IosIcon(IosGlyph.Copy, c, 22.dp) }, L("نسخ الرابط"), Modifier.weight(1f)) { onDismiss(); a.onCopy() }
                        QuickAction({ c -> OptIcon(OptGlyph.Compass, c, 22.dp) }, "Open in browser", Modifier.weight(1f)) { onDismiss(); a.onOpenNova() }
                    }
                    Spacer(Modifier.height(10.dp))

                    // مفاتيح بلاطات: لكل خيار أيقونته
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToggleTile(
                            { c -> OptIcon(OptGlyph.Rotate, c, 24.dp) }, L("اتجاه الشاشة"),
                            when (o.orient) {
                                AppPrefs.ORIENT_PORTRAIT -> L("عمودي")
                                AppPrefs.ORIENT_LANDSCAPE -> L("أفقي")
                                else -> L("تلقائي")
                            },
                            o.orient != AppPrefs.ORIENT_AUTO, Modifier.weight(1f)
                        ) { tick(); a.onOrient() }
                        ToggleTile({ c -> OptIcon(OptGlyph.Bulb, c, 24.dp) }, L("إبقاء الشاشة مضاءة"), null, o.awake, Modifier.weight(1f)) { tick(); a.onAwake(!o.awake) }
                        ToggleTile({ c -> OptIcon(OptGlyph.Expand, c, 24.dp) }, L("ملء الشاشة"), null, o.immersive, Modifier.weight(1f)) { tick(); a.onImmersive(!o.immersive) }
                        ToggleTile({ c -> IosIcon(IosGlyph.Reload, c, 24.dp) }, L("السحب للتحديث"), null, o.pull, Modifier.weight(1f)) { tick(); a.onPull(!o.pull) }
                    }

                    // الأوضاع
                    SectionLabel(L("الأوضاع"))
                    val modes = remember(o.isYt) { if (o.isYt) listOf(0, 1, 2, 3, 4) else listOf(0, 3, 4) }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (m in modes) ModeChip(modeGlyph(m), modeName(m), o.mode == m) { tick(); a.onMode(m) }
                    }

                    // يوتيوب
                    if (o.isYt) {
                        SectionLabel("YouTube")
                        IosGroup {
                            if (o.canDownload) IosCell(
                                L("تنزيل"), { onDismiss(); a.onDownload() },
                                leading = { IosTile(ios.green) { OptIcon(OptGlyph.Down, Color.White, 18.dp) } }, chevron = true
                            )
                            if (o.canAudio) IosCell(
                                L("صوت فقط"), { onDismiss(); a.onAudioOnly() },
                                leading = { IosTile(ios.indigo) { IosIcon(IosGlyph.Play, Color.White, 16.dp) } }, chevron = true
                            )
                            if (o.canShareAt) IosCell(
                                L("مشاركة من الوقت الحالي"), { onDismiss(); a.onShareAt() }, detail = o.shareAtLabel,
                                leading = { IosTile(ios.blue) { OptIcon(OptGlyph.Clock, Color.White, 18.dp) } }, chevron = true
                            )
                            if (o.canYtApp) IosCell(
                                L("افتح في تطبيق يوتيوب"), { onDismiss(); a.onOpenYtApp() },
                                leading = { IosTile(ios.red) { OptIcon(OptGlyph.Tube, Color.White, 18.dp) } }, chevron = true
                            )
                            IosCell(
                                L("إخفاء Shorts"), { tick(); a.onShorts(!o.shorts) },
                                leading = { IosTile(ios.pink) { OptIcon(OptGlyph.NoShorts, Color.White, 18.dp) } },
                                trailing = { IosSwitch(checked = o.shorts, onCheckedChange = null) }
                            )
                            IosCell(
                                L("وضع التركيز"), { tick(); a.onFocus(!o.focus) }, sub = L("يخفي التعليقات والمقترحات في صفحة الفيديو"),
                                leading = { IosTile(ios.teal) { OptIcon(OptGlyph.Focus, Color.White, 18.dp) } },
                                trailing = { IosSwitch(checked = o.focus, onCheckedChange = null) }
                            )
                            IosCell(
                                L("جودة بيانات الجوال"), { tick(); a.onQMobile() }, detail = qualityLabel(o.qMobile),
                                leading = { IosTile(ios.orange) { OptIcon(OptGlyph.Signal, Color.White, 18.dp) } }, chevron = true
                            )
                            IosCell(
                                L("جودة Wi-Fi"), { tick(); a.onQWifi() }, detail = qualityLabel(o.qWifi), divider = false,
                                leading = { IosTile(ios.green) { OptIcon(OptGlyph.Wifi, Color.White, 18.dp) } }, chevron = true
                            )
                        }
                    }

                    // عام
                    SectionLabel(L("عام"))
                    IosGroup {
                        IosCell(
                            L("مؤقّت النوم"), { tick(); a.onSleep() },
                            detail = when {
                                o.sleepLeftSec > 0 -> fmtTime(o.sleepLeftSec)
                                o.sleepMin > 0 -> "${o.sleepMin} " + L("دقيقة")
                                else -> L("متوقف")
                            },
                            leading = { IosTile(ios.purple) { OptIcon(OptGlyph.Timer, Color.White, 18.dp) } }, chevron = true,
                            divider = true
                        )
                        IosCell(
                            L("تعتيم الشاشة"), { tick(); a.onDim(!o.dim) },
                            leading = { IosTile(ios.indigo) { OptIcon(OptGlyph.Moon, Color.White, 18.dp) } },
                            trailing = { IosSwitch(checked = o.dim, onCheckedChange = null) },
                            divider = o.isYt
                        )
                        if (o.isYt) IosCell(
                            L("إيماءات الحواف"), { tick(); a.onGestures(!o.gestures) }, sub = L("السطوع يسار والصوت يمين في ملء الشاشة"),
                            leading = { IosTile(ios.teal) { OptIcon(OptGlyph.Swipe, Color.White, 18.dp) } },
                            trailing = { IosSwitch(checked = o.gestures, onCheckedChange = null) }, divider = false
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    IosGroup {
                        IosCell(
                            L("إزالة التطبيق"), { onDismiss(); a.onRemove() }, tint = ios.red, divider = false,
                            leading = { IosTile(ios.red) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp)) } }
                        )
                    }
                }
            }
        }
    }
}
