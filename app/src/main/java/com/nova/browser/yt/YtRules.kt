package com.nova.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object YtRules {
    private const val FILE = "yt_rules.json"
    private val keys = listOf("shorts", "shortsHas", "focus")
    private val bad = Regex("[{};<>\\\\@]|url\\(|expression|import", RegexOption.IGNORE_CASE)

    @Volatile private var cached: JSONObject? = null

    private fun ok(s: String) = s.length in 2..140 && !bad.containsMatchIn(s)

    private fun sanitize(o: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("v", o.optInt("v", 0))
        for (k in keys) {
            val src = o.optJSONArray(k)
            val clean = JSONArray()
            if (src != null) for (i in 0 until minOf(src.length(), 40)) {
                val s = src.optString(i, "").trim()
                if (ok(s)) clean.put(s)
            }
            out.put(k, clean)
        }
        return out
    }

    private fun load(): JSONObject {
        val r = runCatching { sanitize(JSONObject(Perf.asset(FILE))) }.getOrNull() ?: sanitize(JSONObject())
        cached = r
        return r
    }

    fun json(c: Context): String = (cached ?: load()).toString()
}
