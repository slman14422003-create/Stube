package com.nova.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse


class GeckoBridge(private val tab: BrowserTab, private val h: Handlers) : EngineListener {
    private val ctx: Context get() = h.activity
    private val main = Handler(Looper.getMainLooper())

    override fun onUrl(tab: EngineTab, url: String) {
        if (url.startsWith("http")) {
            PageTint.onStart(this.tab, url)
            this.tab.url = url
            GoogleAccounts.guard(this.tab, tab, url)
            val yt = GeckoYt.isYtUrl(url)
            if (this.tab.yt && !yt) this.tab.ytPlaying = false
            this.tab.yt = yt
            YtMedia.pageChanged(this.tab, url)
        }
    }

    override fun onTitle(tab: EngineTab, title: String) {
        if (title.isNotBlank()) this.tab.title = title
    }

    override fun onProgress(tab: EngineTab, progress: Int) {
        val f = progress / 100f
        if (progress == 0 || progress == 100 || kotlin.math.abs(f - this.tab.progress) >= 0.05f) this.tab.progress = f
    }

    override fun onLoading(tab: EngineTab, loading: Boolean) {
        this.tab.loading = loading
        if (loading) {
            this.tab.clearFailedIfNew()
            if (this.tab.yt) GeckoYt.pushCfg(this.tab)
        } else {
            (this.tab.engineView as? SwipeRefreshLayout)?.isRefreshing = false
            if (!this.tab.popup && !this.tab.isPrivate && tab.url.startsWith("http")) Library.visit(tab.url, tab.title)
        }
    }

    override fun onNav(tab: EngineTab, canBack: Boolean, canForward: Boolean) {
        this.tab.canBack = canBack
        this.tab.canForward = canForward
    }

    override fun onLoadRequest(tab: EngineTab, uri: String, target: Int, gesture: Boolean, redirect: Boolean): Boolean {
        val u = Uri.parse(uri)
        val current = target == GeckoSession.NavigationDelegate.TARGET_WINDOW_CURRENT
        return when (u.scheme) {
            "http", "https" -> {
                if (Shield.isSpoofed(u)) {
                    toast(ctx, L("تم حظر رابط مخادع يُخفي وجهته الحقيقية"))
                    false
                } else {
                    if (current && gesture && this.tab.yt && !GeckoYt.staysInside(u)) {
                        h.openTab(Security.cleanUrl(u).toString())
                        return false
                    }
                    if (current && !this.tab.popup && GoogleAccounts.isSignInUrl(u)) {
                        val hinted = GoogleAccounts.withHint(u)
                        if (hinted != u) {
                            main.post { tab.load(hinted.toString()) }
                            return false
                        }
                    }
                    var c = Security.cleanUrl(u)
                    val host = c.host
                    if (current && Prefs.httpsFirst && c.scheme == "http" && host != null && host != this.tab.noUpgradeHost) {
                        val last = this.tab.lastUpgrade
                        val now = System.currentTimeMillis()
                        if (last != null && last.first == host && now - last.second < 5000) {
                            this.tab.noUpgradeHost = host
                        } else {
                            this.tab.upgradedFrom = c.toString()
                            c = c.buildUpon().scheme("https").build()
                            this.tab.upgradedTo = c.toString()
                            this.tab.lastUpgrade = host to now
                            Security.log("HTTPS", host)
                            main.post { tab.load(c.toString()) }
                            return false
                        }
                    }
                    if (current && c != u) {
                        main.post { tab.load(c.toString()) }
                        false
                    } else true
                }
            }
            null, "about", "blob", "data", "resource", "moz-extension", "view-source" -> true
            "intent" -> {
                PageRules.intentTarget(uri)?.takeIf { !PageRules.samePage(tab.url, it) }?.let { f ->
                    val fu = Uri.parse(f)
                    if (!Shield.isSpoofed(fu)) main.post { tab.load(Security.cleanUrl(fu).toString()) }
                }
                false
            }
            "tel", "mailto", "sms", "geo" -> {
                if (gesture) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                false
            }
            else -> {
                if (gesture) PageExtras.openExternal(ctx, u)
                false
            }
        }
    }

    override fun onFind(tab: EngineTab, current: Int, total: Int) {
        this.tab.findInfo = if (total == 0) "0" else "$current/$total"
    }

    override fun onNewTab(tab: EngineTab, uri: String): EngineTab? {
        val t = h.newPopup(uri)
        if (t == null) {
            h.openTab(uri)
            return null
        }
        val sel = GeckoSelection(h.activity) { id, text -> SmartAssist.onSelection(h.activity, id, text, h.openTab) }
        return EngineTab(t.id, GeckoBridge(t, h), null, t.desktop, GeckoUi(h), sel, GeckoMedia(t), false).also { t.engine = it }
    }

    override fun onClose(tab: EngineTab) {
        main.post { h.closePopup(this.tab) }
    }

    override fun onDownload(tab: EngineTab, response: WebResponse) {
        fun header(name: String) = response.headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        val cd = header("Content-Disposition")
        val mime = header("Content-Type")
        val uri = response.uri
        val ref = tab.url
        if (uri.startsWith("blob:", true) || uri.startsWith("data:", true)) {
            if (!Shield.downloadAllowed(hostOf(ref.ifBlank { uri }))) {
                runCatching { response.body?.close() }
                toast(ctx, L("تم حجب تنزيلات تلقائية متكررة من هذه الصفحة"))
            } else StreamDownload.start(ctx, response, cd, mime)
            return
        }
        runCatching { response.body?.close() }
        h.onDownload(uri, null, cd, mime, ref)
    }

