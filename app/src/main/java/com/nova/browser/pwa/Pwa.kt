package com.nova.browser

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.util.concurrent.ConcurrentHashMap

/** نوع الصفحة من منظور «وضع التطبيق». */
enum class SiteKind { NONE, YT, YT_VIDEO, AI, APP }

/** هوية الموقع المعروضة في الشريط العلوي. */
class SiteInfo(val kind: SiteKind, val name: String, val host: String)

/**
 * وضع PWA لمواقع الذكاء الاصطناعي + الشريط العلوي ليوتيوب:
 * - يكتشف نوع الصفحة (يوتيوب / فيديو / ذكاء اصطناعي) لعرض الشريط العلوي المناسب.
 */
object Pwa {
    const val BAR_H = UiLayout.BAR_DP   // dp: ارتفاع الشريط العلوي (يشمل خط التقدّم) — القيمة في UiLayout

    private val aiNames = mapOf(
        "gemini.google.com" to "Gemini", "chatgpt.com" to "ChatGPT", "chat.openai.com" to "ChatGPT",
        "claude.ai" to "Claude", "perplexity.ai" to "Perplexity", "copilot.microsoft.com" to "Copilot",
        "chat.deepseek.com" to "DeepSeek", "grok.com" to "Grok", "poe.com" to "Poe", "chat.mistral.ai" to "Le Chat"
    )

    // ---------- تطبيقات مثبّتة (أي موقع يُثبَّت كتطبيق مستقل) ----------
    private val apps = ConcurrentHashMap<String, String>()
    @Volatile private var loaded = false

