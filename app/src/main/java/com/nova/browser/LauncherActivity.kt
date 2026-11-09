package com.nova.browser

import android.app.Activity
import android.os.Bundle

class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = intent?.data
        val url = if (d != null && (d.scheme == "https" || d.scheme == "http")) d.toString().replaceFirst("http://", "https://") else "https://m.youtube.com/"
        startActivity(AppActivity.intentFor(this, url))
        finish()
    }
}
