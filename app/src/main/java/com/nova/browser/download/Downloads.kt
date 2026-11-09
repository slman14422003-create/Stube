package com.nova.browser

import androidx.compose.foundation.clickable
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun fmtSize(b: Long): String {
    if (b < 1024) return "$b B"
    val u = arrayOf("KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = -1
    while (v >= 1024 && i < 3) { v /= 1024; i++ }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, u[i])
}

fun fmtSpeed(b: Long) = fmtSize(b) + "/s"

fun fmtEta(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%02d:%02d", m, sec)
}

fun viewUri(t: DlTask): android.net.Uri? {
    val u = t.uri ?: return null
    val base = when {
        t.mime.startsWith("image/", true) -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        t.mime.startsWith("video/", true) -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        t.mime.startsWith("audio/", true) -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> return u
    }
    if (u.authority != android.provider.MediaStore.AUTHORITY) return u
    return runCatching { android.content.ContentUris.withAppendedId(base, android.content.ContentUris.parseId(u)) }.getOrDefault(u)
}

fun openFile(c: Context, t: DlTask) {
    runCatching {
        c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(viewUri(t), t.mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { if (c !is android.app.Activity) it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }.onFailure { toast(c, L("لا يوجد تطبيق لفتح الملف")) }
}

fun shareFile(c: Context, t: DlTask) {
    val i = Intent(Intent.ACTION_SEND).setType(t.mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, t.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    c.startActivity(Intent.createChooser(i, null))
}

private fun statusLine(t: DlTask): String {
    val tot = if (t.total > 0) fmtSize(t.total) else L("؟")
    return when (t.status) {
        Downloader.PREPARING -> L("جارٍ التحضير…")
        Downloader.DOWNLOADING -> {
            val pct = if (t.total > 0) " • ${(t.downloaded * 100 / t.total)}%" else ""
            val eta = if (t.speed > 0 && t.total > 0) " • ${fmtEta((t.total - t.downloaded) / t.speed)}" else ""
            ("" + (fmtSize(t.downloaded)) + " / " + tot + pct + " • " + (fmtSpeed(t.speed)) + eta + " • " + (t.conns) + L(" اتصال"))
        }
        Downloader.PAUSED -> (L("متوقف • ") + (fmtSize(t.downloaded)) + " / " + tot)
        Downloader.FAILED -> (L("فشل: ") + (t.error))
        else -> ("" + tot + L(" • اكتمل"))
    }
}

@Composable
fun SegmentBar(t: DlTask) {
    val ios = LocalIos.current
    val track = ios.fill3; val fill = ios.blue; val gap = ios.groupedBg
    Canvas(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        drawRect(track)
        val tot = t.total
        if (tot > 0 && t.segSnap.isNotEmpty()) {
            for (s in t.segSnap) {
                val x0 = s[0].toFloat() / tot * size.width
                val x1 = s[2].toFloat() / tot * size.width
                if (x1 > x0) drawRect(fill, Offset(x0, 0f), Size(x1 - x0, size.height))
            }
            for (s in t.segSnap) drawRect(gap, Offset(s[0].toFloat() / tot * size.width, 0f), Size(1.5f, size.height))
        } else if (tot > 0) {
            drawRect(fill, size = Size(t.downloaded.toFloat() / tot * size.width, size.height))
        }
    }
}

private fun kindOf(name: String): Int = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts" -> 1
    "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> 2
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> 3
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv" -> 4
    "zip", "rar", "7z", "tar", "gz" -> 5
    "apk", "apks", "xapk" -> 6
    else -> 0
}

private fun kindLabel(k: Int) = when (k) {
    1 -> L("فيديو"); 2 -> L("صوت"); 3 -> L("صورة"); 4 -> L("مستند"); 5 -> L("أرشيف"); 6 -> L("تطبيق"); else -> L("ملف")
}

@Composable
private fun kindColor(k: Int): Color {
    val ios = LocalIos.current
    return when (k) {
        1 -> ios.purple
        2 -> ios.pink
        3 -> ios.teal
        4 -> ios.orange
        5 -> ios.yellow
        6 -> ios.indigo
        else -> ios.gray
    }
}

@Composable
fun DownloadCard(t: DlTask, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val running = t.status == Downloader.DOWNLOADING || t.status == Downloader.PREPARING
    val failed = t.status == Downloader.FAILED
    val done = t.status == Downloader.DONE
    val kind = kindOf(t.name)
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.cell)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosTile(kindColor(kind), size = 46.dp) {
                    Text(t.name.substringAfterLast('.', "?").take(4).uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name.ifBlank { hostOf(t.origUrl) }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = ios.label)
                    Text(statusLine(t), style = MaterialTheme.typography.bodySmall, color = if (failed) ios.red else ios.label2)
                    if (done) Text(
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(t.createdAt)) + " • " + kindLabel(kind) + " • " + hostOf(t.origUrl),
                        style = MaterialTheme.typography.labelSmall, color = ios.label2, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    Box(Modifier.size(36.dp).clickable { menu = true }, contentAlignment = Alignment.Center) { IosIcon(IosGlyph.EllipsisCircle, ios.blue, 24.dp) }
                    IosMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(14.dp), tonalElevation = 0.dp) {
                        DropdownMenuItem(text = { Text(L("نسخ الرابط")) }, leadingIcon = { IosIcon(IosGlyph.Copy, ios.label) }, onClick = { menu = false; copyText(ctx, t.origUrl) })
                        if (done) {
                            DropdownMenuItem(text = { Text(L("إزالة من القائمة")) }, leadingIcon = { IosIcon(IosGlyph.Close, ios.label) }, onClick = { menu = false; Downloader.remove(t, false) })
                            DropdownMenuItem(text = { Text(L("حذف الملف"), color = ios.red) }, leadingIcon = { IosIcon(IosGlyph.Trash, ios.red) }, onClick = { menu = false; confirmDelete = true })
                        } else DropdownMenuItem(text = { Text(L("إلغاء وحذف"), color = ios.red) }, leadingIcon = { IosIcon(IosGlyph.Trash, ios.red) }, onClick = { menu = false; Downloader.cancel(t) })
                    }
                }
            }
            if (!done) { Spacer(Modifier.height(12.dp)); SegmentBar(t) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                when {
                    done -> {
                        IosTextButton(L("مشاركة"), { shareFile(ctx, t) })
                        Spacer(Modifier.width(4.dp))
                        IosPillButton(L("فتح"), { openFile(ctx, t) })
                    }
                    running -> {
                        IosTextButton(L("إلغاء"), { Downloader.cancel(t) })
                        Spacer(Modifier.width(4.dp))
                        IosPillButton(L("إيقاف مؤقت"), { Downloader.pause(t) })
                    }
                    else -> {
                        IosTextButton(L("إلغاء"), { Downloader.cancel(t) })
                        Spacer(Modifier.width(4.dp))
                        IosPillButton(if (failed) L("إعادة المحاولة") else L("استئناف"), { Downloader.resume(t) })
                    }
                }
            }
        }
    }
    if (confirmDelete) NovaDialog(
        title = L("حذف الملف؟"), icon = Icons.Default.Delete, danger = true, onDismiss = { confirmDelete = false },
        confirmText = L("حذف"), onConfirm = { confirmDelete = false; Downloader.remove(t, true) }, dismissText = L("إلغاء")
    ) { DialogText(L("سيُحذف الملف من جهازك نهائياً.")) }
}

