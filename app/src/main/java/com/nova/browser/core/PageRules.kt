package com.nova.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object PageRules {
    fun isMaps(url: String?): Boolean {
        val u = runCatching { Uri.parse(url ?: "") }.getOrNull() ?: return false
        val h = u.host?.lowercase() ?: return false
        val p = u.path ?: ""
        return h == "maps.google.com" || h == "maps.app.goo.gl" || h == "mapsengine.google.com" ||
            ((h.startsWith("www.google.") || h.startsWith("google.")) && p.startsWith("/maps")) ||
            (h == "goo.gl" && p.startsWith("/maps"))
    }

    fun noPullRefresh(url: String?): Boolean {
        if (isMaps(url)) return true
        val u = runCatching { Uri.parse(url ?: "") }.getOrNull() ?: return false
        val h = (u.host ?: return false).lowercase().removePrefix("www.")
        val p = u.path ?: ""
        if (appLike(h)) return true
        return h == "openstreetmap.org" || h == "waze.com" || h.endsWith(".waze.com") || h == "earth.google.com" ||
            h == "maps.apple.com" || h == "windy.com" || h == "wego.here.com" || (h == "bing.com" && p.startsWith("/maps"))
    }

    private val appHosts = arrayOf(
        "dash.cloudflare.com", "one.dash.cloudflare.com", "workers.cloudflare.com", "playground.workers.cloudflare.com",
        "github.dev", "vscode.dev", "stackblitz.com", "codesandbox.io", "replit.com", "codepen.io", "jsfiddle.net",
        "colab.research.google.com", "console.cloud.google.com", "console.aws.amazon.com", "portal.azure.com",
        "vercel.com", "app.netlify.com", "dashboard.render.com", "supabase.com", "app.supabase.com", "figma.com",
        "docs.google.com", "sheets.google.com", "notion.so", "miro.com", "excalidraw.com", "chat.openai.com", "chatgpt.com",
        "claude.ai", "gemini.google.com", "web.telegram.org", "web.whatsapp.com", "discord.com", "app.slack.com"
    )

    private val appMatcher = HostMatcher(appHosts, arrayOf("workers.dev", "pages.dev"))

    fun appLike(host: String): Boolean = appMatcher.matches(host.removePrefix("www."))

    fun rendererGone(tab: BrowserTab): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - tab.lastCrash > 90_000) tab.crashes = 0
        tab.crashes++
        tab.lastCrash = now
        return tab.crashes < 3
    }

    fun intentTarget(raw: String): String? = runCatching {
        val i = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
        val fb = i.getStringExtra("browser_fallback_url")
        val cand = if (fb != null && (fb.startsWith("https://") || fb.startsWith("http://"))) fb else i.dataString
        cand?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }.getOrNull()

    fun samePage(a: String?, b: String?): Boolean {
        val ua = runCatching { Uri.parse(a ?: "") }.getOrNull() ?: return false
        val ub = runCatching { Uri.parse(b ?: "") }.getOrNull() ?: return false
        fun host(u: Uri) = (u.host ?: "").lowercase().removePrefix("www.")
        fun path(u: Uri) = (u.path ?: "").trimEnd('/')
        return host(ua).isNotEmpty() && host(ua) == host(ub) && path(ua) == path(ub)
    }

    fun isYtHost(host: String?): Boolean {
        val h = host?.lowercase()?.removePrefix("www.") ?: return false
        return h == "youtube.com" || h.endsWith(".youtube.com") || h == "youtu.be"
    }
}

object Favicons {
    private val cache = android.util.LruCache<String, Bitmap>(64)
    var version by mutableIntStateOf(0)
        private set

    fun get(host: String): Bitmap? = cache.get(host)

    fun put(host: String, icon: Bitmap) {
        if (host.isBlank() || icon.width <= 0 || icon.height <= 0 || cache.get(host) != null) return
        val b = runCatching { Bitmap.createScaledBitmap(icon, 48, 48, true) }.getOrNull() ?: return
        cache.put(host, b)
        version++
    }
}
