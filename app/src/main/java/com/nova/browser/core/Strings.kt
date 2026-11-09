package com.nova.browser

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object Strings {
    private var en: Context? = null
    private var ar: Context? = null

    private fun forLocale(app: Context, l: Locale): Context =
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(l) })

    @Synchronized
    private fun ensure() {
        if (en != null && ar != null) return
        val app = Perf.context()
        en = forLocale(app, Locale.ENGLISH)
        ar = forLocale(app, Locale.forLanguageTag("ar"))
    }

    fun get(id: Int): String {
        ensure()
        val c = (if (I18n.isEnglish()) en else ar) ?: Perf.context()
        return c.getString(id)
    }
}

fun S(id: Int): String = Strings.get(id)
