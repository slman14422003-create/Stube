package com.nova.browser

import org.json.JSONObject

/** ينقل إعدادات التطبيق (Prefs) إلى إضافة Semo Shield كي تعمل المفاتيح في Gecko. الإضافة تحفظها وتعيد تسجيل سكربتات الصفحة. */
object GeckoCfg {
    fun json(): JSONObject = JSONObject()
        .put("shield", Prefs.shield)
        .put("ads", Prefs.blockAds)
        .put("ytads", Prefs.ytAds)
        .put("fit", Prefs.fitPages)
        .put("lazy", Prefs.lazyMedia)
        .put("boost", Prefs.boost)
        .put("pop", Prefs.popups)
        .put("zoom", Prefs.forceZoom)
        .put("copy", Prefs.unlockCopy)
        .put("draft", Prefs.draftSave)
        .put("dataSaver", Prefs.dataSaver)

    /** مفتاح يتغير عند تغير أي إعداد مؤثر (للاستخدام في LaunchedEffect). */
    fun key(): String = json().toString() + Prefs.blockThirdCookies + Prefs.antiFingerprint + Prefs.siteLang

    fun push() {
        CookieBridge.send(JSONObject().put("type", "cfg").put("cfg", json()))
    }
}
