package com.nova.browser

import android.app.Activity
import android.content.Context
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Benchmark builds only: writes fixed test data, or opens a panel in an already booted app. */
class BenchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (val op = intent.getStringExtra("op")) {
            "seed" -> seed()
            "library", "downloads", "passwords", "settings" -> openPanel(this, op)
        }
        finish()
    }

    private fun seed() {
        val base = 1_700_000_000_000L
        val bookmarks = JSONArray()
        repeat(500) {
            bookmarks.put(JSONObject().put("u", "https://site$it.example.com/page").put("t", "Bench item $it").put("f", "").put("a", base - it))
        }
        File(filesDir, "library.json").writeText(JSONObject().put("b", bookmarks).toString())

        val tasks = JSONArray()
        repeat(200) {
            val url = "https://example.com/bench-file-$it.zip"
            tasks.put(
                JSONObject().put("id", "bench$it").put("url", url).put("orig", url).put("ua", "").put("ref", "")
                    .put("created", base - it).put("name", "bench-file-$it.zip").put("mime", "application/zip").put("uri", "")
                    .put("total", 1_048_576L + it).put("res", true).put("st", Downloader.DONE).put("segs", JSONArray())
            )
        }
        getSharedPreferences("dl", Context.MODE_PRIVATE).edit().putString("tasks", tasks.toString()).commit()

        Vault.init(applicationContext)
        repeat(40) { Vault.upsert("", "bench$it.example.com", "user$it", "not-a-real-password") }
        getSharedPreferences("vault", Context.MODE_PRIVATE).edit().commit()
    }
}