    private fun ensure(c: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                ConfStore.open(c).getString("pwa_apps", "").split(";;").forEach { e ->
                    val i = e.indexOf('|')
                    if (i > 0) apps[e.substring(0, i)] = e.substring(i + 1)
                }
            }
            loaded = true
        }
    }

    private fun save(c: Context) {
        ConfStore.open(c).putString("pwa_apps", apps.entries.joinToString(";;") { it.key + "|" + it.value })
        lastUrl = null; lastInfo = null
        publishShortcuts(c)
    }

    /** اختصارات ديناميكية: الضغط المطوّل على أيقونة Nova يعرض التطبيقات المثبّتة (وعند وجود يوتيوب: الاشتراكات والسجل) لفتحها مباشرة. */
    fun publishShortcuts(c: Context) {
        runCatching {
            // إن كان يوتيوب مثبّتاً كتطبيق: اختصاران مباشران (الاشتراكات والسجل) ثم أحدث 3 تطبيقات؛ وإلا أحدث 4 تطبيقات كما كان
            val ytHost = apps.keys.firstOrNull { PageRules.isYtHost(it) }
            val appList = apps.entries.toList().takeLast(if (ytHost != null) 3 else 4).map { (host, name) ->
                ShortcutInfoCompat.Builder(c, "dyn_$host").setShortLabel(name).setLongLabel(name)
                    .setIcon(IconCompat.createWithBitmap((PwaManifest.cachedIcon(c, host) ?: Favicons.get(host))?.let { Bitmap.createScaledBitmap(it, 192, 192, true) } ?: letterIcon(host, name)))
                    .setIntent(AppActivity.intentFor(c, homeFor("https://$host/"))).build()
            }
            val ytList = if (ytHost == null) emptyList<ShortcutInfoCompat>() else {
                val ico = IconCompat.createWithBitmap((PwaManifest.cachedIcon(c, ytHost) ?: Favicons.get(ytHost))?.let { Bitmap.createScaledBitmap(it, 192, 192, true) } ?: letterIcon(ytHost, "YouTube"))
                listOf(
                    "yt_subs" to ("https://m.youtube.com/feed/subscriptions" to L("اشتراكات يوتيوب")),
                    "yt_history" to ("https://m.youtube.com/feed/history" to L("سجل يوتيوب"))
                ).map { (id, v) ->
                    ShortcutInfoCompat.Builder(c, id).setShortLabel(v.second).setLongLabel(v.second).setIcon(ico)
                        .setIntent(AppActivity.intentFor(c, v.first)).build()
                }
            }
            ShortcutManagerCompat.setDynamicShortcuts(c, ytList + appList)
        }
    }

    fun install(ctx: Context, url: String, title: String) {
        ensure(ctx)
        if (!isInstalled(hostOf(url))) togglePin(ctx, url, title)
    }

    fun installed(ctx: Context): List<Pair<String, String>> {
        ensure(ctx)
        return apps.entries.map { it.key to it.value }
    }

    fun remove(ctx: Context, host: String) {
        ensure(ctx)
        if (apps.remove(host) != null) { save(ctx); PwaManifest.forget(ctx, host) }
    }

    fun isInstalled(host: String): Boolean = apps.containsKey(host.removePrefix("www."))

    fun nameFor(host: String): String? = apps[host.removePrefix("www.")]

    fun homeFor(url: String): String {
        val h = hostOf(url)
        return if (PageRules.isYtHost(h)) "https://m.youtube.com/" else "https://$h/"
    }

    fun launch(ctx: Context, url: String) {
        ensure(ctx)
        runCatching { ctx.startActivity(AppActivity.intentFor(ctx, homeFor(url))) }
    }

    private fun letterIcon(host: String, label: String): Bitmap {
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val hue = ((host.hashCode().toLong().let { if (it < 0) -it else it }) % 360).toFloat()
        p.color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.72f))
        c.drawRoundRect(0f, 0f, 192f, 192f, 44f, 44f, p)
        p.color = android.graphics.Color.WHITE; p.textSize = 108f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        c.drawText(label.take(1).uppercase(), 96f, 96f + 38f, p)
        return b
    }

    fun pinShortcut(ctx: Context, url: String) {
        ensure(ctx)
        val host = hostOf(url)
        val name = apps[host] ?: host
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            toast(ctx, "Launcher does not support pinning")
            return
        }
        val ok = runCatching {
            val icon = IconCompat.createWithBitmap((PwaManifest.cachedIcon(ctx, host) ?: Favicons.get(host))?.let { Bitmap.createScaledBitmap(it, 192, 192, true) } ?: letterIcon(host, name))
            val sc = ShortcutInfoCompat.Builder(ctx, "app_" + host + "_" + System.currentTimeMillis()).setShortLabel(name).setLongLabel(name).setIcon(icon)
                .setIntent(AppActivity.intentFor(ctx, homeFor(url))).build()
            ShortcutManagerCompat.requestPinShortcut(ctx, sc, null)
        }.getOrDefault(false)
        if (!ok) toast(ctx, "Could not add shortcut")
    }

    /** يثبّت الموقع كتطبيق (اختصار على الشاشة الرئيسية + وضع التطبيق) أو يزيله إن كان مثبّتاً. يعيد true إن صار مثبّتاً. */
    fun togglePin(ctx: Context, url: String, title: String): Boolean {
        ensure(ctx)
        val host = hostOf(url)
        if (!url.startsWith("https://") || host.isBlank()) return false
        if (apps.containsKey(host)) {
            apps.remove(host); save(ctx); PwaManifest.forget(ctx, host)
            toast(ctx, "App removed")
            return false
        }
        val name = (title.ifBlank { host }).replace("|", " ").replace(";;", " ").trim().take(30)
        apps[host] = name; save(ctx)
        // Read the site's manifest in the background (name + sharp icon + orientation), then pin; falls back to the favicon
        PwaManifest.refresh(ctx, host) { meta ->
            val n = meta?.name?.takeIf { it.isNotBlank() }
            if (n != null && apps.containsKey(host)) { apps[host] = n; save(ctx) }
            if (apps.containsKey(host)) pinShortcut(ctx, url)
        }
        return true
    }

    @Volatile private var lastUrl: String? = null
    @Volatile private var lastInfo: SiteInfo? = null

    /** معلومات الموقع أو null إن لم يكن ضمن وضع التطبيق. مُخزَّنة لآخر رابط لأنها تُستدعى مع كل حركة لمس. */
    fun info(url: String?): SiteInfo? {
        if (url.isNullOrEmpty()) return null
        if (url == lastUrl) return lastInfo
        val r = compute(url)
        lastUrl = url; lastInfo = r
        return r
    }

    fun kind(url: String?): SiteKind = info(url)?.kind ?: SiteKind.NONE

    private fun compute(url: String): SiteInfo? {
        val p = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        if (p.scheme != "https") return null
        val raw = (p.host ?: return null).lowercase()
        val h = raw.removePrefix("www.")
        if (h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")) {
            val music = h == "music.youtube.com"
            return SiteInfo(if (isYtVideo(url)) SiteKind.YT_VIDEO else SiteKind.YT, if (music) "YouTube Music" else "YouTube", raw)
        }
        aiNames[h]?.let { return SiteInfo(SiteKind.AI, it, raw) }
        apps[h]?.let { return SiteInfo(SiteKind.APP, it, raw) }
        if (h.startsWith("google.") && (p.getQueryParameter("udm") == "50" || (p.path ?: "").startsWith("/ai")))
            return SiteInfo(SiteKind.AI, "Google AI Mode", raw)
        return null
    }
}
