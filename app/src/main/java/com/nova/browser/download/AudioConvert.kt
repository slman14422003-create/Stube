package com.nova.browser

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.IOException

/** صيغة صوت ناتجة: container هو اسم الحاوية في FFmpeg، وcodecArgs وسائط الترميز. */
class AudioFmt(
    val label: String, val sub: String, val ext: String, val mime: String,
    val container: String, val codecArgs: List<String>, val id3: Boolean = false
)

object AudioFormats {
    val all: List<AudioFmt> = listOf(
        AudioFmt("MP3 • 320 kbps", L("أعلى جودة MP3 (متوافق مع كل الأجهزة)"), "mp3", "audio/mpeg", "mp3", listOf("-c:a", "libmp3lame", "-b:a", "320k"), true),
        AudioFmt("MP3 • 192 kbps", L("جودة عالية وحجم معتدل"), "mp3", "audio/mpeg", "mp3", listOf("-c:a", "libmp3lame", "-b:a", "192k"), true),
        AudioFmt("MP3 • 128 kbps", L("حجم صغير"), "mp3", "audio/mpeg", "mp3", listOf("-c:a", "libmp3lame", "-b:a", "128k"), true),
        AudioFmt("OGG Vorbis • ~160 kbps", L("ترميز مفتوح"), "ogg", "audio/ogg", "ogg", listOf("-c:a", "libvorbis", "-q:a", "5")),
        AudioFmt("Opus • 128 kbps", L("أفضل جودة بأقل حجم"), "opus", "audio/ogg", "ogg", listOf("-c:a", "libopus", "-b:a", "128k")),
        AudioFmt("AAC (M4A) • 192 kbps", L("تحويل إلى AAC"), "m4a", "audio/mp4", "ipod", listOf("-c:a", "aac", "-b:a", "192k")),
        AudioFmt("FLAC", L("بلا فقد إضافي (حجم كبير)"), "flac", "audio/flac", "flac", listOf("-c:a", "flac")),
        AudioFmt("WAV", L("غير مضغوط (حجم كبير جداً)"), "wav", "audio/x-wav", "wav", listOf("-c:a", "pcm_s16le"))
    )
}

object AudioConvert {
    /** يحوّل الملف المنزَّل [src] إلى [fmt] ويحفظه في Download/Nova ثم يحذف الأصل؛ عند الفشل يبقى الأصل. */
    fun run(app: Context, base: String, title: String, artist: String, src: DlTask, fmt: AudioFmt) {
        val ui = Handler(Looper.getMainLooper())
        ui.post { toast(app, L("جارٍ التحويل…") + " " + fmt.ext.uppercase()) }
        val r = app.contentResolver
        var out: android.net.Uri? = null
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$base.${fmt.ext}")
                put(MediaStore.Downloads.MIME_TYPE, fmt.mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Stube")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val dst = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IOException("insert failed")
            out = dst
            val inArg = FFmpegKitConfig.getSafParameterForRead(app, src.uri ?: throw IOException("no source"))
            val outArg = FFmpegKitConfig.getSafParameterForWrite(app, dst)
            val args = ArrayList<String>()
            args += listOf("-y", "-i", inArg, "-vn", "-map_metadata", "-1")
            args += fmt.codecArgs
            if (title.isNotBlank()) args += listOf("-metadata", "title=$title")
            if (artist.isNotBlank()) args += listOf("-metadata", "artist=$artist")
            if (fmt.id3) args += listOf("-id3v2_version", "3")
            args += listOf("-f", fmt.container, outArg)
            val session = FFmpegKit.executeWithArguments(args.toTypedArray())
            if (!ReturnCode.isSuccess(session.returnCode)) throw IOException("ffmpeg failed: " + (session.failStackTrace ?: session.returnCode))
            r.update(dst, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            val size = r.openFileDescriptor(dst, "r")?.use { it.statSize } ?: -1L
            if (size <= 0L) throw IOException("empty output")
            Downloader.addFinished(Downloader.displayName(app, dst, "$base.${fmt.ext}"), dst, fmt.mime, size)
            Downloader.removeOnMain(src, true)
        } catch (e: Throwable) {
            out?.let { runCatching { r.delete(it, null, null) } }
            YtLog.add("convert failed: " + e.message)
            ui.post { toast(app, L("فشل التحويل — حُفظ الملف الأصلي")) }
        }
    }
}
