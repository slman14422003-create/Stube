package com.nova.browser

import android.content.Context
import android.content.Intent
import android.net.Uri

object PageExtras {
    private val blockedSchemes = setOf(
        "javascript", "file", "content", "ftp", "view-source", "chrome", "about", "data", "blob", "http", "https", "intent"
    )
    private val schemeRe = Regex("[a-z][a-z0-9+.-]{1,30}")

    fun openExternal(ctx: Context, u: Uri) {
        val scheme = u.scheme?.lowercase() ?: return
        if (scheme in blockedSchemes || !schemeRe.matches(scheme)) return
        if (!Shield.dialogAllowed(scheme)) return
        novaDialog(ctx)
            .setTitle("Open in another app?")
            .setMessage(u.toString().take(160))
            .setPositiveButton("Open") { _, _ ->
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, u)
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { toast(ctx, "No app found to open this link") }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
