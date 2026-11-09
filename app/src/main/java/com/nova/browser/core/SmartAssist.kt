package com.nova.browser

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.util.Locale

object SmartAssist {
    const val SEL_ASK = 0x6E01
    const val SEL_TRANSLATE = 0x6E02
    const val SEL_SEARCH = 0x6E03
    const val SEL_SUMMARIZE = 0x6E04

    class Summary(val title: String, val minutes: Int, val points: List<String>, val keywords: List<String>)


    private fun targetCode(): String = Prefs.siteLangCode().ifEmpty { I18n.code() }

    private fun answerLanguage(): String =
        Locale.forLanguageTag(targetCode()).getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }

    fun aiUrl(prompt: String): String = AI_MODE_URL + "&q=" + Uri.encode(prompt.take(1800))

    fun pagePrompt(title: String, url: String): String =
        "Summarize this web page and list its key points. Reply in " + answerLanguage() + ".\nTitle: " + title.take(200) + "\nURL: " + url

    fun selectionPrompt(text: String): String =
        "Explain the following text clearly and concisely. Reply in " + answerLanguage() + ".\n\n" + text.take(1200)

    fun summarizePrompt(text: String): String =
        "Summarize the following text in a few short bullet points. Reply in " + answerLanguage() + ".\n\n" + text.take(1500)

    fun translateUrl(text: String): String =
        "https://translate.google.com/?sl=auto&tl=" + targetCode() + "&text=" + Uri.encode(text.take(1500)) + "&op=translate"

    fun searchUrl(text: String): String =
        Prefs.engines[Prefs.engine].second + URLEncoder.encode(text.take(300), "UTF-8")

    fun onSelection(ctx: Context, id: Int, text: String, open: (String) -> Unit) {
        val t = text.trim()
        if (t.isEmpty()) return
        when (id) {
            SEL_ASK -> { toast(ctx, S(R.string.smart_sent_to_google)); open(aiUrl(selectionPrompt(t))) }
            SEL_TRANSLATE -> { toast(ctx, S(R.string.smart_sent_translate)); open(translateUrl(t)) }
            SEL_SEARCH -> open(searchUrl(t))
            SEL_SUMMARIZE -> { toast(ctx, S(R.string.smart_sent_to_google)); open(aiUrl(summarizePrompt(t))) }
        }
    }

    fun summarizeRaw(ctx: Context, result: String?, url: String, open: (String) -> Unit) {
        val s = parse(if (result == null) null else org.json.JSONObject.quote(result))
        if (s == null) toast(ctx, S(R.string.smart_no_summary)) else show(ctx, s, url, open)
    }

    private fun parse(raw: String?): Summary? {
        if (raw == null) return null
        return try {
            val str = JSONTokener(raw).nextValue() as? String ?: return null
            val o = JSONObject(str)
            if (o.optInt("ok") != 1) return null
            val p = o.getJSONArray("points")
            val k = o.optJSONArray("keywords")
            Summary(
                o.optString("title"),
                o.optInt("minutes", 1),
                (0 until p.length()).map { p.getString(it) },
                if (k == null) emptyList() else (0 until k.length()).map { k.getString(it) }
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun show(ctx: Context, s: Summary, url: String, open: (String) -> Unit) {
        val body = StringBuilder()
        s.points.forEach { body.append("\u2022 ").append(it).append("\n\n") }
        if (s.keywords.isNotEmpty()) body.append(S(R.string.smart_keywords).format(s.keywords.joinToString(" \u00B7 "))).append("\n\n")
        body.append(S(R.string.smart_on_device))
        novaDialog(ctx)
            .setTitle(S(R.string.smart_summary_title) + " \u00B7 " + S(R.string.smart_read_time).format(s.minutes))
            .setMessage(body.toString())
            .setPositiveButton(S(R.string.smart_copy)) { _, _ -> copyText(ctx, s.points.joinToString("\n")) }
            .setNeutralButton(S(R.string.smart_ask_ai)) { _, _ ->
                toast(ctx, S(R.string.smart_sent_to_google))
                open(aiUrl(pagePrompt(s.title, url)))
            }
            .setNegativeButton(S(R.string.smart_close), null)
            .show()
    }

    fun dupKey(url: String): String =
        url.trim().substringBefore('#').trimEnd('/').lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
}
