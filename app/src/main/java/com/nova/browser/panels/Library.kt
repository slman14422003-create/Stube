package com.nova.browser

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Bookmark(val url: String, val title: String, val folder: String, val added: Long)
class HistoryItem(val url: String, val title: String, val time: Long)

/** مخزن الإشارات المرجعية والسجل (ملف JSON خاص بالتطبيق، يُحمَّل في خيط خلفي ويُحفَظ بتأخير لتجنّب التقطيع). */
object Library {
    var show by mutableStateOf(false)
    val bookmarks = mutableStateListOf<Bookmark>()
    val history = mutableStateListOf<HistoryItem>()
    val readingList = mutableStateListOf<Bookmark>()
    val favorites = mutableStateListOf<Bookmark>()
    var initialTab = 0
    private const val MAX_HISTORY = 5000
    private var file: File? = null
    private val main = Handler(Looper.getMainLooper())
    private var saveQueued = false
    @Volatile private var loaded = false

    fun init(c: Context) {
        if (file != null) return
        val f = File(c.applicationContext.filesDir, "library.json"); file = f
        Work.disk {
            val b = ArrayList<Bookmark>(); val h = ArrayList<HistoryItem>(); val rl = ArrayList<Bookmark>(); val fv = ArrayList<Bookmark>()
            runCatching {
                if (f.exists()) {
                    val o = JSONObject(f.readText())
                    o.optJSONArray("b")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { x -> b.add(Bookmark(x.getString("u"), x.optString("t"), x.optString("f"), x.optLong("a"))) } }
                    o.optJSONArray("f")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { x -> fv.add(Bookmark(x.getString("u"), x.optString("t"), "", x.optLong("a"))) } }
                    o.optJSONArray("r")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { x -> rl.add(Bookmark(x.getString("u"), x.optString("t"), "", x.optLong("a"))) } }
                    o.optJSONArray("h")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { x -> h.add(HistoryItem(x.getString("u"), x.optString("t"), x.optLong("m"))) } }
                }
            }
            main.post { bookmarks.addAll(b); history.addAll(h); readingList.addAll(rl); favorites.addAll(fv); loaded = true }
        }
    }

    private fun scheduleSave() {
        if (saveQueued || !loaded) return
        saveQueued = true
        main.postDelayed({
            saveQueued = false
            val b = bookmarks.toList(); val h = history.toList(); val rl = readingList.toList(); val fv = favorites.toList(); val f = file ?: return@postDelayed
            Work.disk {
                runCatching {
                    val o = JSONObject()
                    o.put("b", JSONArray().also { a -> b.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("f", it.folder).put("a", it.added)) } })
                    o.put("f", JSONArray().also { a -> fv.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("a", it.added)) } })
                    o.put("r", JSONArray().also { a -> rl.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("a", it.added)) } })
                    o.put("h", JSONArray().also { a -> h.forEach { a.put(JSONObject().put("u", it.url).put("t", it.title).put("m", it.time)) } })
                    val tmp = File(f.parentFile, "library.tmp"); tmp.writeText(o.toString()); tmp.renameTo(f)
                }
            }
        }, 4000)
    }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    /** يضيف أو يزيل؛ يعيد true إن أُضيفت. */
    fun toggleBookmark(url: String, title: String): Boolean {
        val i = bookmarks.indexOfFirst { it.url == url }
        val added = i < 0
        if (added) bookmarks.add(0, Bookmark(url, title.ifBlank { url }, "", System.currentTimeMillis())) else bookmarks.removeAt(i)
        scheduleSave(); return added
    }

    fun isInReadingList(url: String) = readingList.any { it.url == url }

    /** يضيف أو يزيل من قائمة القراءة (كسفاري)؛ يعيد true إن أُضيفت. */
    fun toggleReading(url: String, title: String): Boolean {
        val i = readingList.indexOfFirst { it.url == url }
        val added = i < 0
        if (added) readingList.add(0, Bookmark(url, title.ifBlank { url }, "", System.currentTimeMillis())) else readingList.removeAt(i)
        scheduleSave(); return added
    }

    fun isFavorite(url: String) = favorites.any { it.url == url }

    /** مفضلة صفحة البداية (كسفاري)؛ يعيد true إن أُضيفت. */
    fun toggleFavorite(url: String, title: String): Boolean {
        val i = favorites.indexOfFirst { it.url == url }
        val added = i < 0
        if (added) favorites.add(Bookmark(url, title.ifBlank { hostOf(url) }, "", System.currentTimeMillis())) else favorites.removeAt(i)
        scheduleSave(); return added
    }

    fun removeReading(b: Bookmark) { readingList.remove(b); scheduleSave() }

    fun removeBookmark(b: Bookmark) { bookmarks.remove(b); scheduleSave() }
    fun removeHistory(h: HistoryItem) { history.remove(h); scheduleSave() }
    fun clearHistory() { history.clear(); scheduleSave() }
    fun clearHistorySince(since: Long) { history.removeAll { it.time >= since }; scheduleSave() }

    /** تسجيل زيارة صفحة (يُستدعى بعد اكتمال التحميل). */
    fun visit(url: String, title: String?) {
        if (!url.startsWith("http") || !loaded) return
        // نفس الصفحة خلال 30 دقيقة (إعادة تحميل، تدوير الشاشة، تنقّل داخل يوتيوب…) تُحدَّث بدل أن تتكرر
        val now = System.currentTimeMillis()
        var old: HistoryItem? = null
        var i = 0
        while (i < history.size && now - history[i].time < 30 * 60 * 1000L) {
            if (history[i].url == url) { old = history.removeAt(i); break }
            i++
        }
        val t = title?.takeIf { it.isNotBlank() } ?: old?.title ?: url
        history.add(0, HistoryItem(url, t, now))
        while (history.size > MAX_HISTORY) history.removeAt(history.lastIndex)
        scheduleSave()
    }

    /** دمج مستورد دون تكرار. يُستدعى على أي خيط؛ التعديل الفعلي يتم على الخيط الرئيسي ويُنتظر انتهاؤه. */
    fun addBookmarks(list: List<Bookmark>): Int = onMain {
        val have = bookmarks.mapTo(HashSet()) { it.url }; var n = 0
        for (b in list) if (have.add(b.url)) { bookmarks.add(b); n++ }
        if (n > 0) scheduleSave(); n
    }

    fun addHistory(list: List<HistoryItem>): Int = onMain {
        val have = history.mapTo(HashSet()) { it.url + "|" + it.time / 60000 }; var n = 0
        val fresh = list.filter { have.add(it.url + "|" + it.time / 60000) }.also { n = it.size }
        if (n > 0) {
            val merged = (history.toList() + fresh).sortedByDescending { it.time }.take(MAX_HISTORY)
            history.clear(); history.addAll(merged); scheduleSave()
        }
        n
    }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var out: Any? = null; val latch = java.util.concurrent.CountDownLatch(1)
        main.post { out = block(); latch.countDown() }
        latch.await()
        @Suppress("UNCHECKED_CAST") return out as T
    }
}

