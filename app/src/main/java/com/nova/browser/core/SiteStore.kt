package com.nova.browser

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

object SiteStore {
    data class Site(val js: Int = -1, val desktop: Int = -1) {
        val custom get() = js >= 0 || desktop >= 0
    }

    private val mem = ConcurrentHashMap<String, Site>()
    @Volatile private var conf: ConfStore? = null

    fun init(c: Context) {
        conf = ConfStore.open(c)
    }

    private fun key(host: String) = "site.$host"

    private fun parse(raw: String): Site {
        if (raw.isBlank()) return Site()
        val m = raw.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to (it.substring(i + 1).toIntOrNull() ?: return@mapNotNull null) else null
        }.toMap()
        return Site(m["j"] ?: -1, m["d"] ?: -1)
    }

    private fun encode(s: Site) = "j=${s.js};d=${s.desktop}"

    fun get(host: String): Site {
        if (host.isBlank()) return Site()
        mem[host]?.let { return it }
        val s = parse(conf?.getString(key(host), "") ?: "")
        mem[host] = s
        return s
    }

    fun put(host: String, s: Site) {
        if (host.isBlank()) return
        mem[host] = s
        conf?.putString(key(host), if (s.custom) encode(s) else "")
    }

    fun reset(host: String) = put(host, Site())

    fun jsOn(host: String): Boolean {
        val s = get(host)
        return if (s.js >= 0) s.js == 1 else Prefs.js
    }

    fun toggleJs(host: String): Boolean {
        val on = jsOn(host)
        put(host, get(host).copy(js = if (on) 0 else 1))
        return !on
    }

    fun toggleDesktop(host: String, current: Boolean): Boolean {
        put(host, get(host).copy(desktop = if (current) 0 else 1))
        return !current
    }

    fun apply(tab: BrowserTab, host: String) {
        val s = get(host)
        val e = tab.engine ?: return
        e.setJs(jsOn(host))
        if (s.desktop >= 0 && (s.desktop == 1) != tab.desktop) {
            tab.desktop = s.desktop == 1
            e.setDesktop(tab.desktop)
        }
    }

    fun clearData(host: String) {
        if (host.isBlank()) return
        GeckoSettings.clearHost(host.removePrefix("www."))
    }
}
