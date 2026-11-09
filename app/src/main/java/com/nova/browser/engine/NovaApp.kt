package com.nova.browser

import android.app.Application
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: NovaApp
        private var rt: GeckoRuntime? = null

        @Synchronized
        fun runtime(): GeckoRuntime {
            rt?.let { return it }
            val settings = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .remoteDebuggingEnabled(false)
                .consoleOutput(false)
                .aboutConfigEnabled(false)
                .build()
            val created = GeckoRuntime.create(instance, settings)
            created.settings.contentBlocking
                .setAntiTracking(ContentBlocking.AntiTracking.DEFAULT)
                .setSafeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            GeckoSettings.update(created.settings)
            created.webExtensionController.ensureBuiltIn("resource://android/assets/extensions/semo/", "shield@semo.browser")
                .accept { ext -> ext?.setMessageDelegate(CookieBridge, CookieBridge.APP) }
            rt = created
            return created
        }
    }
}
