package com.nova.browser

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * يوتيوب على محرك Gecko عبر إضافة Semo.
 *
 * المسار:
 *   صفحة يوتيوب (yt-main.js في عالم الصفحة، مولَّد من assets/yt.js)
 *     → yt-relay.js (سكربت محتوى معزول) → background.js → CookieBridge (رسالة type="yt") → هنا
 *   وبالعكس: هنا → CookieBridge.send(type="ytcall"/"ytcfg") → background.js → yt-relay.js → yt-main.js
 *
 * كل الدوال تُستدعى من الخيط الرئيسي؛ send() آمن إن لم يكن المنفذ متصلاً (يُهمل).
 */
object GeckoYt {
    private class Owner(val key: Any, val find: (String) -> BrowserTab?, val onState: () -> Unit, val onLoaded: (() -> Unit)?)

    private val main = Handler(Looper.getMainLooper())
    private val owners = CopyOnWriteArrayList<Owner>()
    private val extTab = ConcurrentHashMap<Int, Int>()         // BrowserTab.id → معرّف تبويب الإضافة
    private val insets = ConcurrentHashMap<Int, Float>()
    private val opts = ConcurrentHashMap<Int, JSONObject>()
    private var hooked = false

    /** صحيح أثناء ملء الشاشة في Gecko (MainActivity يقرأه لتقرير الوضع المنبثق). */
    @Volatile var fullscreen = false

    /** صحيح أثناء النافذة المنبثقة: يوقف عمليات الإنعاش المؤجّلة كي لا تُفسد عرضها. */
    @Volatile var pip = false

    @Volatile var appContext: Context? = null
        private set

    private val ytHosts = setOf("youtube.com", "m.youtube.com", "www.youtube.com", "youtu.be")
    private val authHosts = setOf("accounts.google.com", "consent.google.com", "accounts.youtube.com", "consent.youtube.com")
    private val ccSize = arrayOf("s", "m", "l", "xl")
    private val ccBg = arrayOf("glass", "solid", "none")
    private val ccPos = arrayOf("b", "m", "t")

    // ───────────── تعريف الصفحات ─────────────

