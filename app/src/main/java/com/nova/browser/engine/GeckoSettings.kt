package com.nova.browser

import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.StorageController

object GeckoSettings {
    fun update(s: GeckoRuntimeSettings) {
        val zooms = Prefs.zoomValues
        val z = zooms[Prefs.textZoom.coerceIn(0, zooms.lastIndex)]
        s.setJavaScriptEnabled(Prefs.js)
        s.setAutomaticFontSizeAdjustment(false)
        s.setFontSizeFactor(z / 100f)
        // حجب الإعلانات/المتتبعات، منع تتبع البصمة، والكوكيز الخارجية (معزولة بدل الحجب الكامل كي لا تنكسر تسجيلات الدخول)
        var at = if (Prefs.blockAds) ContentBlocking.AntiTracking.DEFAULT else ContentBlocking.AntiTracking.NONE
        if (Prefs.antiFingerprint) at = at or ContentBlocking.AntiTracking.FINGERPRINTING
        s.contentBlocking.setAntiTracking(at)
        s.contentBlocking.setCookieBehavior(
            if (Prefs.blockThirdCookies) ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS
            else ContentBlocking.CookieBehavior.ACCEPT_ALL
        )
        // لغة المواقع (Accept-Language و navigator.language)
        val lc = Prefs.siteLangCode()
        s.setLocales(arrayOf(if (lc.isNotEmpty()) lc else java.util.Locale.getDefault().toLanguageTag()))
        s.setPreferredColorScheme(
            if (Prefs.siteDark && appIsDark()) GeckoRuntimeSettings.COLOR_SCHEME_DARK else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        )
    }

    // الوضع الداكن للمواقع يتبع ثيم التطبيق نفسه (وليس نظام الأندرويد فقط)
    fun appIsDark(): Boolean = when (Prefs.theme) {
        1 -> false
        2 -> true
        else -> (android.content.res.Resources.getSystem().configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun update() {
        update(NovaApp.runtime().settings)
    }

    fun clearData() {
        NovaApp.runtime().storageController.clearData(StorageController.ClearFlags.ALL)
    }

    fun clearHost(host: String) {
        NovaApp.runtime().storageController.clearDataFromHost(host, StorageController.ClearFlags.ALL)
    }

    fun clearCache() {
        NovaApp.runtime().storageController.clearData(
            StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE
        )
    }

    fun engineSummary(): String = "GeckoView"
}
