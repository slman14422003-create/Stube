package com.nova.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** The useful subset of a Web App Manifest. */
class PwaMeta(val name: String, val orientation: Int, val themeColor: Int?, val icon: Bitmap?)

/**
 * Reads a site's Web App Manifest when it is installed as an app (all network work runs on Work.net):
 *  - name / short_name  -> app label and launcher shortcut label
 *  - icons              -> sharp launcher icon (instead of the tiny favicon)
 *  - orientation        -> default screen orientation (only when the user has not chosen one yet)
 *  - theme_color        -> remembered per host for the status bar tint
 * Everything is size-capped, https-only and fails silently: no manifest simply means the old behaviour.
 */
object PwaManifest {
    private const val MAX_HTML = 256 * 1024
    private const val MAX_JSON = 128 * 1024
    private const val MAX_ICON = 1024 * 1024
    private const val DEADLINE_MS = 4500L

    private val icons = ConcurrentHashMap<String, Bitmap>()

    private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val relAttr = Regex("\\brel\\s*=\\s*[\"']?([^\"'>\\s]+)", RegexOption.IGNORE_CASE)
    private val hrefAttr = Regex("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    private fun readCapped(ins: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun fetch(url: String, max: Int, until: Long): ByteArray? {
        if (System.currentTimeMillis() > until) return null
        val u = runCatching { URI(url) }.getOrNull() ?: return null
        if (u.scheme != "https" || u.host.isNullOrBlank()) return null
        val req = runCatching { Request.Builder().url(url).build() }.getOrNull() ?: return null
        return runCatching {
            NetKit.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.byteStream()?.use { readCapped(it, max) }
            }
        }.getOrNull()
    }

    private fun manifestUrl(html: String, pageUrl: String): String? {
        for (m in linkTag.findAll(html)) {
            val tag = m.value
            val rel = relAttr.find(tag)?.groupValues?.get(1) ?: continue
            if (!rel.equals("manifest", true)) continue
            val href = hrefAttr.find(tag)?.groupValues?.get(1)?.replace("&amp;", "&") ?: continue
            return runCatching { URI(pageUrl).resolve(href).toString() }.getOrNull()
        }
        return null
    }

    private fun bestIcon(arr: org.json.JSONArray?, base: String): String? {
        if (arr == null) return null
        var best: String? = null
        var bestScore = -1
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val src = o.optString("src").takeIf { it.isNotBlank() } ?: continue
            val type = o.optString("type").lowercase()
            val purpose = o.optString("purpose").lowercase()
            if (type.contains("svg") || src.lowercase().substringBefore('?').endsWith(".svg")) continue
            if (purpose.contains("monochrome")) continue
            val side = o.optString("sizes").split(' ').maxOfOrNull { s -> s.substringBefore('x').toIntOrNull() ?: 0 } ?: 0
            val score = if (side <= 0) 1 else minOf(side, 512)
            if (score > bestScore) {
                val abs = runCatching { URI(base).resolve(src).toString() }.getOrNull() ?: continue
                best = abs; bestScore = score
            }
        }
        return best
    }

    private fun clean(s: String) = s.replace("|", " ").replace(";;", " ").replace('\n', ' ').trim().take(30)

    private fun iconFile(ctx: Context, host: String) = File(File(ctx.applicationContext.filesDir, "pwa"), host.replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".png")

    /** Launcher icon saved from the manifest (memory-cached), or null. Cheap: 192px PNG. */
    fun cachedIcon(ctx: Context, host: String): Bitmap? {
        icons[host]?.let { return it }
        val f = iconFile(ctx, host)
        if (!f.exists()) return null
        val b = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull() ?: return null
        icons[host] = b
        return b
    }

    /** Theme colour remembered for this host (ARGB) or null. */
    fun themeColor(ctx: Context, host: String): Int? =
        ConfStore.open(ctx).getString("pwa_t_$host", "").toLongOrNull()?.toInt()

    /**
     * Fetches the manifest for https://host/ on Work.net (hard deadline ~4.5s), stores icon/theme/orientation,
     * then calls [done] on the main thread with the result (null when the site has no usable manifest).
     */
    fun refresh(ctx: Context, host: String, done: (PwaMeta?) -> Unit) {
        val app = ctx.applicationContext
        Work.net {
            val meta = runCatching { load(app, host) }.getOrNull()
            Work.ui { done(meta) }
        }
    }

    private fun load(app: Context, host: String): PwaMeta? {
        val until = System.currentTimeMillis() + DEADLINE_MS
        val page = "https://$host/"
        val html = fetch(page, MAX_HTML, until)?.toString(Charsets.UTF_8) ?: return null
        val murl = manifestUrl(html, page) ?: return null
        val j = JSONObject(String(fetch(murl, MAX_JSON, until) ?: return null, Charsets.UTF_8))
        val name = clean(j.optString("short_name").ifBlank { j.optString("name") })
        val orient = j.optString("orientation").lowercase().let {
            when { it.startsWith("portrait") -> AppPrefs.ORIENT_PORTRAIT; it.startsWith("landscape") -> AppPrefs.ORIENT_LANDSCAPE; else -> AppPrefs.ORIENT_AUTO }
        }
        val theme = runCatching { android.graphics.Color.parseColor(j.optString("theme_color")) }.getOrNull()
        var icon: Bitmap? = null
        bestIcon(j.optJSONArray("icons"), murl)?.let { iu ->
            val bytes = fetch(iu, MAX_ICON, until)
            if (bytes != null) {
                val raw = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                if (raw != null && raw.width >= 48) {
                    // أيقونة غير مربّعة كانت تُمطّ إلى 192×192 فتتشوّه: نحافظ على النسبة ونوسّطها على لوحة مربّعة شفافة
                    val k = 192f / maxOf(raw.width, raw.height)
                    val sw = (raw.width * k).toInt().coerceAtLeast(1); val sh = (raw.height * k).toInt().coerceAtLeast(1)
                    val sq = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(sq).drawBitmap(Bitmap.createScaledBitmap(raw, sw, sh, true), (192 - sw) / 2f, (192 - sh) / 2f, null)
                    icon = sq
                    runCatching {
                        val f = iconFile(app, host); f.parentFile?.mkdirs()
                        f.outputStream().use { icon!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }
                    icons[host] = icon!!
                }
            }
        }
        val st = ConfStore.open(app)
        if (theme != null) st.putString("pwa_t_$host", theme.toLong().toString())
        if (orient != AppPrefs.ORIENT_AUTO && !st.contains("pwa_o_$host")) AppPrefs.setOrient(app, host, orient)
        if (name.isBlank() && icon == null && theme == null) return null
        return PwaMeta(name, orient, theme, icon)
    }

    /** Forget everything stored for a host (called when the app is removed). */
    fun forget(ctx: Context, host: String) {
        icons.remove(host)
        Work.disk { runCatching { iconFile(ctx, host).delete() } }
    }
}
