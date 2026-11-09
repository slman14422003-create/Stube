package com.nova.browser

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * Audio-only playback engine used by the in-app mini player and the "Audio only" option.
 * The video's audio stream is resolved with NewPipeExtractor (YtFetch.info) on Work.media and played with
 * MediaPlayer, so the page can be left (or destroyed) without stopping the sound, and no video data is downloaded.
 * It feeds YtMedia, so the existing media notification / lock screen controls keep working unchanged.
 * When a track ends, the first related video (autoplay suggestion) is played next.
 */
object YtAudio {
    var active by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var author by mutableStateOf(""); private set
    var videoId by mutableStateOf(""); private set
    var posMs by mutableLongStateOf(0L); private set
    var durMs by mutableLongStateOf(0L); private set

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var mp: MediaPlayer? = null
    @Volatile private var gen = 0
    @Volatile private var nextUrl: String? = null
    private var app: Context? = null

    private val ticker = object : Runnable {
        override fun run() {
            val p = mp
            if (active && playing && p != null) {
                posMs = runCatching { p.currentPosition.toLong() }.getOrDefault(posMs)
                YtMedia.pos = posMs
                main.postDelayed(this, 1000L)
            }
        }
    }

    /** Watch URL that resumes the video at the current position. */
    fun watchUrl(): String = "https://m.youtube.com/watch?v=$videoId&t=${posMs / 1000}s"

    private fun releasePlayer() {
        main.removeCallbacks(ticker)
        val p = mp; mp = null
        if (p != null) runCatching { p.setOnPreparedListener(null); p.setOnCompletionListener(null); p.setOnErrorListener(null); p.release() }
    }

    /** Frees the player and resets state WITHOUT touching YtMedia (YtMedia.stop calls this). */
    fun release() {
        gen++
        releasePlayer()
        active = false; playing = false; loading = false; nextUrl = null
    }

    /** User closed the mini player: stop everything including the notification. */
    fun close() {
        release()
        YtMedia.stop(false)
    }

    private fun sync(a: Context) {
        YtMedia.title = title.ifBlank { "YouTube" }; YtMedia.artist = author
        YtMedia.playing = playing; YtMedia.pos = posMs; YtMedia.dur = durMs; YtMedia.vw = 0; YtMedia.vh = 0
        if (videoId.isNotBlank()) YtMedia.loadArt("https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
        val svc = MediaService.instance
        if (svc != null) svc.refresh()
        else if (playing) runCatching { ContextCompat.startForegroundService(a, Intent(a, MediaService::class.java)) }
    }

    // ---- resolved-stream cache: stream info is fetched ahead while the page plays, so the hand-off is instant ----
    private class Pre(val info: YtInfo, val at: Long)
    private val cache = object : LinkedHashMap<String, Pre>(8, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Pre>?) = size > 6
    }
    private val inflight = HashSet<String>()
    private const val TTL = 20 * 60_000L
    @Volatile private var silentFallback = false

    private fun idOf(url: String): String? = runCatching { YtFetch.canonical(url).substringAfter("v=") }.getOrNull()

    @Synchronized private fun cached(id: String): YtInfo? {
        val c = cache[id] ?: return null
        return if (System.currentTimeMillis() - c.at < TTL) c.info else { cache.remove(id); null }
    }
    @Synchronized private fun put(id: String, i: YtInfo) { cache[id] = Pre(i, System.currentTimeMillis()) }
    @Synchronized private fun claim(id: String): Boolean = inflight.add(id)
    @Synchronized private fun unclaim(id: String) { inflight.remove(id) }

    private fun nat(i: YtInfo?): List<YtOpt> = i?.audios?.filter { it.fmt == null }.orEmpty()

    private fun resolve(url: String, id: String, fresh: Boolean): YtInfo? {
        if (!fresh) cached(id)?.let { return it }
        repeat(2) { n ->
            try {
                val i = YtFetch.info(url)
                if (nat(i).isNotEmpty()) { put(id, i); return i }
            } catch (e: Throwable) {
                YtLog.add("audio resolve " + e.javaClass.simpleName)
            }
            if (n == 0) runCatching { Thread.sleep(350L) }
        }
        return null
    }

    /** Resolves the audio stream of [url] in the background so a later hand-off does not have to wait for the extractor. */
    fun warm(url: String) {
        val id = idOf(url) ?: return
        if (cached(id) != null || !claim(id)) return
        Work.yt { try { resolve(url, id, false) } finally { unclaim(id) } }
    }