@Composable
private fun ActiveSummary(running: List<DlTask>) {
    val ios = LocalIos.current
    val speed = running.sumOf { it.speed }
    val known = running.filter { it.total > 0 }
    val totalB = known.sumOf { it.total }
    val progress = if (totalB > 0) (known.sumOf { it.downloaded }.toFloat() / totalB).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ios.blue.copy(alpha = 0.12f))) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("جارٍ تنزيل ") + running.size + L(" ملف"), style = MaterialTheme.typography.titleSmall, color = ios.blue, fontWeight = FontWeight.Bold)
                    Text(fmtSpeed(speed) + if (totalB > 0) " • ${(progress * 100).toInt()}%" else "", style = MaterialTheme.typography.bodySmall, color = ios.blue)
                }
                IosTextButton(L("إيقاف الكل"), { Downloader.pauseAll() }, color = ios.blue)
            }
            if (totalB > 0) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(ios.blue.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(ios.blue))
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val ios = LocalIos.current
    DisposableEffect(Unit) {
        Downloader.uiVisible = true
        onDispose { Downloader.uiVisible = false }
    }
    var filter by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    val all = Downloader.tasks.toList()
    val isRun: (DlTask) -> Boolean = { it.status == Downloader.DOWNLOADING || it.status == Downloader.PREPARING }
    val isStop: (DlTask) -> Boolean = { it.status == Downloader.PAUSED || it.status == Downloader.FAILED }
    val isDone: (DlTask) -> Boolean = { it.status == Downloader.DONE }
    val running = all.filter(isRun)
    val nRun = running.size; val nStop = all.count(isStop); val nDone = all.count(isDone)
    val q = query.trim()
    val filtered = all.filter {
        (when (filter) { 1 -> isRun(it); 2 -> isDone(it); 3 -> isStop(it); else -> true }) &&
            (q.isBlank() || it.name.contains(q, true) || it.origUrl.contains(q, true))
    }
    val list = when (sort) {
        1 -> filtered.sortedBy { it.name.lowercase() }
        2 -> filtered.sortedByDescending { it.total }
        else -> filtered.sortedByDescending { it.createdAt }
    }
    val doneBytes = all.filter(isDone).sumOf { if (it.total > 0) it.total else it.downloaded }
    val subtitle = if (all.isEmpty()) null else all.size.toString() + " " + L("ملف") + " • " + fmtSize(doneBytes)

    Surface(Modifier.fillMaxSize(), color = ios.groupedBg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PanelTopBar(L("التنزيلات"), subtitle, onBack) {
                if (all.isNotEmpty()) PanelMenu { close ->
                    if (nRun > 0) DropdownMenuItem(text = { Text(L("إيقاف الكل")) }, leadingIcon = { IosIcon(IosGlyph.Pause, ios.label) }, onClick = { close(); Downloader.pauseAll() })
                    if (nStop > 0) DropdownMenuItem(text = { Text(L("استئناف الكل")) }, leadingIcon = { IosIcon(IosGlyph.Play, ios.label) },
                        onClick = { close(); all.filter(isStop).forEach { Downloader.resume(it) } })
                    if (nDone > 0) DropdownMenuItem(text = { Text(L("مسح المكتملة من القائمة"), color = ios.red) }, leadingIcon = { IosIcon(IosGlyph.Trash, ios.red) }, onClick = { close(); confirmClear = true })
                    IosDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(L("الترتيب"), style = MaterialTheme.typography.labelMedium, color = ios.label2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    listOf(L("الأحدث أولاً"), L("الاسم"), L("الحجم")).forEachIndexed { i, n ->
                        DropdownMenuItem(text = { Text(n) }, trailingIcon = { if (sort == i) IosIcon(IosGlyph.Check, ios.blue, 18.dp) }, onClick = { close(); sort = i })
                    }
                }
            }
            if (all.isEmpty()) {
                PanelEmpty(Icons.Default.KeyboardArrowDown, L("لا توجد تنزيلات"), L("الملفات التي تنزّلها من المواقع تظهر هنا"))
            } else {
                PanelSearch(query, { query = it }, L("بحث في التنزيلات"))
                PanelChips(
                    listOf(L("الكل") + " (${all.size})", L("جارٍ") + " ($nRun)", L("مكتمل") + " ($nDone)", L("متوقف") + " ($nStop)"),
                    filter, { filter = it }
                )
                if (list.isEmpty()) PanelEmpty(Icons.Default.Search, L("لا نتائج"), modifier = Modifier.weight(1f).fillMaxWidth())
                else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (nRun > 0 && filter != 2 && q.isBlank()) item(key = "summary") { ActiveSummary(running) }
                    items(list, key = { it.id }) { DownloadCard(it, Modifier.animateItem()) }
                }
            }
        }
    }
    if (confirmClear) NovaDialog(
        title = L("مسح المكتملة من القائمة؟"), icon = Icons.Default.Delete, danger = true, onDismiss = { confirmClear = false },
        confirmText = L("مسح"), onConfirm = { confirmClear = false; all.filter(isDone).forEach { Downloader.remove(it, false) } }, dismissText = L("إلغاء")
    ) { DialogText(L("تُزال من القائمة فقط، وتبقى الملفات على جهازك.")) }
}