    override fun onContextMenu(tab: EngineTab, element: GeckoSession.ContentDelegate.ContextElement) {
        val ref = this.tab.url
        val src = element.srcUri
        val link = element.linkUri
        val items = ArrayList<Pair<String, () -> Unit>>()
        fun add(label: String, act: () -> Unit) { items.add(label to act) }
        fun web(u: String?) = u != null && (u.startsWith("http://", true) || u.startsWith("https://", true))
        fun fetchable(u: String?) = web(u) || (u != null && u.startsWith("data:", true))
        when (element.type) {
            GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> if (src != null) {
                if (fetchable(src)) add(L("تنزيل الصورة")) { h.onDownload(src, null, null, null, ref) }
                if (web(src)) {
                    add(L("فتح الصورة في تبويب جديد")) { h.openTab(src) }
                    add(L("نسخ رابط الصورة")) { copyText(ctx, src) }
                    add(L("مشاركة الصورة")) { shareText(ctx, src) }
                }
            }
            GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO, GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> if (web(src)) {
                add(L("تنزيل")) { h.onDownload(src!!, null, null, null, ref) }
                add(L("نسخ الرابط")) { copyText(ctx, src!!) }
            }
        }
        if (web(link) && link != src) {
            add(L("فتح في تبويب جديد")) { h.openTab(link!!) }
            add(L("نسخ الرابط")) { copyText(ctx, link!!) }
            add(L("مشاركة الرابط")) { shareText(ctx, link!!) }
        }
        if (items.isEmpty()) return
        main.post { runCatching { choose(ctx, items) } }
    }

    override fun onCrash(tab: EngineTab) {
        val saved = this.tab.engine?.state
        main.post {
            h.onGeckoFullScreen(false)
            GeckoYt.fullscreen = false
            if (this.tab.yt) {
                YtMedia.tabClosed(this.tab)
                GeckoYt.forget(this.tab)
            }
            GeckoPage.forget(this.tab)
            this.tab.engine?.close()
            this.tab.engine = null
            this.tab.engineView = null
            this.tab.loading = false
            this.tab.holdLoad = !PageRules.rendererGone(this.tab)
            if (!this.tab.holdLoad) this.tab.engineState = saved
            this.tab.epoch++
        }
    }

    override fun onFullScreen(tab: EngineTab, on: Boolean) {
        h.onGeckoFullScreen(on)
        if (this.tab.yt) GeckoYt.onFullscreen(this.tab, on) else GeckoYt.fullscreen = on
        val w = h.activity.window
        val c = WindowCompat.getInsetsController(w, w.decorView)
        if (on) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
            if (this.tab.yt) GeckoYt.afterFullscreen(this.tab)
        }
    }
}

fun geckoContainer(ctx: Context, tb: BrowserTab, h: Handlers, primary: Int, bg: Int, spinner: Int): View {
    val view = NovaGeckoView(ctx)
    tb.yt = GeckoYt.isYtUrl(tb.url)
    var fresh = false
    val e = tb.engine ?: run {
        fresh = true
        if (tb.engineState == null && tb.url.startsWith("http")) {
            val sd = SiteStore.get(hostOf(tb.url).removePrefix("www.")).desktop
            if (sd >= 0) tb.desktop = sd == 1
        }
        val st = tb.engineState
        tb.engineState = null
        EngineTab(tb.id, GeckoBridge(tb, h), st, tb.desktop, GeckoUi(h), GeckoSelection(h.activity) { id, text -> SmartAssist.onSelection(h.activity, id, text, h.openTab) }, GeckoMedia(tb), true, tb.isPrivate).also { tb.engine = it }
    }
    e.attach(view)
    if (fresh && !e.restored) {
        if (tb.holdLoad) {
            tb.holdLoad = false
            e.loadHtml(errorHtml(tb.url, L("تعطّلت عملية عرض الصفحة عدة مرات. أغلق تبويبات أخرى لتحرير الذاكرة ثم أعد المحاولة.")))
        } else e.load(tb.url)
    }
    val box = SwipeRefreshLayout(ctx)
    tb.engineView = box
    var judgedUrl: String? = null
    var judged = false
    box.apply {
        addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setOnRefreshListener { e.reload() }
        setOnChildScrollUpCallback { _, _ ->
            if (view.pageCanScrollUp() != false || e.scrollY > 0 || tb.noPull) true
            else {
                val u = tb.url
                if (u != judgedUrl) {
                    judgedUrl = u
                    judged = Pwa.kind(u) != SiteKind.NONE || PageRules.noPullRefresh(u)
                }
                judged
            }
        }
        setDistanceToTriggerSync((120 * ctx.resources.displayMetrics.density).toInt())
        setSlingshotDistance((140 * ctx.resources.displayMetrics.density).toInt())
        setColorSchemeColors(primary)
        setProgressBackgroundColorSchemeColor(bg)
        useIosSpinner(spinner)
    }
    return box
}

fun snapEngine(t: BrowserTab, e: EngineTab) {
    if (t.url.isBlank()) return
    if (Adaptive.level >= 2 && t.thumb != null) return
    e.capture { bmp ->
        val k = 0.4f
        val w = (bmp.width * k).toInt().coerceAtLeast(1)
        val hh = (bmp.height * k).toInt().coerceAtLeast(1)
        t.thumb = android.graphics.Bitmap.createScaledBitmap(bmp, w, hh, true)
    }
}
