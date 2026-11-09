package com.nova.browser

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper

/**
 * إعادة تحميل تلقائية: عند عودة الاتصال تُعاد الصفحات التي فشل تحميلها (عُرضت لها صفحة الخطأ) فقط؛
 * الصفحات السليمة لا تُمسّ فلا يضيع تمريرها أو ما كُتب فيها.
 */
class NetRetry(ctx: Context, private val tabs: () -> List<BrowserTab>) {
    private val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val ui = Handler(Looper.getMainLooper())
    private var cb: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (cb != null || cm == null) return
        val c = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { ui.postDelayed({ retry() }, 700L) }
        }
        cb = c
        runCatching { cm.registerDefaultNetworkCallback(c) }.onFailure { cb = null }
    }

    fun stop() {
        cb?.let { c -> runCatching { cm?.unregisterNetworkCallback(c) } }
        cb = null
        ui.removeCallbacksAndMessages(null)
    }

    private fun retry() {
        for (t in tabs()) {
            val e = t.engine ?: continue
            if (!t.failed || t.url.isBlank() || !t.url.startsWith("http")) continue
            t.failed = false
            e.load(t.url)
        }
    }
}
