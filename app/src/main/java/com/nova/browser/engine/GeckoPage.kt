package com.nova.browser

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

object GeckoPage {
    private class Owner(val key: Any, val find: (String) -> BrowserTab?)

    private val main = Handler(Looper.getMainLooper())
    private val owners = CopyOnWriteArrayList<Owner>()
    private val extTab = ConcurrentHashMap<Int, Int>()
    private val pending = ConcurrentHashMap<String, (String?) -> Unit>()
    private val seq = AtomicLong()
    private var hooked = false

    fun register(owner: Any, find: (String) -> BrowserTab?) {
        owners.removeAll { it.key === owner }
        owners.add(Owner(owner, find))
        if (!hooked) {
            hooked = true
            CookieBridge.listeners.add { type, o ->
                when (type) {
                    "page" -> main.post { handlePage(o) }
                    "execres" -> main.post { handleResult(o) }
                    else -> Unit
                }
            }
        }
    }

    fun unregister(owner: Any) {
        owners.removeAll { it.key === owner }
    }

    fun forget(tab: BrowserTab) {
        extTab.remove(tab.id)
    }

    private fun handlePage(m: JSONObject) {
        val url = m.optString("url")
        val body = m.optJSONObject("o") ?: return
        val tab = owners.firstNotNullOfOrNull { it.find(url) } ?: return
        val id = m.optInt("tab", -1)
        if (id >= 0) extTab[tab.id] = id
        when (body.optString("t")) {
            "tint" -> tab.topColor = PageTint.parse(body.optString("c"))
            "bgb" -> tab.botColor = PageTint.parse(body.optString("c"))
            "noPull" -> tab.noPull = body.optBoolean("v")
            "icon" -> loadIcon(url, body.optString("u"))
        }
    }

    private fun safeIcon(u: String): Boolean {
        if (u.length > 512 || !u.startsWith("https://")) return false
        val h = runCatching { URL(u).host.lowercase() }.getOrNull() ?: return false
        if (h.isEmpty() || h == "localhost" || h.endsWith(".local") || h.endsWith(".internal")) return false
        if (h.contains(':') || Regex("^[0-9.]+$").matches(h)) return false
        return true
    }

    private fun loadIcon(pageUrl: String, iconUrl: String) {
        if (!safeIcon(iconUrl)) return
        val host = hostOf(pageUrl)
        if (host.isBlank() || Favicons.get(host) != null) return
        Work.img {
            runCatching {
                val c = URL(iconUrl).openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 6000
                c.instanceFollowRedirects = false
                val bytes = try {
                    if (c.responseCode != 200) null else c.inputStream.use { it.readNBytes(524288) }
                } finally {
                    c.disconnect()
                }
                val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) main.post { Favicons.put(host, bmp) }
            }
        }
    }

    fun exec(tab: BrowserTab, file: String, cb: (String?) -> Unit) {
        val id = extTab[tab.id]
        if (id == null) {
            cb(null)
            return
        }
        val key = seq.incrementAndGet().toString()
        pending[key] = cb
        CookieBridge.send(JSONObject().put("type", "exec").put("tab", id).put("file", file).put("id", key))
        main.postDelayed({ pending.remove(key)?.invoke(null) }, 8000L)
    }

    private fun handleResult(o: JSONObject) {
        val cb = pending.remove(o.optString("id")) ?: return
        val r = o.opt("result")
        cb(if (r == null || r == JSONObject.NULL) null else r.toString())
    }
}