    private fun failFinal(a: Context) {
        val was = active
        val quiet = silentFallback
        release()
        if (!was) return
        if (quiet && YtMedia.resumePage()) return
        toast(a, "Audio playback failed")
        YtMedia.stop(false)
    }

    private fun retryOrFail(a: Context, url: String, id: String, g: Int, attempt: Int, at: Long) {
        if (g != gen) return
        if (attempt >= 2) { failFinal(a); return }
        val ng = ++gen
        releasePlayer()
        loading = true; playing = false
        begin(a, url, id, at, ng, attempt + 1)
    }

    private fun begin(a: Context, url: String, id: String, startMs: Long, g: Int, attempt: Int) {
        Work.yt {
            val info = resolve(url, id, attempt > 0)
            if (g != gen) return@yt
            val cands = nat(info)
            if (info == null || cands.isEmpty()) { Work.ui { retryOrFail(a, url, id, g, attempt + 1, startMs) }; return@yt }
            val opt = cands[attempt % cands.size]
            val p = MediaPlayer()
            try {
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                runCatching { p.setWakeMode(a, PowerManager.PARTIAL_WAKE_LOCK) }
                p.setDataSource(a, Uri.parse(opt.url), mapOf("User-Agent" to YT_UA, "Referer" to "https://www.youtube.com/", "Origin" to "https://www.youtube.com"))
                p.setOnPreparedListener { m ->
                    if (g != gen) { runCatching { m.release() } }
                    else {
                        durMs = runCatching { m.duration.toLong() }.getOrDefault(0L)
                        if (startMs > 0) runCatching { m.seekTo(startMs.toInt()) }
                        runCatching { m.start() }
                        loading = false; playing = true
                        main.removeCallbacks(ticker); main.postDelayed(ticker, 1000L)
                        sync(a)
                    }
                }
                p.setOnCompletionListener {
                    if (g == gen) {
                        val n = nextUrl
                        if (n != null) start(a, n, 0L) else { playing = false; posMs = 0L; sync(a) }
                    }
                }
                p.setOnErrorListener { _, w, x ->
                    YtLog.add("audio player error $w/$x")
                    if (g == gen) retryOrFail(a, url, id, g, attempt, maxOf(posMs, startMs))
                    true
                }
                Work.ui {
                    if (g != gen) { runCatching { p.release() } }
                    else {
                        title = info.title; author = info.author; nextUrl = info.next
                        mp = p
                        sync(a)
                        runCatching { p.prepareAsync() }.onFailure { retryOrFail(a, url, id, g, attempt, startMs) }
                    }
                }
            } catch (e: Throwable) {
                runCatching { p.release() }
                YtLog.add("audio setup " + e.javaClass.simpleName)
                Work.ui { retryOrFail(a, url, id, g, attempt, startMs) }
            }
        }
    }

    /** Starts audio-only playback of [url] from [startMs]. Safe to call again to switch videos. */
    fun start(ctx: Context, url: String, startMs: Long, title0: String = "", author0: String = "", quiet: Boolean = false) {
        val a = ctx.applicationContext; app = a
        val id = idOf(url) ?: return
        val g = ++gen
        releasePlayer()
        silentFallback = quiet
        videoId = id; title = title0; author = author0
        posMs = startMs; durMs = 0L; active = true; loading = true; playing = false; nextUrl = null
        sync(a)
        begin(a, url, id, startMs, g, 0)
    }

    /** Same command names as the page player (play, pause, fwd, back, seek:<ms>, next, prev). */
    fun control(cmd: String) {
        Work.ui {
            val p = mp ?: return@ui
            when {
                cmd == "play" -> { runCatching { p.start() }; playing = true; main.removeCallbacks(ticker); main.postDelayed(ticker, 1000L) }
                cmd == "pause" -> { runCatching { p.pause() }; playing = false }
                cmd == "fwd" || cmd == "back" -> {
                    val t = (runCatching { p.currentPosition }.getOrDefault(0) + if (cmd == "fwd") 10_000 else -10_000).coerceIn(0, maxOf(0L, durMs).toInt())
                    runCatching { p.seekTo(t) }; posMs = t.toLong()
                }
                cmd.startsWith("seek:") -> cmd.substring(5).toLongOrNull()?.let { runCatching { p.seekTo(it.toInt()) }; posMs = it }
                cmd == "next" -> nextUrl?.let { n -> app?.let { start(it, n, 0L) } }
                cmd == "prev" -> { runCatching { p.seekTo(0) }; posMs = 0L }
            }
            app?.let { sync(it) }
        }
    }
}