    fun isYtUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return (u.scheme == "https" || u.scheme == "http") && u.host?.lowercase() in ytHosts
    }

    /** صفحات تبقى داخل تبويب يوتيوب: يوتيوب نفسه + تسجيل الدخول والموافقة. */
    fun staysInside(u: Uri): Boolean {
        val h = u.host?.lowercase() ?: return false
        return h in ytHosts || h.endsWith(".youtube.com") || h in authHosts
    }

    /** مفتاح مطابقة رابط الصفحة (من الإضافة) مع رابط التبويب: المضيف + المسار + معرّف الفيديو/القائمة. */
    fun key(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = (u.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val id = u.getQueryParameter("v") ?: u.getQueryParameter("list") ?: u.getQueryParameter("search_query") ?: ""
        return host + (u.path ?: "") + (if (id.isNotEmpty()) "?$id" else "")
    }

    fun isLive(t: BrowserTab): Boolean = t.yt && t.ytPlaying

    fun owns(tab: BrowserTab?): Boolean = tab != null && tab.yt && tab.engine != null

    // ───────────── التسجيل ─────────────

    fun register(owner: Any, ctx: Context, find: (String) -> BrowserTab?, onState: () -> Unit, onLoaded: (() -> Unit)? = null) {
        appContext = ctx.applicationContext
        owners.removeAll { it.key === owner }
        owners.add(Owner(owner, find, onState, onLoaded))
        if (!hooked) {
            hooked = true
            CookieBridge.listeners.add { type, o -> if (type == "yt") main.post { handle(o) } }
        }
        pushCfgAll()
    }

    fun unregister(owner: Any) {
        owners.removeAll { it.key === owner }
    }

    fun forget(tab: BrowserTab) {
        extTab.remove(tab.id); insets.remove(tab.id); opts.remove(tab.id)
    }

    // ───────────── رسائل الصفحة → التطبيق ─────────────

    private fun handle(m: JSONObject) {
        val url = m.optString("url")
        val body = runCatching { JSONObject(m.optString("s")) }.getOrNull() ?: return
        val owner = owners.firstOrNull { it.find(url) != null } ?: return
        val tab = owner.find(url) ?: return
        val id = m.optInt("tab", -1)
        if (id >= 0) extTab[tab.id] = id
        when (body.optString("t")) {
            "hello" -> {
                pushCfg(tab)
                insets[tab.id]?.let { call(tab, "__novaInset", it.toDouble()) }
                opts[tab.id]?.let { call(tab, "__novaOpts", it) }
                owner.onLoaded?.invoke()
            }
            "log" -> YtLog.add(body.optString("m"))
            "state" -> {
                tab.ytPlaying = body.optBoolean("playing")
                if (!isYtVideo(url)) return          // لا إشعار لمعاينات الصفحة الرئيسية
                val c = appContext ?: return
                YtMedia.updateGecko(c, tab, url, body)
                owner.onState()
            }
        }
    }

    // ───────────── التطبيق → الصفحة ─────────────

    private fun call(tab: BrowserTab, fn: String, vararg args: Any) {
        val id = extTab[tab.id] ?: return
        val a = JSONArray()
        args.forEach { a.put(it) }
        CookieBridge.send(JSONObject().put("type", "ytcall").put("tab", id).put("fn", fn).put("args", a))
    }

    private fun later(ms: Long, r: () -> Unit) { if (ms <= 0L) main.post(r) else main.postDelayed(r, ms) }

    private fun ccJson() = JSONObject()
        .put("size", ccSize[Prefs.ytCcSize.coerceIn(0, 3)])
        .put("bg", ccBg[Prefs.ytCcBg.coerceIn(0, 2)])
        .put("pos", ccPos[Prefs.ytCcPos.coerceIn(0, 2)])

    private fun buildCfg(): JSONObject? {
        val c = appContext ?: return null
        return JSONObject()
            .put("bg", Prefs.ytBg)
            .put("play", JSONObject().put("resume", Prefs.ytResume).put("keep", Prefs.ytKeepRate).put("hold", Prefs.ytHold2x))
            .put("cc", ccJson().put("off", 0))
            .put("ui", JSONObject().put("shorts", Prefs.ytNoShorts).put("anim", Prefs.smoothAnim && Adaptive.level == 0))
            .put("rules", runCatching { JSONObject(YtRules.json(c)) }.getOrDefault(JSONObject()))
    }

    /** يحدّث إعدادات الإضافة العامة (تُقرأ عند فتح صفحة يوتيوب جديدة). */
    fun pushCfgAll() {
        val cfg = buildCfg() ?: return
        CookieBridge.send(JSONObject().put("type", "ytcfg").put("cfg", cfg))
    }

    /** يدفع الإعدادات لصفحة مفتوحة: الإعدادات الكاملة للإقلاع المبكر، والترجمة مباشرة لما بعد الإقلاع. */
    fun pushCfg(tab: BrowserTab) {
        val cfg = buildCfg() ?: return
        val id = extTab[tab.id]
        CookieBridge.send(JSONObject().put("type", "ytcfg").put("cfg", cfg).put("tabs", JSONArray().apply { if (id != null) put(id) }))
        call(tab, "__novaCc", ccJson())
    }

    fun pushOpts(tab: BrowserTab, shorts: Boolean, focus: Boolean, q: String) {
        val o = JSONObject().put("shorts", shorts).put("focus", focus).put("q", q)
        opts[tab.id] = o
        call(tab, "__novaOpts", o)
    }

    fun setInset(tab: BrowserTab, px: Float) {
        insets[tab.id] = px
        call(tab, "__novaInset", px.toDouble())
    }

    fun control(tab: BrowserTab, a: String) {
        if (a == "play" && Prefs.ytBg) tab.engine?.setActive(true)
        call(tab, "__novaYtCtl", a)
    }

    // ───────────── دورة الحياة: خلفية / منبثقة / ملء الشاشة / دوران ─────────────

    fun background(tab: BrowserTab, on: Boolean) = call(tab, "__novaBg", on)

    fun recover(tab: BrowserTab) {
        for (d in longArrayOf(0L, 500L, 1200L)) later(d) {
            if (pip) return@later
            tab.engine?.setActive(true)
            call(tab, "__novaPip", false)
            call(tab, "__novaRefit", false)
        }
    }

    fun onFullscreen(tab: BrowserTab, on: Boolean) {
        fullscreen = on
        if (!Prefs.ytBg) return
        if (on) call(tab, "__novaBg", true) else later(1300L) { background(tab, false) }
    }

    fun afterFullscreen(tab: BrowserTab) {
        for (d in longArrayOf(0L, 350L, 800L, 1500L)) later(d) {
            if (!pip && owns(tab)) call(tab, "__novaRefit", d >= 800L)
        }
        heal(tab)
    }

    @Volatile private var healGen = 0
    fun heal(tab: BrowserTab) {
        val g = ++healGen
        for ((i, d) in longArrayOf(1200L, 3000L, 6000L).withIndex()) later(d) {
            if (g == healGen && !pip && owns(tab)) call(tab, "__novaHeal", i)
        }
    }

    fun afterRotate(tab: BrowserTab) {
        if (pip) return
        for (d in longArrayOf(150L, 500L, 1000L, 1800L)) later(d) {
            if (!pip && owns(tab)) call(tab, "__novaRefit", d >= 1000L)
        }
        heal(tab)
    }

    fun onPip(tab: BrowserTab, inPip: Boolean, fullscreen: Boolean) {
        pip = inPip
        if (inPip) {
            tab.engine?.setActive(true)
            call(tab, "__novaBg", true)
            if (!fullscreen) call(tab, "__novaPip", true)
        } else {
            background(tab, false)
            recover(tab)
        }
    }
}
