package com.nova.browser

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import org.mozilla.geckoview.WebResponse
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object StreamDownload {
    private const val BUF = 128 * 1024

    fun start(ctx: Context, response: WebResponse, cd: String?, mime: String?) {
        val app = ctx.applicationContext
        Downloader.init(app)
        val uri = response.uri
        val body = response.body
        toast(app, L("جارٍ حفظ الملف…"))
        Work.io {
            val r = runCatching {
                when {
                    uri.startsWith("data:", true) -> {
                        runCatching { body?.close() }
                        saveData(app, uri, cd, mime)
                    }
                    body != null -> saveStream(app, body, cd, mime)
                    else -> throw IOException("no body")
                }
            }
            if (r.isFailure) Work.ui { toast(app, L("تعذّر حفظ الملف")) }
        }
    }

    private fun saveStream(app: Context, body: InputStream, cd: String?, mime: String?) {
        val m = normMime(mime).ifBlank { "application/octet-stream" }
        val sink = Sink.open(app, nameFor(cd, m), m)
        try {
            body.use { it.copyTo(sink.out, BUF) }
            sink.finish()
        } catch (e: Throwable) {
            sink.abort()
            throw e
        }
        done(app, sink)
    }

    private fun saveData(app: Context, url: String, cd: String?, mime: String?) {
        val comma = url.indexOf(',')
        if (comma < 0) throw IOException("bad data url")
        val head = url.substring(5, comma)
        val body = Uri.decode(url.substring(comma + 1))
        val m = head.substringBefore(';').trim().ifBlank { normMime(mime) }.ifBlank { "application/octet-stream" }
        val bytes = if (head.contains(";base64", true)) Base64.decode(body, Base64.DEFAULT) else body.toByteArray(Charsets.UTF_8)
        val sink = Sink.open(app, nameFor(cd, m), m)
        try {
            sink.out.write(bytes)
            sink.finish()
        } catch (e: Throwable) {
            sink.abort()
            throw e
        }
        done(app, sink)
    }

    private fun normMime(m: String?): String = m?.substringBefore(';')?.trim()?.lowercase().orEmpty()

    private fun nameFor(cd: String?, mime: String): String {
        var n = FileKind.nameFromHeader(cd)?.takeIf { it.isNotBlank() } ?: "download"
        if (!FileKind.hasExt(n)) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(normMime(mime))
            if (ext != null) n = "$n.$ext"
        }
        return n
    }

    private fun done(app: Context, s: Sink) {
        val name = Downloader.displayName(app, s.uri, s.name)
        Downloader.addFinished(name, s.uri, s.mime, s.size(app))
        Work.ui { toast(app, L("تم الحفظ — القائمة ⋮ ثم التنزيلات")) }
    }

    private class Sink(val app: Context, val uri: Uri, val out: OutputStream, val name: String, val mime: String) {
        fun finish() {
            out.flush()
            out.close()
            app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }

        fun abort() {
            runCatching { out.close() }
            runCatching { app.contentResolver.delete(uri, null, null) }
        }

        fun size(c: Context): Long =
            runCatching { c.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)

        companion object {
            fun open(app: Context, name: String, mime: String): Sink {
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Stube")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val u = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("insert failed")
                val o = app.contentResolver.openOutputStream(u, "w") ?: run {
                    runCatching { app.contentResolver.delete(u, null, null) }
                    throw IOException("open failed")
                }
                return Sink(app, u, o.buffered(256 * 1024), name, mime)
            }
        }
    }
}