@Composable
fun LibraryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(Library.initialTab.also { Library.initialTab = 0 }) }
    var query by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var help by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            importing = true
            Work.cpu {
                val r = runCatching { ChromeImport.run(ctx.applicationContext, uris) }.getOrElse { ImportResult().also { x -> x.notes += (it.message ?: "error") } }
                Handler(Looper.getMainLooper()).post { importing = false; result = r }
            }
        }
    }

    val q = query.trim()
    val bms by remember(q) { derivedStateOf { Library.bookmarks.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) || it.folder.contains(q, true) } } }
    val rds by remember(q) { derivedStateOf { Library.readingList.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) } } }
    val hs by remember(q) { derivedStateOf { Library.history.filter { q.isBlank() || it.title.contains(q, true) || it.url.contains(q, true) } } }
    val rows = remember(hs, tab) { if (tab == 1) buildRows(hs.take(1000)) else emptyList() }
    val timeFmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }

    Surface(Modifier.fillMaxSize(), color = LocalIos.current.groupedBg) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PanelTopBar(L("المكتبة"), Library.bookmarks.size.toString() + " " + L("المفضلة") + " • " + Library.history.size + " " + L("السجل"), onBack) {
                PanelMenu { close ->
                    DropdownMenuItem(text = { Text(L("استيراد من كروم")) }, leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }, onClick = { close(); help = true })
                    if (Library.history.isNotEmpty()) DropdownMenuItem(text = { Text(L("مسح السجل")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { close(); confirmClear = true })
                }
            }
            PanelSegments(listOf(L("المفضلة") + " (${Library.bookmarks.size})", L("السجل") + " (${Library.history.size})", L("قائمة القراءة") + " (${Library.readingList.size})"), tab) { tab = it }
            PanelSearch(query, { query = it }, L("بحث"))
            if (importing) NovaLoadingRow(L("جارٍ الاستيراد…"))
            val empty = when (tab) { 0 -> bms.isEmpty(); 2 -> rds.isEmpty(); else -> hs.isEmpty() }
            val emptyMod = Modifier.weight(1f).fillMaxWidth()
            if (empty && q.isNotBlank()) {
                PanelEmpty(Icons.Default.Search, L("لا نتائج"), modifier = emptyMod)
            } else if (empty && tab == 0) {
                PanelEmpty(Icons.Default.FavoriteBorder, L("لا توجد مفضلة بعد"), L("اضغط «المفضلة» في قائمة الخيارات لحفظ الصفحة هنا"), emptyMod)
            } else if (empty && tab == 2) {
                PanelEmpty(Icons.Default.Info, L("قائمة القراءة فارغة"), L("احفظ صفحات لقراءتها لاحقاً من قائمة المشاركة"), emptyMod)
            } else if (empty) {
                PanelEmpty(Icons.Default.Refresh, L("السجل فارغ"), L("الصفحات التي تزورها تظهر هنا"), emptyMod)
            } else if (tab == 0) {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(bms, key = { i, b -> b.url + i }) { i, b ->
                        ListRow(groupShape(i, bms.size), b.title, if (b.folder.isBlank()) hostOf(b.url) else b.folder + " • " + hostOf(b.url), { onOpen(b.url) },
                            trailing = { ItemMenu(b.url, b.title, false, { onOpen(b.url) }, { Library.removeBookmark(b) }) }) { SiteIcon(b.url, Icons.Default.Star) }
                    }
                }
            } else if (tab == 2) {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(rds, key = { i, b -> b.url + i }) { i, b ->
                        ListRow(groupShape(i, rds.size), b.title, hostOf(b.url), { onOpen(b.url) },
                            trailing = { ItemMenu(b.url, b.title, false, { onOpen(b.url) }, { Library.removeReading(b) }) }) { SiteIcon(b.url, Icons.Default.Info) }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(rows, key = { i, r -> if (r.header != null) "h" + r.header else "i$i" }) { _, r ->
                        if (r.header != null) {
                            Text(r.header, style = MaterialTheme.typography.titleMedium, color = LocalIos.current.label,
                                modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
                        } else {
                            val h = r.item!!
                            ListRow(groupShape(r.idx, r.n), h.title, hostOf(h.url) + " • " + timeFmt.format(java.util.Date(h.time)), { onOpen(h.url) },
                                trailing = { ItemMenu(h.url, h.title, true, { onOpen(h.url) }, { Library.removeHistory(h) }) }) { SiteIcon(h.url, Icons.Default.Refresh) }
                        }
                    }
                }
            }
        }
    }

    if (help) NovaDialog(
        title = L("استيراد بيانات كروم"), icon = Icons.Default.Info, onDismiss = { help = false },
        confirmText = L("اختيار الملفات"), onConfirm = { help = false; picker.launch(arrayOf("*/*")) }, dismissText = L("إغلاق")
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(L("أندرويد يمنع أي تطبيق من قراءة بيانات كروم مباشرة، لذلك يُستورد من ملفات يصدّرها كروم نفسه. اختر ملفاً أو عدة ملفات دفعة واحدة:"), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text(L("• كلمات المرور: كروم ← الإعدادات ← مدير كلمات المرور ← تصدير (ملف CSV). احذف الملف بعد الاستيراد."), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(L("• المفضلة والسجل والباقي: takeout.google.com ← اختر Chrome فقط ← صدّر ← اختر ملف ZIP كما هو."), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(L("• من كروم سطح المكتب: ملف Bookmarks.html (مدير الإشارات ← تصدير) أو ملفا Bookmarks وHistory من مجلد ملف التعريف."), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(L("لا يمكن نقل الكوكيز وتبويبات كروم المفتوحة؛ كروم لا يصدّرها."), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }

    result?.let { r ->
        NovaDialog(
            title = L("اكتمل الاستيراد"), icon = Icons.Default.Check, onDismiss = { result = null },
            confirmText = L("إغلاق"), onConfirm = { result = null }, dismissText = null
        ) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(L("المفضلة") + ": ${r.bookmarks}\n" + L("السجل") + ": ${r.history}\n" + L("كلمات المرور") + ": ${r.passwords}\n" + L("متجاهَل") + ": ${r.skipped}")
                if (r.notes.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(r.notes.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = cs.error) }
            }
        }
    }
    if (confirmClear) {
        val now = System.currentTimeMillis()
        val midnight = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val opts = listOf(
            L("آخر ساعة") to now - 3_600_000L, L("اليوم") to midnight,
            L("آخر 7 أيام") to now - 7 * 86_400_000L, L("كل السجل") to 0L
        )
        NovaDialog(
            title = L("مسح السجل"), icon = Icons.Default.Delete, danger = true, onDismiss = { confirmClear = false }, dismissText = L("إلغاء")
        ) {
            opts.forEach { (name, since) ->
                Surface(
                    onClick = { Library.clearHistorySince(since); confirmClear = false; toast(ctx, L("تم المسح")) },
                    shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHighest.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()
                ) { Text(name, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

private class HRow(val header: String?, val item: HistoryItem?, val idx: Int, val n: Int)

/** يجمع السجل تحت عناوين الأيام (اليوم / أمس / تاريخ) مع أشكال مجموعات متصلة داخل كل يوم. */
private fun buildRows(list: List<HistoryItem>): List<HRow> {
    val cal = java.util.Calendar.getInstance()
    fun key(t: Long): Int { cal.timeInMillis = t; return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR) }
    val nowT = System.currentTimeMillis()
    val today = key(nowT); val yest = key(nowT - 86_400_000L)
    val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    val out = ArrayList<HRow>()
    var i = 0
    while (i < list.size) {
        val k = key(list[i].time); var j = i
        while (j < list.size && key(list[j].time) == k) j++
        val label = when (k) { today -> L("اليوم"); yest -> L("أمس"); else -> df.format(java.util.Date(list[i].time)) }
        out.add(HRow(label, null, 0, 0))
        for (x in i until j) out.add(HRow(null, list[x], x - i, j - i))
        i = j
    }
    return out
}

@Composable
private fun SiteIcon(url: String, fallback: androidx.compose.ui.graphics.vector.ImageVector) {
    val ver = Favicons.version
    val bmp = remember(ver, url) { Favicons.get(hostOf(url)) }
    IconCircle {
        if (bmp != null) Image(remember(bmp) { bmp.asImageBitmap() }, null, Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))) else Icon(fallback, null)
    }
}

@Composable
private fun ItemMenu(url: String, title: String, showFav: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(36.dp).clickable { open = true }, contentAlignment = Alignment.Center) { IosIcon(IosGlyph.EllipsisCircle, LocalIos.current.blue, 24.dp) }
        IosMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(14.dp), tonalElevation = 0.dp) {
            DropdownMenuItem(text = { Text(L("فتح")) }, leadingIcon = { Icon(Icons.Default.ExitToApp, null) }, onClick = { open = false; onOpen() })
            DropdownMenuItem(text = { Text(L("نسخ الرابط")) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { open = false; copyText(ctx, url) })
            DropdownMenuItem(text = { Text(L("مشاركة")) }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { open = false; shareText(ctx, url) })
            if (showFav) {
                val marked = Library.isBookmarked(url)
                DropdownMenuItem(
                    text = { Text(if (marked) L("إزالة من المفضلة") else L("إضافة إلى المفضلة")) },
                    leadingIcon = { Icon(if (marked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
                    onClick = { open = false; toast(ctx, if (Library.toggleBookmark(url, title)) L("أُضيفت إلى المفضلة") else L("أُزيلت من المفضلة")) }
                )
            }
            DropdownMenuItem(text = { Text(L("حذف")) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { open = false; onDelete() })
        }
    }
}
