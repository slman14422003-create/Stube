package com.nova.browser

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object Shield {
    enum class Kind { TRACKER, SPOOF, DIALOG, DOWNLOAD }

    private val counts = Array(Kind.values().size) { AtomicInteger(0) }
    private var persisted = 0
    private var conf: ConfStore? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pending = false

    var version by mutableIntStateOf(0)
        private set

    fun init(c: Context) {
        val st = ConfStore.open(c.applicationContext)
        conf = st
        persisted = st.getInt("shield_total", 0)
    }

    fun count(k: Kind): Int = counts[k.ordinal].get()
    val session: Int get() { var n = 0; for (c in counts) n += c.get(); return n }
    val total: Int get() = persisted + session

    private fun hit(k: Kind, detail: String) {
        counts[k.ordinal].incrementAndGet()
        if (k != Kind.TRACKER) Security.log(label(k), detail)
        val s = session
        if (s % 10 == 0 || k != Kind.TRACKER) conf?.putInt("shield_total", persisted + s)
        if (!pending) {
            pending = true
            main.postDelayed({ pending = false; version++ }, 500)
        }
    }

    private fun label(k: Kind): String = when (k) {
        Kind.TRACKER -> L("تتبع")
        Kind.SPOOF -> L("رابط مخادع")
        Kind.DIALOG -> L("إغراق نوافذ")
        Kind.DOWNLOAD -> L("تنزيل تلقائي")
    }

    fun summary(): String =
        L("الحماية الفورية: ") + (if (Prefs.shield) L("مفعّلة") else L("متوقفة")) + "\n" +
            L("متتبعات الطرف الثالث: ") + count(Kind.TRACKER) + "\n" +
            L("روابط مخادعة: ") + count(Kind.SPOOF) + "\n" +
            L("نوافذ وتنزيلات تلقائية: ") + (count(Kind.DIALOG) + count(Kind.DOWNLOAD))

    fun tracker(host: String) {
        hit(Kind.TRACKER, host)
        Security.countBlocked()
    }

    fun hostFor(url: String?): String {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return ""
        return runCatching { Uri.parse(url).host?.lowercase() }.getOrNull().orEmpty()
    }

    fun isSpoofed(u: Uri): Boolean {
        if (!Prefs.shield) return false
        val ui = runCatching { u.userInfo }.getOrNull() ?: return false
        if (ui.isEmpty()) return false
        val bad = ui.contains('.') || ui.length > 24
        if (bad) hit(Kind.SPOOF, (u.host ?: "?"))
        return bad
    }

    private class Win { var n = 0; var t0 = 0L }
    private val dialogWin = ConcurrentHashMap<String, Win>()
    private val downloadWin = ConcurrentHashMap<String, Win>()

    private fun within(map: ConcurrentHashMap<String, Win>, key: String, windowMs: Long, max: Int): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (map.size > 300) map.clear()
        val w = map.getOrPut(key) { Win() }
        synchronized(w) {
            if (now - w.t0 > windowMs) { w.n = 0; w.t0 = now }
            w.n++
            return w.n <= max
        }
    }

    fun dialogAllowed(host: String): Boolean {
        if (!Prefs.shield) return true
        val ok = within(dialogWin, host, 10_000, 3)
        if (!ok && dialogWin[host]?.n == 4) hit(Kind.DIALOG, host)
        return ok
    }

    fun downloadAllowed(host: String): Boolean {
        if (!Prefs.shield) return true
        val ok = within(downloadWin, host, 6_000, 3)
        if (!ok && downloadWin[host]?.n == 4) hit(Kind.DOWNLOAD, host)
        return ok
    }
}
