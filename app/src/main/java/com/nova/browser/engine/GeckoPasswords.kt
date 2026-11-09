package com.nova.browser

import android.net.Uri
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object GeckoPasswords {
    private var current: () -> BrowserTab? = { null }
    private var onForm: (BrowserTab, String) -> Unit = { _, _ -> }
    private var onCred: (String, String, String) -> Unit = { _, _, _ -> }
    private val extTab = ConcurrentHashMap<Int, Int>()
    private var hooked = false

    fun bind(current: () -> BrowserTab?, onForm: (BrowserTab, String) -> Unit, onCred: (String, String, String) -> Unit) {
        this.current = current
        this.onForm = onForm
        this.onCred = onCred
        if (!hooked) {
            hooked = true
            CookieBridge.listeners.add { type, o -> if (type == "pw") handle(o) }
        }
    }

    fun unbind() {
        current = { null }
        onForm = { _, _ -> }
        onCred = { _, _, _ -> }
    }

    private fun handle(msg: JSONObject) {
        if (Prefs.pwMode != 0) return
        val tab = current() ?: return
        if (tab.engine == null) return
        val u = runCatching { Uri.parse(msg.optString("url")) }.getOrNull() ?: return
        val host = u.host ?: return
        if (u.scheme != "https" && host != "localhost") return
        if (Vault.norm(host) != Vault.norm(hostOf(tab.url))) return
        val body = msg.optJSONObject("o") ?: return
        if (body.toString().length > 4096) return
        val id = msg.optInt("tab", -1)
        if (id >= 0) extTab[tab.id] = id
        when (body.optString("t")) {
            "form" -> onForm(tab, Vault.norm(host))
            "save" -> onCred(Vault.norm(host), body.optString("u"), body.optString("p"))
        }
    }

    fun fill(tab: BrowserTab, c: Cred) {
        val id = extTab[tab.id] ?: return
        val cur = runCatching { Uri.parse(tab.url) }.getOrNull() ?: return
        if (cur.scheme != "https" && cur.host != "localhost") return
        if (Vault.norm(hostOf(tab.url)) != c.host) return
        CookieBridge.send(
            JSONObject().put("type", "fill").put("tab", id).put("host", c.host).put("u", c.user).put("p", c.pass)
        )
    }
}
