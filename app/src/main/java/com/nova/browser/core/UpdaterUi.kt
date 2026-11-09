package com.nova.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** نص حالة التحديث لصف الإعدادات. */
@Composable
fun updateStatus(): String = when (Updater.phase) {
    UpdPhase.CHECKING -> L("جارٍ الفحص…")
    UpdPhase.UP_TO_DATE -> L("أنت على أحدث إصدار")
    UpdPhase.AVAILABLE -> L("إصدار جديد") + " " + (Updater.info?.version ?: "") + " — " + L("اضغط للتنزيل")
    UpdPhase.DOWNLOADING -> L("جارٍ التنزيل…") + " " + (Updater.progress * 100).toInt() + "%"
    UpdPhase.READY -> L("جاهز — اضغط للتثبيت") + (if (Updater.error.isNotBlank()) "\n" + Updater.error else "")
    UpdPhase.INSTALLING -> L("جارٍ التثبيت…")
    UpdPhase.ERROR -> L("فشل") + ": " + Updater.error
    UpdPhase.IDLE -> L("اضغط للفحص الآن")
}

private val RE_COMMENT = Regex("<!--[\\s\\S]*?-->")
private val RE_LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
private val RE_BY = Regex("\\s+by\\s+@\\S+(\\s+in\\s+\\S+)?", RegexOption.IGNORE_CASE)
private val RE_URL = Regex("https?://\\S+")

/**
 * ملاحظات الإصدار من GitHub تأتي بصيغة Markdown خام (تعليقات HTML، عناوين، روابط، «by @user in …»).
 * نحوّلها إلى أسطر نظيفة قصيرة تصلح نقاطاً في النافذة، ونحذف سطر «Full Changelog» والعناوين التلقائية.
 */
fun releaseHighlights(raw: String, max: Int = 8): List<String> {
    val out = ArrayList<String>()
    for (line in RE_COMMENT.replace(raw, "").lines()) {
        var l = line.trim()
        if (l.isEmpty()) continue
        if (l.contains("Full Changelog", true) || l.contains("What's Changed", true) ||
            l.contains("New Contributors", true) || l.contains("first contribution", true)) continue
        l = l.trimStart('#', '>', '*', '-', '+', '•', ' ')
        l = RE_LINK.replace(l, "\$1")
        l = RE_BY.replace(l, "")
        l = RE_URL.replace(l, "")
        l = l.replace("**", "").replace("__", "").replace("`", "").trim()
        if (l.length < 2) continue
        out += if (l.length > 140) l.take(137) + "…" else l
        if (out.size >= max) break
    }
    return out
}

@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val ios = LocalIos.current
    val i = Updater.info ?: return
    val busy = Updater.phase == UpdPhase.DOWNLOADING || Updater.phase == UpdPhase.INSTALLING
    val notes = remember(i.notes) { releaseHighlights(i.notes) }
    NovaDialog(
        title = L("تحديث جديد"), onDismiss = { if (!busy) Updater.dismissPrompt() },
        confirmText = when (Updater.phase) {
            UpdPhase.READY -> L("تثبيت")
            UpdPhase.DOWNLOADING -> L("جارٍ التنزيل…")
            UpdPhase.INSTALLING -> L("جارٍ التثبيت…")
            else -> L("تنزيل وتثبيت")
        },
        onConfirm = { when (Updater.phase) { UpdPhase.READY -> Updater.install(ctx); else -> Updater.download(ctx) } },
        confirmEnabled = !busy, dismissText = L("لاحقاً"), dismissEnabled = !busy, onDismissClick = { Updater.dismissPrompt() }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("v" + i.version.removePrefix("v"), strong = true)
            if (i.size > 0) Chip(fmtSize(i.size), strong = false)
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("ما الجديد"), style = MaterialTheme.typography.titleSmall, color = ios.label)
            (notes.ifEmpty { listOf(L("تحسينات وإصلاحات عامة")) }).forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(ios.label2))
                    Text(n, style = MaterialTheme.typography.bodySmall, color = ios.label, modifier = Modifier.weight(1f))
                }
            }
        }
        if (Updater.phase == UpdPhase.DOWNLOADING) {
            val p by animateFloatAsState(Updater.progress.coerceIn(0f, 1f), tween(Adaptive.ms(180)), label = "upd")
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(ios.fill)) {
                    Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.02f)).fillMaxHeight().clip(CircleShape).background(ios.blue))
                }
                Text((p * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelMedium, color = ios.label2)
            }
        }
        if (Updater.error.isNotBlank()) Text(Updater.error, style = MaterialTheme.typography.bodySmall, color = ios.red)
    }
}

@Composable
private fun Chip(text: String, strong: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (strong) cs.tertiary.copy(alpha = 0.15f) else cs.surfaceContainerHighest) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (strong) cs.tertiary else cs.onSurfaceVariant
        )
    }
}

