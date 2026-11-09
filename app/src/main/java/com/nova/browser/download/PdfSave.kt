package com.nova.browser

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object PdfSave {
    fun save(ctx: Context, tab: EngineTab, name: String) {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Semo" }
        tab.pdf { input ->
            if (input == null) {
                toast(ctx, L("تعذّر حفظ الصفحة كملف PDF"))
                return@pdf
            }
            val ok = runCatching {
                input.use { i ->
                    if (Build.VERSION.SDK_INT >= 29) {
                        val v = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, "$clean.pdf")
                            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
                        ctx.contentResolver.openOutputStream(u)!!.use { o -> i.copyTo(o) }
                    } else {
                        val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        d.mkdirs()
                        File(d, "$clean.pdf").outputStream().use { o -> i.copyTo(o) }
                    }
                }
            }.isSuccess
            toast(ctx, if (ok) L("تم حفظ الصفحة كملف PDF في التنزيلات") else L("تعذّر حفظ الصفحة كملف PDF"))
        }
    }
}
